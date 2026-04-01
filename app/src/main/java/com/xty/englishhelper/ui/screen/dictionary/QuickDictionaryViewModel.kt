package com.xty.englishhelper.ui.screen.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.CambridgeEntry
import com.xty.englishhelper.domain.repository.CambridgeDictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickDictionaryViewModel @Inject constructor(
    private val repository: CambridgeDictionaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickDictionaryUiState())
    val uiState: StateFlow<QuickDictionaryUiState> = _uiState.asStateFlow()

    private var suggestionJob: Job? = null
    private var entryJob: Job? = null

    fun updateQuery(query: String) {
        val oldQuery = _uiState.value.query
        val normalizedOld = oldQuery.trim()
        val normalizedNew = query.trim()
        _uiState.update {
            it.copy(
                query = query,
                error = null,
                entry = if (normalizedOld != normalizedNew) null else it.entry
            )
        }
        scheduleSuggestionFetch(query)
    }

    fun submitQuery() {
        val query = uiState.value.query.trim()
        if (query.isBlank()) return
        fetchEntry(query)
    }

    fun selectSuggestion(suggestion: String) {
        _uiState.update {
            it.copy(
                query = suggestion,
                suggestions = emptyList(),
                error = null
            )
        }
        fetchEntry(suggestion)
    }

    fun clearEntry() {
        _uiState.update { it.copy(entry = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun scheduleSuggestionFetch(query: String) {
        suggestionJob?.cancel()
        val requestedQuery = query.trim()
        if (requestedQuery.length < 2) {
            _uiState.update { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }
        suggestionJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(300)
            try {
                val suggestions = repository.searchSuggestions(requestedQuery)
                if (!isActive || _uiState.value.query.trim() != requestedQuery) return@launch
                _uiState.update {
                    it.copy(
                        suggestions = suggestions,
                        isSearching = false
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Exception) {
                if (_uiState.value.query.trim() != requestedQuery) return@launch
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = err.message ?: "获取建议失败"
                    )
                }
            }
        }
    }

    private fun fetchEntry(word: String) {
        entryJob?.cancel()
        val requestedWord = word.trim()
        entryJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingEntry = true,
                    error = null,
                    entry = null,
                    suggestions = emptyList()
                )
            }
            try {
                val entry = repository.fetchEntry(requestedWord)
                if (!isActive || !_uiState.value.query.trim().equals(requestedWord, ignoreCase = true)) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        entry = entry,
                        isLoadingEntry = false
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Exception) {
                if (!_uiState.value.query.trim().equals(requestedWord, ignoreCase = true)) return@launch
                _uiState.update {
                    it.copy(
                        isLoadingEntry = false,
                        error = err.message ?: "查询失败"
                    )
                }
            }
        }
    }
}

data class QuickDictionaryUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val entry: CambridgeEntry? = null,
    val isSearching: Boolean = false,
    val isLoadingEntry: Boolean = false,
    val error: String? = null
)
