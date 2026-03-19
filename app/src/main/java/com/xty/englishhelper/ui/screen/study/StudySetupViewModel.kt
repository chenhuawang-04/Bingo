package com.xty.englishhelper.ui.screen.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.usecase.study.CountDueWordsUseCase
import com.xty.englishhelper.domain.usecase.study.CountNewWordsUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitsWithWordCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val refreshSignal = savedStateHandle.getStateFlow("study_refresh_token", 0L)

    private val _uiState = MutableStateFlow(StudySetupUiState())
    val uiState: StateFlow<StudySetupUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadUnits(showLoading = true)
        observeRefreshSignal()
    }

    private fun observeRefreshSignal() {
        viewModelScope.launch {
            refreshSignal.collect { token ->
                if (token != 0L) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        loadUnits(showLoading = false)
    }

    private fun loadUnits(showLoading: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val previousSelected = _uiState.value.selectedUnitIds
                if (showLoading) {
                    _uiState.update { it.copy(isLoading = true) }
                }
                val units = getUnitsWithWordCount(dictionaryId).first()
                val items = coroutineScope {
                    units.map { unit ->
                        async {
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
                    }.map { it.await() }
                }
                val existingUnitIds = items.map { it.unitId }.toSet()
                val availableUnitIds = items
                    .filter { item -> item.dueCount > 0 || item.newCount > 0 }
                    .map { item -> item.unitId }
                    .toSet()
                val restoredSelection = previousSelected.intersect(existingUnitIds)
                val selectedUnitIds = if (restoredSelection.isNotEmpty()) {
                    restoredSelection
                } else {
                    availableUnitIds
                }
                _uiState.update {
                    it.copy(
                        unitItems = items,
                        selectedUnitIds = selectedUnitIds,
                        isLoading = false,
                        error = null
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

    fun getSelectedModeString(): String =
        _uiState.value.selectedMode.name

    fun setMode(mode: StudyMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
