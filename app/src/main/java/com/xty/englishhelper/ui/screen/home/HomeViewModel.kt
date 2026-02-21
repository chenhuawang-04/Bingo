package com.xty.englishhelper.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.study.FsrsConstants
import com.xty.englishhelper.domain.usecase.dictionary.CreateDictionaryUseCase
import com.xty.englishhelper.domain.usecase.dictionary.DeleteDictionaryUseCase
import com.xty.englishhelper.domain.usecase.dictionary.GetAllDictionariesUseCase
import com.xty.englishhelper.ui.theme.DictionaryColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.pow

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllDictionaries: GetAllDictionariesUseCase,
    private val createDictionary: CreateDictionaryUseCase,
    private val deleteDictionary: DeleteDictionaryUseCase,
    private val studyRepository: StudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadDictionaries()
        loadDashboard()
    }

    private fun loadDictionaries() {
        viewModelScope.launch {
            getAllDictionaries()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { dictionaries ->
                    _uiState.update { it.copy(dictionaries = dictionaries, isLoading = false) }
                }
        }
    }

    fun refreshDashboard() {
        loadDashboard()
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()

                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val activeStates = studyRepository.getAllActiveStudyStates()
                if (activeStates.isEmpty()) {
                    _uiState.update { it.copy(dashboard = DashboardStats(hasData = false)) }
                    return@launch
                }

                val averageR = activeStates.map { state ->
                    val elapsedDays = max(0.0, (now - state.lastReviewAt).toDouble() / (1000.0 * 60 * 60 * 24))
                    val s = state.stability.coerceAtLeast(FsrsConstants.STABILITY_MIN)
                    (1.0 + FsrsConstants.FACTOR * elapsedDays / s).pow(FsrsConstants.DECAY)
                }.average().coerceIn(0.0, 1.0)

                val dueCount = studyRepository.countAllDueWords(now)
                val reviewedToday = studyRepository.countReviewedToday(todayStart, now)
                val todayTotal = reviewedToday + dueCount

                val hoursElapsed = (now - todayStart).toDouble() / (1000.0 * 60 * 60)
                val estimatedClearHours = if (reviewedToday > 0 && hoursElapsed > 0 && dueCount > 0) {
                    dueCount.toDouble() / (reviewedToday.toDouble() / hoursElapsed)
                } else {
                    null
                }

                _uiState.update {
                    it.copy(
                        dashboard = DashboardStats(
                            averageRetention = averageR,
                            dueCount = dueCount,
                            reviewedToday = reviewedToday,
                            todayTotal = todayTotal,
                            estimatedClearHours = estimatedClearHours,
                            hasData = true
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Failed to load dashboard stats", e)
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                newDictName = "",
                newDictDesc = "",
                selectedColorIndex = (0 until DictionaryColors.size).random()
            )
        }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(newDictName = name) }
    }

    fun onDescChange(desc: String) {
        _uiState.update { it.copy(newDictDesc = desc) }
    }

    fun onColorSelect(index: Int) {
        _uiState.update { it.copy(selectedColorIndex = index) }
    }

    fun confirmCreate() {
        val state = _uiState.value
        if (state.newDictName.isBlank()) return
        viewModelScope.launch {
            val color = DictionaryColors[state.selectedColorIndex].value.toLong().toInt()
            createDictionary(state.newDictName.trim(), state.newDictDesc.trim(), color)
            _uiState.update { it.copy(showCreateDialog = false) }
        }
    }

    fun showDeleteConfirm(dict: com.xty.englishhelper.domain.model.Dictionary) {
        _uiState.update { it.copy(deleteTarget = dict) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            deleteDictionary(target.id)
            _uiState.update { it.copy(deleteTarget = null) }
            refreshDashboard()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
