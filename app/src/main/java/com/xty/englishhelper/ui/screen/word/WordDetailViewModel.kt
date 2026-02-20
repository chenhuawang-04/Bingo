package com.xty.englishhelper.ui.screen.word

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWordById: GetWordByIdUseCase,
    private val deleteWord: DeleteWordUseCase
) : ViewModel() {

    private val wordId: Long = savedStateHandle["wordId"] ?: 0L
    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(WordDetailUiState())
    val uiState: StateFlow<WordDetailUiState> = _uiState.asStateFlow()

    init {
        loadWord()
    }

    fun loadWord() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val word = getWordById(wordId)
                _uiState.update { it.copy(word = word, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteWord(wordId, dictionaryId)
            onDeleted()
        }
    }
}
