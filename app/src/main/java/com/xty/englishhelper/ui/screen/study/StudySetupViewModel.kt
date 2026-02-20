package com.xty.englishhelper.ui.screen.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.usecase.study.CountDueWordsUseCase
import com.xty.englishhelper.domain.usecase.study.CountNewWordsUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitsWithWordCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudySetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getUnitsWithWordCount: GetUnitsWithWordCountUseCase,
    private val countDueWords: CountDueWordsUseCase,
    private val countNewWords: CountNewWordsUseCase
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(StudySetupUiState())
    val uiState: StateFlow<StudySetupUiState> = _uiState.asStateFlow()

    init {
        loadUnits()
    }

    private fun loadUnits() {
        viewModelScope.launch {
            try {
                val units = getUnitsWithWordCount(dictionaryId).first()
                val items = units.map { unit ->
                    val due = countDueWords(unit.id)
                    val newW = countNewWords(unit.id)
                    UnitStudyInfo(
                        unitId = unit.id,
                        unitName = unit.name,
                        wordCount = unit.wordCount,
                        dueCount = due,
                        newCount = newW
                    )
                }
                _uiState.update {
                    it.copy(
                        unitItems = items,
                        selectedUnitIds = items.filter { item -> item.dueCount > 0 || item.newCount > 0 }
                            .map { item -> item.unitId }.toSet(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun toggleUnit(unitId: Long) {
        _uiState.update {
            val newSet = it.selectedUnitIds.toMutableSet()
            if (unitId in newSet) newSet.remove(unitId) else newSet.add(unitId)
            it.copy(selectedUnitIds = newSet)
        }
    }

    fun getSelectedUnitIdsString(): String =
        _uiState.value.selectedUnitIds.joinToString(",")

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
