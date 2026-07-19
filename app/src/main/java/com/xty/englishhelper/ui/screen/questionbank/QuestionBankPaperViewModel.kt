package com.xty.englishhelper.ui.screen.questionbank

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.ExamPaperGeneratePayload
import com.xty.englishhelper.domain.model.ExamPaperSource
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestionBankPaperUiState(
    val paper: ExamPaper? = null,
    val groups: List<QuestionGroup> = emptyList(),
    val sources: List<ExamPaperSource> = emptyList(),
    val completedGroupIds: Set<Long> = emptySet(),
    val isGenerating: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class QuestionBankPaperViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: QuestionBankRepository,
    private val taskRepository: BackgroundTaskRepository,
    private val backgroundTaskManager: BackgroundTaskManager
) : ViewModel() {
    private val paperId: Long = savedStateHandle["paperId"] ?: 0L
    private val _uiState = MutableStateFlow(QuestionBankPaperUiState())
    val uiState: StateFlow<QuestionBankPaperUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllExamPapers().collect { papers ->
                _uiState.update {
                    it.copy(
                        paper = papers.firstOrNull { paper -> paper.id == paperId },
                        isLoading = false
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.getGroupsByPaper(paperId).collect { groups ->
                _uiState.update { it.copy(groups = groups.sortedBy(QuestionGroup::orderInPaper), isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.getExamPaperSources(paperId).collect { sources ->
                _uiState.update { it.copy(sources = sources, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.observeCompletedExamPaperGroupIds(paperId).collect { ids ->
                _uiState.update { it.copy(completedGroupIds = ids.toSet()) }
            }
        }
        viewModelScope.launch {
            taskRepository.observeTasksByTypes(listOf(BackgroundTaskType.EXAM_PAPER_GENERATE)).collect { tasks ->
                val generating = tasks.any { task ->
                    (task.payload as? ExamPaperGeneratePayload)?.paperId == paperId &&
                        task.status in setOf(BackgroundTaskStatus.PENDING, BackgroundTaskStatus.RUNNING)
                }
                _uiState.update { it.copy(isGenerating = generating) }
            }
        }
    }

    fun retryGeneration() {
        val paper = _uiState.value.paper ?: return
        viewModelScope.launch {
            try {
                backgroundTaskManager.enqueueExamPaperGeneration(paper.id, paper.title, force = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update { it.copy(error = "继续出卷失败：${error.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
