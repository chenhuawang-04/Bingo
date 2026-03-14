package com.xty.englishhelper.ui.screen.backgroundtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackgroundTaskUiState(
    val tasks: List<BackgroundTask> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL
)

enum class TaskFilter {
    ALL, PENDING, RUNNING, PAUSED, FAILED, SUCCESS, CANCELED
}

@HiltViewModel
class BackgroundTaskViewModel @Inject constructor(
    private val repository: BackgroundTaskRepository,
    private val manager: BackgroundTaskManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackgroundTaskUiState())
    val uiState: StateFlow<BackgroundTaskUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllTasks().collect { tasks ->
                _uiState.update { it.copy(tasks = tasks.sortedByDescending { t -> t.createdAt }) }
            }
        }
    }

    fun setFilter(filter: TaskFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun pauseAll() {
        manager.pauseAll()
    }

    fun cancelAll() {
        manager.cancelAll()
    }

    fun resumeAll() {
        manager.resumeAll()
    }

    fun retryFailed() {
        manager.retryFailed()
    }

    fun clearFinished() {
        manager.clearFinished()
    }

    fun cancelTask(taskId: Long) {
        manager.cancelTask(taskId)
    }

    fun resumeTask(taskId: Long) {
        manager.resumeTask(taskId)
    }

    fun restartTask(taskId: Long) {
        manager.restartTask(taskId)
    }

    fun deleteTask(taskId: Long) {
        manager.deleteTask(taskId)
    }

    fun matchesFilter(task: BackgroundTask): Boolean {
        return when (_uiState.value.filter) {
            TaskFilter.ALL -> true
            TaskFilter.PENDING -> task.status == BackgroundTaskStatus.PENDING
            TaskFilter.RUNNING -> task.status == BackgroundTaskStatus.RUNNING
            TaskFilter.PAUSED -> task.status == BackgroundTaskStatus.PAUSED
            TaskFilter.FAILED -> task.status == BackgroundTaskStatus.FAILED
            TaskFilter.SUCCESS -> task.status == BackgroundTaskStatus.SUCCESS
            TaskFilter.CANCELED -> task.status == BackgroundTaskStatus.CANCELED
        }
    }
}
