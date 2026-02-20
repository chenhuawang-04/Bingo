package com.xty.englishhelper.ui.screen.unitdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.usecase.unit.AddWordsToUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.DeleteUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitByIdUseCase
import com.xty.englishhelper.domain.usecase.unit.GetWordIdsInUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.GetWordsInUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.RemoveWordsFromUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.RenameUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.UpdateRepeatCountUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordsByDictionaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getUnitById: GetUnitByIdUseCase,
    private val getWordsInUnit: GetWordsInUnitUseCase,
    private val getWordIdsInUnit: GetWordIdsInUnitUseCase,
    private val getWordsByDictionary: GetWordsByDictionaryUseCase,
    private val addWordsToUnit: AddWordsToUnitUseCase,
    private val removeWordsFromUnit: RemoveWordsFromUnitUseCase,
    private val renameUnit: RenameUnitUseCase,
    private val updateRepeatCount: UpdateRepeatCountUseCase,
    private val deleteUnit: DeleteUnitUseCase
) : ViewModel() {

    private val unitId: Long = savedStateHandle["unitId"] ?: 0L
    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(UnitDetailUiState())
    val uiState: StateFlow<UnitDetailUiState> = _uiState.asStateFlow()

    init {
        loadUnit()
        observeWords()
    }

    private fun loadUnit() {
        viewModelScope.launch {
            val unit = getUnitById(unitId)
            _uiState.update { it.copy(unit = unit) }
        }
    }

    private fun observeWords() {
        viewModelScope.launch {
            getWordsInUnit(unitId)
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { words ->
                    _uiState.update { it.copy(wordsInUnit = words, isLoading = false) }
                }
        }
    }

    // Rename
    fun showRenameDialog() {
        _uiState.update {
            it.copy(showRenameDialog = true, renameText = it.unit?.name ?: "")
        }
    }

    fun onRenameTextChange(text: String) {
        _uiState.update { it.copy(renameText = text) }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false) }
    }

    fun confirmRename() {
        val name = _uiState.value.renameText.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            renameUnit(unitId, name)
            loadUnit()
            _uiState.update { it.copy(showRenameDialog = false) }
        }
    }

    // Repeat count
    fun showRepeatCountDialog() {
        _uiState.update {
            it.copy(
                showRepeatCountDialog = true,
                repeatCountText = (it.unit?.defaultRepeatCount ?: 2).toString()
            )
        }
    }

    fun onRepeatCountTextChange(text: String) {
        _uiState.update { it.copy(repeatCountText = text) }
    }

    fun dismissRepeatCountDialog() {
        _uiState.update { it.copy(showRepeatCountDialog = false) }
    }

    fun confirmRepeatCount() {
        val count = _uiState.value.repeatCountText.toIntOrNull() ?: return
        if (count < 1) return
        viewModelScope.launch {
            updateRepeatCount(unitId, count)
            loadUnit()
            _uiState.update { it.copy(showRepeatCountDialog = false) }
        }
    }

    // Add words dialog
    fun showAddWordsDialog() {
        viewModelScope.launch {
            val allWords = getWordsByDictionary(dictionaryId).first()
            val currentIds = getWordIdsInUnit(unitId).toSet()
            _uiState.update {
                it.copy(
                    showAddWordsDialog = true,
                    allWordsInDictionary = allWords,
                    wordIdsInUnit = currentIds,
                    addWordsSelection = currentIds.toMutableSet()
                )
            }
        }
    }

    fun toggleWordSelection(wordId: Long) {
        _uiState.update {
            val newSelection = it.addWordsSelection.toMutableSet()
            if (wordId in newSelection) {
                newSelection.remove(wordId)
            } else {
                newSelection.add(wordId)
            }
            it.copy(addWordsSelection = newSelection)
        }
    }

    fun dismissAddWordsDialog() {
        _uiState.update { it.copy(showAddWordsDialog = false) }
    }

    fun confirmAddWords() {
        viewModelScope.launch {
            val state = _uiState.value
            val toAdd = state.addWordsSelection - state.wordIdsInUnit
            val toRemove = state.wordIdsInUnit - state.addWordsSelection

            if (toAdd.isNotEmpty()) {
                addWordsToUnit(unitId, toAdd.toList())
            }
            if (toRemove.isNotEmpty()) {
                removeWordsFromUnit(unitId, toRemove.toList())
            }

            _uiState.update { it.copy(showAddWordsDialog = false) }
        }
    }

    // Remove word
    fun showRemoveWordConfirm(word: WordDetails) {
        _uiState.update { it.copy(removeWordTarget = word) }
    }

    fun dismissRemoveWord() {
        _uiState.update { it.copy(removeWordTarget = null) }
    }

    fun confirmRemoveWord() {
        val target = _uiState.value.removeWordTarget ?: return
        viewModelScope.launch {
            removeWordsFromUnit(unitId, listOf(target.id))
            _uiState.update { it.copy(removeWordTarget = null) }
        }
    }

    // Delete unit
    fun showDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDeleteUnit(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteUnit(unitId)
            _uiState.update { it.copy(showDeleteConfirm = false) }
            onDeleted()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
