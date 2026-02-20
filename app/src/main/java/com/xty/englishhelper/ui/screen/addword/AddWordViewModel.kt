package com.xty.englishhelper.ui.screen.addword

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.usecase.ai.OrganizeWordWithAiUseCase
import com.xty.englishhelper.domain.usecase.unit.AddWordsToUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitIdsForWordUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitsWithWordCountUseCase
import com.xty.englishhelper.domain.usecase.unit.RemoveWordsFromUnitUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordByIdUseCase
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddWordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWordById: GetWordByIdUseCase,
    private val saveWord: SaveWordUseCase,
    private val organizeWordWithAi: OrganizeWordWithAiUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val getUnitsWithWordCount: GetUnitsWithWordCountUseCase,
    private val addWordsToUnit: AddWordsToUnitUseCase,
    private val removeWordsFromUnit: RemoveWordsFromUnitUseCase,
    private val getUnitIdsForWord: GetUnitIdsForWordUseCase
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L
    private val wordId: Long = savedStateHandle["wordId"] ?: 0L

    private val _uiState = MutableStateFlow(AddWordUiState())
    val uiState: StateFlow<AddWordUiState> = _uiState.asStateFlow()

    init {
        loadUnits()
        if (wordId != 0L) {
            loadExistingWord()
        }
    }

    private fun loadUnits() {
        viewModelScope.launch {
            try {
                val units = getUnitsWithWordCount(dictionaryId).first()
                val lastSelected = settingsDataStore.getLastSelectedUnitIds(dictionaryId)
                // Only pre-select units that still exist
                val validIds = units.map { it.id }.toSet()
                val initialSelected = if (wordId != 0L) {
                    // Edit mode: load actual associations
                    getUnitIdsForWord(wordId).toSet()
                } else {
                    lastSelected.filter { it in validIds }.toSet()
                }
                _uiState.update {
                    it.copy(
                        availableUnits = units,
                        selectedUnitIds = initialSelected
                    )
                }
            } catch (_: Exception) {
                // Units are optional; silently ignore errors
            }
        }
    }

    private fun loadExistingWord() {
        viewModelScope.launch {
            try {
                val word = getWordById(wordId) ?: return@launch
                _uiState.update {
                    it.copy(
                        isEditing = true,
                        spelling = word.spelling,
                        phonetic = word.phonetic,
                        meanings = word.meanings.ifEmpty { listOf(Meaning("", "")) },
                        rootExplanation = word.rootExplanation,
                        decomposition = word.decomposition,
                        synonyms = word.synonyms,
                        similarWords = word.similarWords,
                        cognates = word.cognates
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun onSpellingChange(value: String) {
        _uiState.update { it.copy(spelling = value) }
    }

    fun onPhoneticChange(value: String) {
        _uiState.update { it.copy(phonetic = value) }
    }

    fun onRootExplanationChange(value: String) {
        _uiState.update { it.copy(rootExplanation = value) }
    }

    fun toggleUnitSelection(unitId: Long) {
        _uiState.update {
            val newSet = it.selectedUnitIds.toMutableSet()
            if (unitId in newSet) newSet.remove(unitId) else newSet.add(unitId)
            it.copy(selectedUnitIds = newSet)
        }
    }

    // Meanings
    fun onMeaningChange(index: Int, meaning: Meaning) {
        _uiState.update {
            val list = it.meanings.toMutableList()
            list[index] = meaning
            it.copy(meanings = list)
        }
    }

    fun addMeaning() {
        _uiState.update { it.copy(meanings = it.meanings + Meaning("", "")) }
    }

    fun removeMeaning(index: Int) {
        _uiState.update {
            if (it.meanings.size <= 1) return@update it
            it.copy(meanings = it.meanings.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    // Synonyms
    fun onSynonymChange(index: Int, synonym: SynonymInfo) {
        _uiState.update {
            val list = it.synonyms.toMutableList()
            list[index] = synonym
            it.copy(synonyms = list)
        }
    }

    fun addSynonym() {
        _uiState.update { it.copy(synonyms = it.synonyms + SynonymInfo(word = "", explanation = "")) }
    }

    fun removeSynonym(index: Int) {
        _uiState.update {
            it.copy(synonyms = it.synonyms.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    // Similar words
    fun onSimilarWordChange(index: Int, similarWord: SimilarWordInfo) {
        _uiState.update {
            val list = it.similarWords.toMutableList()
            list[index] = similarWord
            it.copy(similarWords = list)
        }
    }

    fun addSimilarWord() {
        _uiState.update {
            it.copy(similarWords = it.similarWords + SimilarWordInfo(word = "", meaning = "", explanation = ""))
        }
    }

    fun removeSimilarWord(index: Int) {
        _uiState.update {
            it.copy(similarWords = it.similarWords.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    // Cognates
    fun onCognateChange(index: Int, cognate: CognateInfo) {
        _uiState.update {
            val list = it.cognates.toMutableList()
            list[index] = cognate
            it.copy(cognates = list)
        }
    }

    fun addCognate() {
        _uiState.update {
            it.copy(cognates = it.cognates + CognateInfo(word = "", meaning = "", sharedRoot = ""))
        }
    }

    fun removeCognate(index: Int) {
        _uiState.update {
            it.copy(cognates = it.cognates.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    // Decomposition
    fun onDecompositionPartChange(index: Int, part: DecompositionPart) {
        _uiState.update {
            val list = it.decomposition.toMutableList()
            list[index] = part
            it.copy(decomposition = list)
        }
    }

    fun addDecompositionPart() {
        _uiState.update {
            it.copy(decomposition = it.decomposition + DecompositionPart(segment = "", role = MorphemeRole.ROOT))
        }
    }

    fun removeDecompositionPart(index: Int) {
        _uiState.update {
            it.copy(decomposition = it.decomposition.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    // AI organize
    fun organizeWithAi() {
        val spelling = _uiState.value.spelling.trim()
        if (spelling.isBlank()) {
            _uiState.update { it.copy(error = "请先输入单词") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true, error = null) }
            try {
                val apiKey = settingsDataStore.apiKey.first()
                val model = settingsDataStore.model.first()
                val baseUrl = settingsDataStore.baseUrl.first()

                if (apiKey.isBlank()) {
                    _uiState.update { it.copy(isAiLoading = false, error = "请先在设置中配置 API Key") }
                    return@launch
                }

                val result = organizeWordWithAi(spelling, apiKey, model, baseUrl)
                _uiState.update {
                    it.copy(
                        isAiLoading = false,
                        phonetic = result.phonetic.ifBlank { it.phonetic },
                        meanings = result.meanings.ifEmpty { it.meanings },
                        rootExplanation = result.rootExplanation.ifBlank { it.rootExplanation },
                        decomposition = result.decomposition.ifEmpty { it.decomposition },
                        synonyms = result.synonyms.ifEmpty { it.synonyms },
                        similarWords = result.similarWords.ifEmpty { it.similarWords },
                        cognates = result.cognates.ifEmpty { it.cognates }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAiLoading = false, error = "AI 整理失败：${e.message}") }
            }
        }
    }

    // Save
    fun save() {
        val state = _uiState.value
        if (state.spelling.isBlank()) {
            _uiState.update { it.copy(error = "请输入单词拼写") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val word = WordDetails(
                    id = wordId,
                    dictionaryId = dictionaryId,
                    spelling = state.spelling.trim(),
                    phonetic = state.phonetic.trim(),
                    meanings = state.meanings.filter { it.definition.isNotBlank() },
                    rootExplanation = state.rootExplanation.trim(),
                    decomposition = state.decomposition.filter { it.segment.isNotBlank() },
                    synonyms = state.synonyms.filter { it.word.isNotBlank() },
                    similarWords = state.similarWords.filter { it.word.isNotBlank() },
                    cognates = state.cognates.filter { it.word.isNotBlank() }
                )
                val savedWordId = saveWord(word)

                // Handle unit associations
                val selectedIds = state.selectedUnitIds
                if (wordId != 0L) {
                    // Edit mode: compute diff
                    val currentUnitIds = getUnitIdsForWord(savedWordId).toSet()
                    val toAdd = selectedIds - currentUnitIds
                    val toRemove = currentUnitIds - selectedIds
                    for (unitId in toAdd) {
                        addWordsToUnit(unitId, listOf(savedWordId))
                    }
                    for (unitId in toRemove) {
                        removeWordsFromUnit(unitId, listOf(savedWordId))
                    }
                } else {
                    // New word: add to all selected units
                    for (unitId in selectedIds) {
                        addWordsToUnit(unitId, listOf(savedWordId))
                    }
                }

                // Remember selected unit IDs
                settingsDataStore.setLastSelectedUnitIds(dictionaryId, selectedIds)

                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "保存失败：${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
