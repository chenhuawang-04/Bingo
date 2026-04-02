package com.xty.englishhelper.ui.screen.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.PlanDaySummary
import com.xty.englishhelper.domain.model.PlanAutoEventLog
import com.xty.englishhelper.domain.model.PlanItem
import com.xty.englishhelper.domain.model.PlanTaskProgress
import com.xty.englishhelper.domain.model.PlanTaskType
import com.xty.englishhelper.domain.model.PlanAutoSource
import com.xty.englishhelper.domain.model.PlanStatsMode
import com.xty.englishhelper.domain.model.PlanTemplate
import com.xty.englishhelper.domain.model.PlanTypeSummary
import com.xty.englishhelper.domain.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class PlanUiState(
    val todayStart: Long = 0L,
    val templates: List<PlanTemplate> = emptyList(),
    val activeTemplate: PlanTemplate? = null,
    val activeItems: List<PlanItem> = emptyList(),
    val todayTasks: List<PlanTaskProgress> = emptyList(),
    val todayEventLogs: List<PlanAutoEventLog> = emptyList(),
    val daySummaries: List<PlanDaySummary> = emptyList(),
    val typeSummaries: List<PlanTypeSummary> = emptyList(),
    val statsMode: PlanStatsMode = PlanStatsMode.ALL,
    val todayCompletionRate: Float = 0f,
    val streakDays: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel @Inject constructor(
    private val repository: PlanRepository
) : ViewModel() {

    private val todayStart = startOfDay(System.currentTimeMillis())
    private val _uiState = MutableStateFlow(PlanUiState(todayStart = todayStart))
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()
    private var ensuredTemplateId: Long? = null

    init {
        viewModelScope.launch {
            try {
                repository.ensureDefaultTemplate()
                repository.ensureDayRecords(todayStart)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "初始化计划失败") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        viewModelScope.launch {
            repository.observeTemplates().collectLatest { templates ->
                _uiState.update { it.copy(templates = templates) }
            }
        }

        viewModelScope.launch {
            repository.observeActiveTemplate().collectLatest { template ->
                _uiState.update { it.copy(activeTemplate = template) }
                val templateId = template?.id
                if (templateId != null && templateId != ensuredTemplateId) {
                    repository.ensureDayRecords(todayStart)
                    ensuredTemplateId = templateId
                }
            }
        }

        viewModelScope.launch {
            repository.observeActiveTemplate()
                .flatMapLatest { template ->
                    if (template == null) flowOf(emptyList()) else repository.observeItemsByTemplate(template.id)
                }
                .collectLatest { items ->
                    _uiState.update { it.copy(activeItems = items) }
                }
        }

        viewModelScope.launch {
            repository.observeTodayTasks(todayStart).collectLatest { tasks ->
                _uiState.update {
                    val rate = computeTodayCompletionRate(tasks)
                    it.copy(todayTasks = tasks, todayCompletionRate = rate)
                }
            }
        }

        viewModelScope.launch {
            repository.observeTodayEventLogs(todayStart, 20).collectLatest { logs ->
                _uiState.update { it.copy(todayEventLogs = logs) }
            }
        }

        viewModelScope.launch {
            _uiState
                .map { it.statsMode }
                .distinctUntilChanged()
                .flatMapLatest { mode -> repository.observeDaySummaries(21, mode) }
                .collectLatest { summaries ->
                _uiState.update {
                    it.copy(
                        daySummaries = summaries,
                        streakDays = calculateStreak(summaries)
                    )
                }
            }
        }

        viewModelScope.launch {
            _uiState
                .map { it.statsMode }
                .distinctUntilChanged()
                .flatMapLatest { mode -> repository.observeTypeSummaries(30, mode) }
                .collectLatest { summaries ->
                _uiState.update { it.copy(typeSummaries = summaries) }
            }
        }
    }

    fun setStatsMode(mode: PlanStatsMode) {
        _uiState.update { it.copy(statsMode = mode) }
    }

    fun incrementTask(itemId: Long) {
        val task = _uiState.value.todayTasks.firstOrNull { it.item.id == itemId } ?: return
        val nextDone = (task.record.doneCount + 1).coerceAtMost(task.item.targetCount.coerceAtLeast(1))
        updateTaskDone(itemId, nextDone)
    }

    fun decrementTask(itemId: Long) {
        val task = _uiState.value.todayTasks.firstOrNull { it.item.id == itemId } ?: return
        val nextDone = (task.record.doneCount - 1).coerceAtLeast(0)
        updateTaskDone(itemId, nextDone)
    }

    fun setTaskCompleted(itemId: Long, completed: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.setTaskCompleted(todayStart, itemId, completed)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "更新任务状态失败") }
            }
        }
    }

    fun setActiveTemplate(templateId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.setActiveTemplate(templateId)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "切换计划失败") }
            }
        }
    }

    fun createTemplate(name: String) {
        val normalized = name.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val id = repository.createTemplate(normalized)
                repository.setActiveTemplate(id)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "创建计划失败") }
            }
        }
    }

    fun renameTemplate(templateId: Long, name: String) {
        val normalized = name.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.renameTemplate(templateId, normalized)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "重命名计划失败") }
            }
        }
    }

    fun deleteTemplate(templateId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteTemplate(templateId)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "删除计划失败") }
            }
        }
    }

    fun addPlanItem(
        taskType: PlanTaskType,
        title: String,
        targetCount: Int,
        autoEnabled: Boolean,
        autoSource: PlanAutoSource?
    ) {
        val template = _uiState.value.activeTemplate ?: return
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return
        val safeTarget = targetCount.coerceAtLeast(1)
        val order = (_uiState.value.activeItems.maxOfOrNull { it.orderIndex } ?: -1) + 1
        viewModelScope.launch {
            runCatching {
                repository.addItem(
                    PlanItem(
                        templateId = template.id,
                        taskType = taskType,
                        title = normalizedTitle,
                        targetCount = safeTarget,
                        autoEnabled = autoEnabled,
                        autoSource = if (autoEnabled) autoSource else null,
                        orderIndex = order
                    )
                )
                repository.ensureDayRecords(todayStart)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "新增任务失败") }
            }
        }
    }

    fun updatePlanItem(
        itemId: Long,
        taskType: PlanTaskType,
        title: String,
        targetCount: Int,
        autoEnabled: Boolean,
        autoSource: PlanAutoSource?
    ) {
        val existing = _uiState.value.activeItems.firstOrNull { it.id == itemId } ?: return
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return
        val safeTarget = targetCount.coerceAtLeast(1)
        viewModelScope.launch {
            runCatching {
                repository.updateItem(
                    existing.copy(
                        taskType = taskType,
                        title = normalizedTitle,
                        targetCount = safeTarget,
                        autoEnabled = autoEnabled,
                        autoSource = if (autoEnabled) autoSource else null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                repository.ensureDayRecords(todayStart)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "更新任务失败") }
            }
        }
    }

    fun deletePlanItem(itemId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteItem(itemId)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "删除任务失败") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun updateTaskDone(itemId: Long, doneCount: Int) {
        viewModelScope.launch {
            runCatching {
                repository.updateTaskProgress(todayStart, itemId, doneCount)
            }.onFailure { err ->
                _uiState.update { it.copy(error = err.message ?: "更新进度失败") }
            }
        }
    }

    private fun calculateStreak(summaries: List<PlanDaySummary>): Int {
        if (summaries.isEmpty()) return 0
        val sorted = summaries.sortedByDescending { it.dayStart }
        var cursor = todayStart
        var streak = 0
        for (summary in sorted) {
            if (summary.dayStart != cursor) {
                if (summary.dayStart < cursor) break
                continue
            }
            val completed = summary.totalCount > 0 && summary.completedCount >= summary.totalCount
            if (!completed) break
            streak += 1
            cursor -= DAY_MS
        }
        return streak
    }

    private fun computeTodayCompletionRate(tasks: List<PlanTaskProgress>): Float {
        if (tasks.isEmpty()) return 0f
        val done = tasks.count { it.record.isCompleted }
        return done.toFloat() / tasks.size.toFloat()
    }

    private fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
