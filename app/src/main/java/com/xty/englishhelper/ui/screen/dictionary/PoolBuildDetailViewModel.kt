package com.xty.englishhelper.ui.screen.dictionary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
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
    private val backgroundTaskManager: BackgroundTaskManager
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(PoolBuildDetailUiState())
    val uiState: StateFlow<PoolBuildDetailUiState> = _uiState.asStateFlow()

    private var poolTaskId: Long? = null

    init {
        observePoolRebuildTasks()
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
                            errorMessage = null
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
                        edgesFound = parsedEdgesFound
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
        val mode = runCatching { RebuildMode.valueOf(state.rebuildMode ?: "") }
            .getOrDefault(RebuildMode.INCREMENTAL)
        backgroundTaskManager.enqueueWordPoolRebuild(
            dictionaryId = dictionaryId,
            strategy = poolStrategy,
            force = mode == RebuildMode.FULL,
            rebuildMode = mode
        )
    }

    fun clearErrorLogs() {
        _uiState.update { it.copy(errorLogs = emptyList()) }
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
    val edgesFound: Int = 0          // 当前词已找到的边数
)
