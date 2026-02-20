package com.xty.englishhelper.ui.screen.dictionary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.usecase.dictionary.GetDictionaryByIdUseCase
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordsByDictionaryUseCase
import com.xty.englishhelper.domain.usecase.word.SearchWordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDictionaryById: GetDictionaryByIdUseCase,
    private val getWordsByDictionary: GetWordsByDictionaryUseCase,
    private val searchWords: SearchWordsUseCase,
    private val deleteWord: DeleteWordUseCase
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadDictionary()
        observeWords()
    }

    private fun loadDictionary() {
        viewModelScope.launch {
            val dict = getDictionaryById(dictionaryId)
            _uiState.update { it.copy(dictionary = dict) }
        }
    }

    private fun observeWords() {
        viewModelScope.launch {
            _searchQuery.flatMapLatest { query ->
                if (query.isBlank()) {
                    getWordsByDictionary(dictionaryId)
                } else {
                    searchWords(dictionaryId, query)
                }
            }.catch { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }.collect { words ->
                _uiState.update { it.copy(words = words, isLoading = false) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        _searchQuery.value = query
    }

    fun showDeleteConfirm(word: WordDetails) {
        _uiState.update { it.copy(deleteTarget = word) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            deleteWord(target.id, dictionaryId)
            _uiState.update { it.copy(deleteTarget = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
