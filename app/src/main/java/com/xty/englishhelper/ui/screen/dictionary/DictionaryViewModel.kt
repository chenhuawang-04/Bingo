package com.xty.englishhelper.ui.screen.dictionary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.usecase.dictionary.GetDictionaryByIdUseCase
import com.xty.englishhelper.domain.usecase.unit.CreateUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitsWithWordCountUseCase
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
    private val deleteWord: DeleteWordUseCase,
    private val getUnitsWithWordCount: GetUnitsWithWordCountUseCase,
    private val createUnit: CreateUnitUseCase
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadDictionary()
        observeWords()
        observeUnits()
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
                _uiState.update {
                    val maxPage = if (words.isEmpty()) 0 else (words.size + it.pageSize - 1) / it.pageSize - 1
                    it.copy(words = words, isLoading = false, currentPage = it.currentPage.coerceAtMost(maxPage.coerceAtLeast(0)))
                }
            }
        }
    }

    private fun observeUnits() {
        viewModelScope.launch {
            getUnitsWithWordCount(dictionaryId)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
                .collect { units ->
                    _uiState.update { it.copy(units = units) }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, currentPage = 0) }
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

    fun showCreateUnitDialog() {
        _uiState.update { it.copy(showCreateUnitDialog = true, newUnitName = "") }
    }

    fun dismissCreateUnitDialog() {
        _uiState.update { it.copy(showCreateUnitDialog = false, newUnitName = "") }
    }

    fun onNewUnitNameChange(name: String) {
        _uiState.update { it.copy(newUnitName = name) }
    }

    fun confirmCreateUnit() {
        val name = _uiState.value.newUnitName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            createUnit(dictionaryId, name)
            _uiState.update { it.copy(showCreateUnitDialog = false, newUnitName = "") }
        }
    }

    fun goToPage(page: Int) {
        _uiState.update {
            val maxPage = (it.totalPages - 1).coerceAtLeast(0)
            it.copy(currentPage = page.coerceIn(0, maxPage))
        }
    }

    fun nextPage() = goToPage(_uiState.value.currentPage + 1)
    fun previousPage() = goToPage(_uiState.value.currentPage - 1)

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
