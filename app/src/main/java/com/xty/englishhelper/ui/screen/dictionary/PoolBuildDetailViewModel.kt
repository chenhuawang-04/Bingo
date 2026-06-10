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
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
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

    private val _uiState = MutableStateFlow(PoolBuildDetailUiState())
    val uiState: StateFlow<PoolBuildDetailUiState> = _uiState.asStateFlow()

    private var poolTaskId: Long? = null

    init {
        observePoolRebuildTasks()
        observeLiveChunks()
        observePoolModelConfig()
        observeProviders()
    }

    /** 收集实时分块网格（仅内存态）。仅展示属于本词典的当前词；其它情况清空方块。 */
    private fun observeLiveChunks() {
        viewModelScope.launch {
            liveMonitor.liveWord.collect { live ->
                _uiState.update {
                    if (live != null && live.dictionaryId == dictionaryId) {
                        it.copy(liveChunks = live.chunks, liveChunkWord = live.word)
                    } else {
                        it.copy(liveChunks = emptyList(), liveChunkWord = null)
                    }
                }
            }
        }
    }

    private fun observePoolRebuildTasks() {
        viewModelScope.launch {
            var lastStatus: BackgroundTaskStatus? = null
            taskRepository.observeAllTasks().collect { tasks ->
                val poolTask = tasks
                    .filter { it.type == BackgroundTaskType.WORD_POOL_REBUILD }
                    .filter { (it.payload as? WordPoolRebuildPayload)?.dictionaryId == dictionaryId }
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
                            isPaused = false,
                            errorMessage = null,
                            fillableChunkIndex = null
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
                val strategy = (poolTask.payload as? WordPoolRebuildPayload)?.strategy
                val rebuildMode = (poolTask.payload as? WordPoolRebuildPayload)?.rebuildMode
                val newError = if (poolTask.status == BackgroundTaskStatus.FAILED && poolTask.errorMessage != null) {
                    poolTask.errorMessage
                } else {
                    null
                }
                val terminalToActive = lastStatus in TERMINAL_STATES &&
                    poolTask.status in ACTIVE_STATES
                val errorLogs = when {
                    // Clear stale logs when a new build starts after failure/cancel
                    terminalToActive -> emptyList()
                    // Append new unique error, cap at 100
                    newError != null && newError != _uiState.value.errorMessage ->
                        (_uiState.value.errorLogs + newError).takeLast(100)
                    else -> _uiState.value.errorLogs
                }

                // 解析 progressMessage 格式: "word|chunkIdx|totalChunks|edgesFound"
                // 如果不是这个格式（例如纯单词），则 chunk 信息为 0
                val rawMsg = poolTask.progressMessage
                val parsedWord: String?
                val parsedChunkCurrent: Int
                val parsedChunkTotal: Int
                val parsedEdgesFound: Int
                if (rawMsg != null && rawMsg.contains("|")) {
                    val parts = rawMsg.split("|")
                    parsedWord = parts.getOrNull(0)
                    parsedChunkCurrent = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    parsedChunkTotal = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    parsedEdgesFound = parts.getOrNull(3)?.toIntOrNull() ?: 0
                } else {
                    parsedWord = rawMsg
                    parsedChunkCurrent = 0
                    parsedChunkTotal = 0
                    parsedEdgesFound = 0
                }

                _uiState.update {
                    it.copy(
                        status = status,
                        currentWord = parsedWord,
                        progressCurrent = poolTask.progressCurrent,
                        progressTotal = poolTask.progressTotal,
                        strategy = strategy,
                        rebuildMode = rebuildMode,
                        isPaused = poolTask.status == BackgroundTaskStatus.PAUSED,
                        errorMessage = newError,
                        errorLogs = errorLogs,
                        chunkCurrent = parsedChunkCurrent,
                        chunkTotal = parsedChunkTotal,
                        edgesFound = parsedEdgesFound,
                        // 仅 FAILED 且有待整理块时，断点块（=已提交块数）可手动填入。
                        fillableChunkIndex = if (
                            status == BuildStatus.FAILED &&
                            parsedChunkTotal > 0 &&
                            parsedChunkCurrent in 0 until parsedChunkTotal
                        ) parsedChunkCurrent else null
                    )
                }

                if (poolTask.status != lastStatus && poolTask.status == BackgroundTaskStatus.SUCCESS) {
                    // Clear error logs on success
                    _uiState.update { it.copy(errorLogs = emptyList()) }
                }
                lastStatus = poolTask.status
            }
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
        // “重试” = 从断点继续，**绝不**重新清库：无论原构建是 FULL 还是 INCREMENTAL，重试一律走 INCREMENTAL 续传。
        // （要彻底重建请用词典页的「完全重建」——那是独立的 force=true 路径，会清零进度并清库。）
        backgroundTaskManager.enqueueWordPoolRebuild(
            dictionaryId = dictionaryId,
            strategy = poolStrategy,
            force = false,
            rebuildMode = RebuildMode.INCREMENTAL
        )
    }

    fun clearErrorLogs() {
        _uiState.update { it.copy(errorLogs = emptyList()) }
    }

    // ── 手动填块 ──

    /** 打开当前 FAILED 任务"下一待填块"的填入弹窗，并异步加载该块候选词与提示词。 */
    fun openManualFill() {
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
                    !it.manualFillVisible -> it // 用户已关闭
                    ctx == null -> it.copy(
                        manualFillLoading = false,
                        manualFillError = "无法加载该块（窗口大小可能已变更，或当前不在可填状态）"
                    )
                    else -> it.copy(manualFillLoading = false, manualFillContext = ctx, manualFillError = null)
                }
            }
        }
    }

    /** 提交手动填入的 JSON；成功则关闭弹窗（进度/网格会经任务流自动刷新）。失败则在弹窗内提示。 */
    fun submitManualFill(json: String) {
        val taskId = poolTaskId ?: return
        if (json.isBlank()) {
            _uiState.update { it.copy(manualFillError = "请粘贴 JSON 数组（该块无边可填 [])") }
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

    // ── 词池整理模型热切换（全局 POOL 作用域）──
    // callAi() 每次请求都现读 getAiConfig(POOL)，故这里写入 POOL 配置后，构建的下一次请求（含失败重试）即用新模型——
    // 无需触碰构建循环。词池构建一次只跑一个任务，故全局模型无并发冲突。

    /** 观察当前 POOL 作用域配置（提供商 + 模型），用于卡片显示与弹窗初值。 */
    private fun observePoolModelConfig() {
        viewModelScope.launch {
            settingsDataStore.scopeConfig(AiSettingsScope.POOL).collect { config ->
                _uiState.update { it.copy(poolProviderName = config.providerName, poolModel = config.model) }
            }
        }
    }

    /** 观察可用提供商（含是否已配置 API Key），供切换弹窗选择。 */
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

    /** 打开切换弹窗，编辑态以当前 POOL 配置为初值（确认前不落库）。 */
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

    /** 切换编辑中的提供商：清掉上一个提供商拉取到的模型列表；手动输入框保留，由用户决定。 */
    fun onEditingProviderChange(providerName: String) {
        _uiState.update { it.copy(editingProviderName = providerName, modelOptions = emptyList(), modelError = null) }
    }

    fun onEditingModelChange(model: String) {
        _uiState.update { it.copy(editingModel = model) }
    }

    /** 从接口拉取「编辑中提供商」的可用模型列表（复用设置页同款 FetchAiModelsUseCase）。 */
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

    /** 确认切换：写入全局 POOL 配置（scopeConfig flow 会自动回流刷新卡片显示）。 */
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
            settingsDataStore.setScopeConfig(AiSettingsScope.POOL, providerName, model)
            _uiState.update {
                it.copy(
                    modelSwitchVisible = false,
                    modelOptions = emptyList(),
                    modelLoading = false,
                    modelError = null,
                    modelSwitchMessage = "已切换词池整理模型为「$model」，从下一次请求生效。"
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
    val status: BuildStatus = BuildStatus.IDLE,
    val currentWord: String? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val strategy: String? = null,
    val rebuildMode: String? = null,
    val isPaused: Boolean = false,
    val errorMessage: String? = null,
    val errorLogs: List<String> = emptyList(),
    // 详细 chunk 信息（从 progressMessage 解析）
    val chunkCurrent: Int = 0,       // 当前 chunk 序号
    val chunkTotal: Int = 0,         // 总 chunk 数
    val edgesFound: Int = 0,         // 当前词已找到的边数
    // 实时分块网格（来自 PoolBuildLiveMonitor，仅内存态）
    val liveChunks: List<ChunkProgress> = emptyList(),
    val liveChunkWord: String? = null,
    // 手动填块
    val fillableChunkIndex: Int? = null,
    val manualFillVisible: Boolean = false,
    val manualFillLoading: Boolean = false,
    val manualFillContext: ManualChunkContext? = null,
    val manualFillError: String? = null,
    val manualFillSubmitting: Boolean = false,
    val manualFillMessage: String? = null,
    // ── 词池整理模型热切换（全局 POOL 作用域）──
    val poolProviderName: String = "",
    val poolModel: String = "",
    val poolProviders: List<PoolModelProvider> = emptyList(),
    val modelSwitchVisible: Boolean = false,
    val editingProviderName: String = "",
    val editingModel: String = "",
    val modelOptions: List<String> = emptyList(),
    val modelLoading: Boolean = false,
    val modelError: String? = null,
    val modelSwitchMessage: String? = null
)

/** 切换弹窗用的提供商摘要（名称、接口格式、Base URL、是否已配 Key）。 */
data class PoolModelProvider(
    val name: String,
    val format: AiProvider,
    val baseUrl: String,
    val hasApiKey: Boolean
)
