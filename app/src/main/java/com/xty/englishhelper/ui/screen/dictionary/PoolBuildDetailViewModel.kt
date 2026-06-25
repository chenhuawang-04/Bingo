package com.xty.englishhelper.ui.screen.dictionary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.background.ChunkProgress
import com.xty.englishhelper.domain.background.PoolBuildLiveMonitor
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.model.WordPoolReviewPayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.ManualChunkContext
import com.xty.englishhelper.domain.repository.ManualFillResult
import com.xty.englishhelper.domain.usecase.ai.FetchAiModelsUseCase
import com.xty.englishhelper.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PoolBuildDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: BackgroundTaskRepository,
    private val backgroundTaskManager: BackgroundTaskManager,
    private val liveMonitor: PoolBuildLiveMonitor,
    private val settingsDataStore: SettingsDataStore,
    private val fetchAiModels: FetchAiModelsUseCase
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L
    private val taskMode: PoolTaskMode = PoolTaskMode.fromTaskTypeName(savedStateHandle["taskType"])
    private val taskType: BackgroundTaskType = taskMode.taskType
    private val aiScope: AiSettingsScope = when (taskMode) {
        PoolTaskMode.BUILD -> AiSettingsScope.POOL
        PoolTaskMode.REVIEW -> AiSettingsScope.REVIEWER
    }

    private val _uiState = MutableStateFlow(PoolBuildDetailUiState(taskMode = taskMode))
    val uiState: StateFlow<PoolBuildDetailUiState> = _uiState.asStateFlow()

    private var poolTaskId: Long? = null

    init {
        observePoolTasks()
        observeLiveChunks()
        observePoolModelConfig()
        observeProviders()
    }

    private fun observeLiveChunks() {
        viewModelScope.launch {
            liveMonitor.liveWord.collect { live ->
                _uiState.update {
                    if (
                        live != null &&
                        live.dictionaryId == dictionaryId &&
                        live.taskType == taskType
                    ) {
                        it.copy(liveChunks = live.chunks, liveChunkWord = live.word)
                    } else {
                        it.copy(liveChunks = emptyList(), liveChunkWord = null)
                    }
                }
            }
        }
    }

    private fun observePoolTasks() {
        viewModelScope.launch {
            var lastStatus: BackgroundTaskStatus? = null
            taskRepository.observeAllTasks().collect { tasks ->
                val poolTask = tasks
                    .filter { it.type == taskType }
                    .filter { matchesDictionary(it) }
                    .maxByOrNull { it.updatedAt }

                if (poolTask == null) {
                    poolTaskId = null
                    _uiState.update {
                        it.copy(
                            status = BuildStatus.IDLE,
                            currentWord = null,
                            progressCurrent = 0,
                            progressTotal = 0,
                            strategy = null,
                            rebuildMode = null,
                            isPaused = false,
                            errorMessage = null,
                            errorLogs = emptyList(),
                            chunkCurrent = 0,
                            chunkTotal = 0,
                            edgesFound = 0,
                            fillableChunkIndex = null,
                            manualFillVisible = false,
                            manualFillLoading = false,
                            manualFillContext = null,
                            manualFillError = null,
                            manualFillSubmitting = false,
                            currentReviewMessage = null
                        )
                    }
                    lastStatus = null
                    return@collect
                }

                poolTaskId = poolTask.id
                val inProgress = poolTask.status == BackgroundTaskStatus.PENDING ||
                    poolTask.status == BackgroundTaskStatus.RUNNING ||
                    poolTask.status == BackgroundTaskStatus.PAUSED
                val status = when (poolTask.status) {
                    BackgroundTaskStatus.PENDING, BackgroundTaskStatus.RUNNING -> BuildStatus.RUNNING
                    BackgroundTaskStatus.PAUSED -> BuildStatus.PAUSED
                    BackgroundTaskStatus.SUCCESS -> BuildStatus.SUCCESS
                    BackgroundTaskStatus.FAILED -> BuildStatus.FAILED
                    BackgroundTaskStatus.CANCELED -> BuildStatus.CANCELED
                    else -> BuildStatus.IDLE
                }
                val strategy = extractStrategy(poolTask)
                val rebuildMode = extractRebuildMode(poolTask)
                val newError = if (poolTask.status == BackgroundTaskStatus.FAILED && poolTask.errorMessage != null) {
                    poolTask.errorMessage
                } else {
                    null
                }
                val terminalToActive = lastStatus in TERMINAL_STATES &&
                    poolTask.status in ACTIVE_STATES
                val errorLogs = when {
                    terminalToActive -> emptyList()
                    newError != null && newError != _uiState.value.errorMessage ->
                        (_uiState.value.errorLogs + newError).takeLast(100)
                    else -> _uiState.value.errorLogs
                }

                val detailProgress = when (taskMode) {
                    PoolTaskMode.BUILD -> parseBuildProgress(poolTask.progressMessage)
                    PoolTaskMode.REVIEW -> parseReviewProgress(poolTask.progressMessage)
                }

                _uiState.update {
                    it.copy(
                        status = status,
                        currentWord = detailProgress.label ?: poolTask.progressMessage,
                        progressCurrent = poolTask.progressCurrent,
                        progressTotal = poolTask.progressTotal,
                        strategy = strategy,
                        rebuildMode = rebuildMode,
                        isPaused = poolTask.status == BackgroundTaskStatus.PAUSED,
                        errorMessage = newError,
                        errorLogs = errorLogs,
                        chunkCurrent = detailProgress.chunkCurrent,
                        chunkTotal = detailProgress.chunkTotal,
                        edgesFound = if (taskMode == PoolTaskMode.REVIEW) {
                            poolTask.progressCurrent
                        } else {
                            detailProgress.metricCount
                        },
                        fillableChunkIndex = if (
                            taskMode == PoolTaskMode.BUILD &&
                            status == BuildStatus.FAILED &&
                            detailProgress.chunkTotal > 0 &&
                            detailProgress.chunkCurrent in 0 until detailProgress.chunkTotal
                        ) detailProgress.chunkCurrent else null,
                        currentReviewMessage = if (taskMode == PoolTaskMode.REVIEW) {
                            formatReviewMessage(poolTask)
                        } else {
                            null
                        }
                    )
                }

                if (poolTask.status != lastStatus && poolTask.status == BackgroundTaskStatus.SUCCESS) {
                    _uiState.update { it.copy(errorLogs = emptyList()) }
                }
                lastStatus = poolTask.status
            }
        }
    }

    private fun matchesDictionary(task: BackgroundTask): Boolean {
        return when (val payload = task.payload) {
            is WordPoolRebuildPayload -> payload.dictionaryId == dictionaryId
            is WordPoolReviewPayload -> payload.dictionaryId == dictionaryId
            else -> false
        }
    }

    private fun extractStrategy(task: BackgroundTask): String? {
        return when (val payload = task.payload) {
            is WordPoolRebuildPayload -> payload.strategy
            is WordPoolReviewPayload -> payload.strategy
            else -> null
        }
    }

    private fun extractRebuildMode(task: BackgroundTask): String? {
        val payload = task.payload as? WordPoolRebuildPayload ?: return null
        return payload.rebuildMode
    }

    private data class DetailProgress(
        val label: String?,
        val chunkCurrent: Int,
        val chunkTotal: Int,
        val metricCount: Int
    )

    private data class ReviewProgressMessage(
        val completedBatches: Int,
        val totalBatches: Int,
        val modifiedEdges: Int
    )

    private fun parseBuildProgress(message: String?): DetailProgress {
        if (message.isNullOrBlank() || !message.contains("|")) {
            return DetailProgress(label = message, chunkCurrent = 0, chunkTotal = 0, metricCount = 0)
        }
        val parts = message.split("|")
        val word = parts.getOrNull(0)
        val chunkCurrent = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val chunkTotal = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val edges = parts.getOrNull(3)?.toIntOrNull() ?: 0
        return DetailProgress(word, chunkCurrent, chunkTotal, edges)
    }

    private fun parseReviewProgress(message: String?): DetailProgress {
        if (message.isNullOrBlank()) {
            return DetailProgress(label = null, chunkCurrent = 0, chunkTotal = 0, metricCount = 0)
        }
        val parts = message.split("|")
        if (parts.size < 4 || parts[0] != "review") {
            return DetailProgress(label = message, chunkCurrent = 0, chunkTotal = 0, metricCount = 0)
        }
        val review = ReviewProgressMessage(
            completedBatches = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            totalBatches = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            modifiedEdges = parts.getOrNull(3)?.toIntOrNull() ?: 0
        )
        return DetailProgress(
            label = "AI复查批次",
            chunkCurrent = review.completedBatches,
            chunkTotal = review.totalBatches,
            metricCount = review.modifiedEdges
        )
    }

    private fun formatReviewMessage(task: BackgroundTask): String? {
        val parsed = parseReviewProgress(task.progressMessage)
        return when {
            parsed.chunkTotal > 0 -> {
                "已审 ${parsed.chunkCurrent}/${parsed.chunkTotal} 批 · 已审核 ${task.progressCurrent}/${task.progressTotal} 条边 · 已调整 ${parsed.metricCount} 条"
            }
            task.progressTotal > 0 -> {
                "已审核 ${task.progressCurrent}/${task.progressTotal} 条边"
            }
            else -> task.progressMessage
        }
    }

    fun pause() {
        val taskId = poolTaskId ?: return
        backgroundTaskManager.pauseTask(taskId)
    }

    fun resume() {
        val taskId = poolTaskId ?: return
        backgroundTaskManager.resumeTask(taskId)
    }

    fun cancel() {
        val taskId = poolTaskId ?: return
        backgroundTaskManager.cancelTask(taskId)
    }

    fun retry() {
        val state = _uiState.value
        val strategy = state.strategy ?: return
        val poolStrategy = runCatching { PoolStrategy.valueOf(strategy) }.getOrNull() ?: return
        when (taskMode) {
            PoolTaskMode.BUILD -> {
                backgroundTaskManager.enqueueWordPoolRebuild(
                    dictionaryId = dictionaryId,
                    strategy = poolStrategy,
                    force = false,
                    rebuildMode = RebuildMode.INCREMENTAL
                )
            }

            PoolTaskMode.REVIEW -> {
                backgroundTaskManager.enqueueWordPoolReview(dictionaryId, poolStrategy)
            }
        }
    }

    fun clearErrorLogs() {
        _uiState.update { it.copy(errorLogs = emptyList()) }
    }

    // ── 手动填块 ──

    fun openManualFill() {
        if (taskMode != PoolTaskMode.BUILD) return
        val taskId = poolTaskId ?: return
        _uiState.update {
            it.copy(
                manualFillVisible = true,
                manualFillLoading = true,
                manualFillContext = null,
                manualFillError = null,
                manualFillSubmitting = false
            )
        }
        viewModelScope.launch {
            val ctx = runCatching { backgroundTaskManager.getPoolManualChunkContext(taskId) }.getOrNull()
            _uiState.update {
                when {
                    !it.manualFillVisible -> it
                    ctx == null -> it.copy(
                        manualFillLoading = false,
                        manualFillError = "无法加载该块（窗口大小可能已变更，或当前不在可填状态）"
                    )
                    else -> it.copy(manualFillLoading = false, manualFillContext = ctx, manualFillError = null)
                }
            }
        }
    }

    fun submitManualFill(json: String) {
        if (taskMode != PoolTaskMode.BUILD) return
        val taskId = poolTaskId ?: return
        if (json.isBlank()) {
            _uiState.update { it.copy(manualFillError = "请粘贴 JSON 数组（该块无边可填 []）") }
            return
        }
        _uiState.update { it.copy(manualFillSubmitting = true, manualFillError = null) }
        viewModelScope.launch {
            val result = runCatching { backgroundTaskManager.manualFillPoolChunk(taskId, json) }
                .getOrElse { ManualFillResult(false, error = it.message ?: "提交失败") }
            _uiState.update {
                if (result.success) {
                    it.copy(
                        manualFillSubmitting = false,
                        manualFillVisible = false,
                        manualFillContext = null,
                        manualFillError = null,
                        manualFillMessage = if (result.wordComplete) {
                            "已填入，该词全部块完成，正在自动继续下一个词…"
                        } else {
                            "已填入（${result.insertedEdges} 条边）。可继续填下一块，或点「重试」让 AI 跑完剩下的。"
                        }
                    )
                } else {
                    it.copy(manualFillSubmitting = false, manualFillError = result.error ?: "提交失败")
                }
            }
        }
    }

    fun dismissManualFill() {
        _uiState.update {
            it.copy(
                manualFillVisible = false,
                manualFillLoading = false,
                manualFillContext = null,
                manualFillError = null,
                manualFillSubmitting = false
            )
        }
    }

    fun clearManualFillMessage() {
        _uiState.update { it.copy(manualFillMessage = null) }
    }

    fun clearReviewMessage() {
        _uiState.update { it.copy(currentReviewMessage = null) }
    }

    private fun observePoolModelConfig() {
        viewModelScope.launch {
            settingsDataStore.scopeConfig(aiScope).collect { config ->
                _uiState.update { it.copy(poolProviderName = config.providerName, poolModel = config.model) }
            }
        }
    }

    private fun observeProviders() {
        viewModelScope.launch {
            settingsDataStore.providersWithKeys.collect { list ->
                _uiState.update { state ->
                    state.copy(
                        poolProviders = list.map { s ->
                            PoolModelProvider(s.profile.name, s.profile.provider, s.profile.baseUrl, s.hasApiKey)
                        }
                    )
                }
            }
        }
    }

    fun openModelSwitch() {
        _uiState.update {
            it.copy(
                modelSwitchVisible = true,
                editingProviderName = it.poolProviderName,
                editingModel = it.poolModel,
                modelOptions = emptyList(),
                modelLoading = false,
                modelError = null
            )
        }
    }

    fun dismissModelSwitch() {
        _uiState.update {
            it.copy(modelSwitchVisible = false, modelOptions = emptyList(), modelLoading = false, modelError = null)
        }
    }

    fun onEditingProviderChange(providerName: String) {
        _uiState.update { it.copy(editingProviderName = providerName, modelOptions = emptyList(), modelError = null) }
    }

    fun onEditingModelChange(model: String) {
        _uiState.update { it.copy(editingModel = model) }
    }

    fun fetchModelsForEditing() {
        val provider = _uiState.value.poolProviders.firstOrNull { it.name == _uiState.value.editingProviderName }
        if (provider == null) {
            _uiState.update { it.copy(modelError = "提供商不存在") }
            return
        }
        viewModelScope.launch {
            val apiKey = settingsDataStore.getProviderApiKey(provider.name)
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(modelError = "请先在设置中为「${provider.name}」配置 API Key") }
                return@launch
            }
            _uiState.update { it.copy(modelLoading = true, modelError = null) }
            val baseUrl = provider.baseUrl.ifBlank { defaultBaseUrl(provider.format) }
            val result = runCatching { fetchAiModels(apiKey, provider.format, baseUrl) }
            _uiState.update {
                result.fold(
                    onSuccess = { models -> it.copy(modelLoading = false, modelOptions = models) },
                    onFailure = { e -> it.copy(modelLoading = false, modelError = e.message ?: "拉取失败") }
                )
            }
        }
    }

    fun confirmModelSwitch() {
        val providerName = _uiState.value.editingProviderName
        val model = _uiState.value.editingModel.trim()
        if (providerName.isBlank()) {
            _uiState.update { it.copy(modelError = "请选择提供商") }
            return
        }
        if (model.isBlank()) {
            _uiState.update { it.copy(modelError = "请填写或选择模型") }
            return
        }
        viewModelScope.launch {
            settingsDataStore.setScopeConfig(aiScope, providerName, model)
            _uiState.update {
                it.copy(
                    modelSwitchVisible = false,
                    modelOptions = emptyList(),
                    modelLoading = false,
                    modelError = null,
                    modelSwitchMessage = when (taskMode) {
                        PoolTaskMode.BUILD -> "已切换词池整理模型为「$model」，从下一次请求生效。"
                        PoolTaskMode.REVIEW -> "已切换词池审核模型为「$model」，从下一次请求生效。"
                    }
                )
            }
        }
    }

    fun clearModelSwitchMessage() {
        _uiState.update { it.copy(modelSwitchMessage = null) }
    }

    private fun defaultBaseUrl(provider: AiProvider): String = when (provider) {
        AiProvider.ANTHROPIC -> Constants.ANTHROPIC_BASE_URL
        AiProvider.OPENAI_COMPATIBLE -> Constants.OPENAI_BASE_URL
    }

    companion object {
        private val TERMINAL_STATES = setOf(
            BackgroundTaskStatus.FAILED,
            BackgroundTaskStatus.CANCELED,
            BackgroundTaskStatus.SUCCESS
        )
        private val ACTIVE_STATES = setOf(
            BackgroundTaskStatus.PENDING,
            BackgroundTaskStatus.RUNNING
        )
    }
}

