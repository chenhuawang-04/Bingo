package com.xty.englishhelper.ui.screen.word

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase
import com.xty.englishhelper.domain.usecase.word.GetAssociatedWordsUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordByIdUseCase
import com.xty.englishhelper.domain.usecase.word.ResolveLinkedWordsUseCase
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
    private val deleteWord: DeleteWordUseCase,
    private val resolveLinkedWords: ResolveLinkedWordsUseCase,
    private val getAssociatedWords: GetAssociatedWordsUseCase
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

                if (word != null) {
                    // Resolve linked words (synonyms, similar words, cognates)
                    val allSpellings = word.synonyms.map { it.word } +
                            word.similarWords.map { it.word } +
                            word.cognates.map { it.word }
                    if (allSpellings.isNotEmpty()) {
                        val linkedIds = resolveLinkedWords(dictionaryId, allSpellings)
                        _uiState.update { it.copy(linkedWordIds = linkedIds) }
                    }

                    // Load associated words
                    val associated = getAssociatedWords(wordId)
                    _uiState.update { it.copy(associatedWords = associated) }
                }
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
