package com.xty.englishhelper.ui.screen.dictionary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.background.ChunkProgress
import com.xty.englishhelper.domain.background.PoolBuildLiveMonitor
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.ManualChunkContext
import com.xty.englishhelper.domain.repository.ManualFillResult
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
    private val liveMonitor: PoolBuildLiveMonitor
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(PoolBuildDetailUiState())
    val uiState: StateFlow<PoolBuildDetailUiState> = _uiState.asStateFlow()

    private var poolTaskId: Long? = null

    init {
        observePoolRebuildTasks()
        observeLiveChunks()
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
    val manualFillMessage: String? = null
)
