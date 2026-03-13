package com.xty.englishhelper.ui.screen.questionbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestionBankListUiState(
    val groups: List<QuestionGroup> = emptyList(),
    val isLoading: Boolean = true,
    val deleteConfirmGroupId: Long? = null,
    val error: String? = null
)

@HiltViewModel
class QuestionBankListViewModel @Inject constructor(
    private val repository: QuestionBankRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionBankListUiState())
    val uiState: StateFlow<QuestionBankListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllGroupsWithPaperTitle().collect { groups ->
                _uiState.update { it.copy(groups = groups, isLoading = false) }
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
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "删除失败：${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