data class PoolBuildDetailUiState(
    val taskMode: PoolTaskMode = PoolTaskMode.BUILD,
    val status: BuildStatus = BuildStatus.IDLE,
    val currentWord: String? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val strategy: String? = null,
    val rebuildMode: String? = null,
    val isPaused: Boolean = false,
    val errorMessage: String? = null,
    val errorLogs: List<String> = emptyList(),
    val chunkCurrent: Int = 0,
    val chunkTotal: Int = 0,
    val edgesFound: Int = 0,
    val liveChunks: List<ChunkProgress> = emptyList(),
    val liveChunkWord: String? = null,
    val fillableChunkIndex: Int? = null,
    val manualFillVisible: Boolean = false,
    val manualFillLoading: Boolean = false,
    val manualFillContext: ManualChunkContext? = null,
    val manualFillError: String? = null,
    val manualFillSubmitting: Boolean = false,
    val manualFillMessage: String? = null,
    val poolProviderName: String = "",
    val poolModel: String = "",
    val poolProviders: List<PoolModelProvider> = emptyList(),
    val modelSwitchVisible: Boolean = false,
    val editingProviderName: String = "",
    val editingModel: String = "",
    val modelOptions: List<String> = emptyList(),
    val modelLoading: Boolean = false,
    val modelError: String? = null,
    val modelSwitchMessage: String? = null,
    val currentReviewMessage: String? = null
)

data class PoolModelProvider(
    val name: String,
    val format: AiProvider,
    val baseUrl: String,
    val hasApiKey: Boolean
)
