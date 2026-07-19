package com.xty.englishhelper.ui.screen.questionbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.ExamPaperSummary
import com.xty.englishhelper.domain.model.ExamPaperGeneratePayload
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.QuestionAnswerGeneratePayload
import com.xty.englishhelper.domain.model.QuestionWritingSamplePayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestionBankListUiState(
    val papers: List<ExamPaperSummary> = emptyList(),
    val groups: List<QuestionGroup> = emptyList(),
    val isLoading: Boolean = true,
    val generatingGroupIds: Set<Long> = emptySet(),
    val generatingPaperIds: Set<Long> = emptySet(),
    val deleteConfirmPaperId: Long? = null,
    val deleteConfirmGroupId: Long? = null,
    val error: String? = null
)

@HiltViewModel
class QuestionBankListViewModel @Inject constructor(
    private val repository: QuestionBankRepository,
    private val taskRepository: BackgroundTaskRepository,
    private val backgroundTaskManager: BackgroundTaskManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionBankListUiState())
    val uiState: StateFlow<QuestionBankListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllExamPaperSummaries().collect { papers ->
                _uiState.update { it.copy(papers = papers, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repository.getAllGroupsWithPaperTitle().collect { groups ->
                _uiState.update { it.copy(groups = groups, isLoading = false) }
            }
        }
        viewModelScope.launch {
            taskRepository.observeAllTasks().collect { tasks ->
                val generating = tasks.asSequence()
                    .filter {
                        (it.type == BackgroundTaskType.QUESTION_ANSWER_GENERATE ||
                            it.type == BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH) &&
                            (it.status == BackgroundTaskStatus.PENDING || it.status == BackgroundTaskStatus.RUNNING)
                    }
                    .mapNotNull {
                        when (val payload = it.payload) {
                            is QuestionAnswerGeneratePayload -> payload.groupId
                            is QuestionWritingSamplePayload -> payload.groupId
                            else -> null
                        }
                    }
                    .toSet()
                val generatingPapers = tasks.asSequence()
                    .filter {
                        it.type == BackgroundTaskType.EXAM_PAPER_GENERATE &&
                            (it.status == BackgroundTaskStatus.PENDING || it.status == BackgroundTaskStatus.RUNNING)
                    }
                    .mapNotNull { (it.payload as? ExamPaperGeneratePayload)?.paperId }
                    .toSet()
                _uiState.update {
                    it.copy(
                        generatingGroupIds = generating,
                        generatingPaperIds = generatingPapers
                    )
                }
            }
        }
    }

    fun requestDeletePaper(paperId: Long) {
        _uiState.update { it.copy(deleteConfirmPaperId = paperId) }
    }

    fun cancelDeletePaper() {
        _uiState.update { it.copy(deleteConfirmPaperId = null) }
    }

    fun confirmDeletePaper() {
        val paperId = _uiState.value.deleteConfirmPaperId ?: return
        _uiState.update { it.copy(deleteConfirmPaperId = null) }
        viewModelScope.launch {
            runCatching { repository.deleteExamPaper(paperId) }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "删除试卷失败：${error.message}") }
                }
        }
    }

    fun retryPaper(paperId: Long) {
        viewModelScope.launch {
            try {
                val paper = repository.getExamPaperById(paperId) ?: return@launch
                backgroundTaskManager.enqueueExamPaperGeneration(paper.id, paper.title, force = true)
            } catch (error: Exception) {
                _uiState.update { it.copy(error = "重新出卷失败：${error.message}") }
            }
        }
    }

    fun requestDelete(groupId: Long) {
        _uiState.update { it.copy(deleteConfirmGroupId = groupId) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(deleteConfirmGroupId = null) }
    }

    fun confirmDelete() {
        val groupId = _uiState.value.deleteConfirmGroupId ?: return
        _uiState.update { it.copy(deleteConfirmGroupId = null) }
        viewModelScope.launch {
            try {
                repository.deleteQuestionGroup(groupId)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "删除失败：${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
