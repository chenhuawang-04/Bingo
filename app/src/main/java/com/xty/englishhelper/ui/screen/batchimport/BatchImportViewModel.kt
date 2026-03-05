package com.xty.englishhelper.ui.screen.batchimport

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.organize.BackgroundOrganizeManager
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import com.xty.englishhelper.domain.usecase.unit.AddWordsToUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitsWithWordCountUseCase
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExtractedWord(
    val spelling: String,
    val checked: Boolean = true
)

data class BatchImportUiState(
    val imageUris: List<Uri> = emptyList(),
    val conditions: String = "",
    val isExtracting: Boolean = false,
    val extractedWords: List<ExtractedWord> = emptyList(),
    val availableUnits: List<StudyUnit> = emptyList(),
    val selectedUnitIds: Set<Long> = emptySet(),
    val isImporting: Boolean = false,
    val importProgress: Pair<Int, Int>? = null,
    val importDone: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BatchImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val articleAiRepository: ArticleAiRepository,
    private val settingsDataStore: SettingsDataStore,
    private val getUnitsWithWordCount: GetUnitsWithWordCountUseCase,
    private val addWordsToUnit: AddWordsToUnitUseCase,
    private val saveWord: SaveWordUseCase,
    private val backgroundOrganizeManager: BackgroundOrganizeManager
) : ViewModel() {

    val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(BatchImportUiState())
    val uiState: StateFlow<BatchImportUiState> = _uiState.asStateFlow()

    init {
        loadUnits()
    }

    private fun loadUnits() {
        viewModelScope.launch {
            val units = getUnitsWithWordCount(dictionaryId).first()
            val lastSelected = settingsDataStore.getLastSelectedUnitIds(dictionaryId)
            val validIds = units.map { it.id }.toSet()
            val initialSelected = lastSelected.filter { it in validIds }.toSet()
            _uiState.update {
                it.copy(
                    availableUnits = units,
                    selectedUnitIds = initialSelected
                )
            }
        }
    }

    fun addImages(uris: List<Uri>) {
        _uiState.update { it.copy(imageUris = it.imageUris + uris) }
    }

    fun removeImage(index: Int) {
        _uiState.update {
            it.copy(imageUris = it.imageUris.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun onConditionsChange(value: String) {
        _uiState.update { it.copy(conditions = value) }
    }

    fun extractWords(readImageBytes: suspend (Uri) -> ByteArray) {
        val state = _uiState.value
        if (state.imageUris.isEmpty()) {
            _uiState.update { it.copy(error = "璇峰厛閫夋嫨鍥剧墖") }
            return
        }
        if (state.conditions.isBlank()) {
            _uiState.update { it.copy(error = "请输入提取条件") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExtracting = true, error = null, extractedWords = emptyList()) }
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.OCR)
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isExtracting = false, error = "璇峰厛鍦ㄨ缃腑閰嶇疆 API Key") }
                    return@launch
                }

                val imageBytesList = state.imageUris.map { readImageBytes(it) }
                val words = articleAiRepository.extractWordsFromImages(
                    imageBytes = imageBytesList,
                    conditions = state.conditions,
                    apiKey = config.apiKey,
                    model = config.model,
                    baseUrl = config.baseUrl,
                    provider = config.provider
                )

                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        extractedWords = words.distinct().map { w -> ExtractedWord(w) }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExtracting = false, error = "AI 提取失败：${e.message}") }
            }
        }
    }

    fun toggleWord(index: Int) {
        _uiState.update { state ->
            val updated = state.extractedWords.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(checked = !updated[index].checked)
            }
            state.copy(extractedWords = updated)
        }
    }

    fun editWord(index: Int, newSpelling: String) {
        _uiState.update { state ->
            val updated = state.extractedWords.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(spelling = newSpelling)
            }
            state.copy(extractedWords = updated)
        }
    }

    fun toggleUnitSelection(unitId: Long) {
        _uiState.update {
            val newSet = it.selectedUnitIds.toMutableSet()
            if (unitId in newSet) newSet.remove(unitId) else newSet.add(unitId)
            it.copy(selectedUnitIds = newSet)
        }
    }

    fun importWords() {
        val selected = _uiState.value.extractedWords
            .filter { it.checked && it.spelling.isNotBlank() }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(error = "请至少选择一个有效单词") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = 0 to selected.size, error = null) }
            try {
                for ((index, word) in selected.withIndex()) {
                    val wordDetails = WordDetails(
                        id = 0L,
                        dictionaryId = dictionaryId,
                        spelling = word.spelling.trim()
                    )
                    val savedId = saveWord(wordDetails)
                    if (_uiState.value.selectedUnitIds.isNotEmpty()) {
                        for (unitId in _uiState.value.selectedUnitIds) {
                            addWordsToUnit(unitId, listOf(savedId))
                        }
                    }
                    backgroundOrganizeManager.enqueue(savedId, dictionaryId, word.spelling.trim())
                    _uiState.update { it.copy(importProgress = (index + 1) to selected.size) }
                }
                settingsDataStore.setLastSelectedUnitIds(dictionaryId, _uiState.value.selectedUnitIds)
                _uiState.update { it.copy(isImporting = false, importDone = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, error = "导入失败：${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}



