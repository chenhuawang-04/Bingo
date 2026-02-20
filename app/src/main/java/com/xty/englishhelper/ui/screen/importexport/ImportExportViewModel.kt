package com.xty.englishhelper.ui.screen.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.json.JsonImportExporter
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.usecase.dictionary.GetAllDictionariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val getAllDictionaries: GetAllDictionariesUseCase,
    private val dictionaryRepository: DictionaryRepository,
    private val wordRepository: WordRepository,
    private val unitRepository: UnitRepository,
    private val studyRepository: StudyRepository,
    private val jsonImportExporter: JsonImportExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportExportUiState())
    val uiState: StateFlow<ImportExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getAllDictionaries().collect { dicts ->
                _uiState.update { it.copy(dictionaries = dicts) }
            }
        }
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: throw IllegalStateException("无法读取文件")

                val result = jsonImportExporter.importFromJson(json)
                val dictId = dictionaryRepository.insertDictionary(result.dictionary)

                // Insert words and build spelling->id map
                val spellingToId = mutableMapOf<String, Long>()
                result.words.forEach { word ->
                    val wordId = wordRepository.insertWord(word.copy(dictionaryId = dictId))
                    spellingToId[word.spelling] = wordId
                }
                dictionaryRepository.updateWordCount(dictId)

                // Import units
                result.units.forEach { unitJson ->
                    val unitId = unitRepository.insertUnit(
                        StudyUnit(
                            dictionaryId = dictId,
                            name = unitJson.name,
                            defaultRepeatCount = unitJson.repeatCount
                        )
                    )
                    val wordIds = unitJson.wordSpellings.mapNotNull { spellingToId[it] }
                    if (wordIds.isNotEmpty()) {
                        unitRepository.addWordsToUnit(unitId, wordIds)
                    }
                }

                // Import study states
                result.studyStates.forEach { stateJson ->
                    val wordId = spellingToId[stateJson.spelling] ?: return@forEach
                    studyRepository.upsertStudyState(
                        WordStudyState(
                            wordId = wordId,
                            remainingReviews = stateJson.remainingReviews,
                            easeLevel = stateJson.easeLevel,
                            nextReviewAt = stateJson.nextReviewAt,
                            lastReviewedAt = stateJson.lastReviewedAt
                        )
                    )
                }

                _uiState.update {
                    it.copy(isLoading = false, message = "导入成功：${result.dictionary.name}（${result.words.size} 个单词）")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "导入失败：${e.message}") }
            }
        }
    }

    fun exportDictionary(context: Context, dictionary: Dictionary, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val words = wordRepository.getWordsByDictionary(dictionary.id).first()
                val units = unitRepository.getUnitsByDictionary(dictionary.id)
                val studyStates = studyRepository.getStudyStatesForDictionary(dictionary.id)

                // Build wordId -> spelling map
                val wordIdToSpelling = words.associate { it.id to it.spelling }

                // Build unitId -> list of word spellings
                val unitWordMap = mutableMapOf<Long, List<String>>()
                for (unit in units) {
                    val wordIds = unitRepository.getWordIdsInUnit(unit.id)
                    unitWordMap[unit.id] = wordIds.mapNotNull { wordIdToSpelling[it] }
                }

                val json = jsonImportExporter.exportToJson(
                    dictionary = dictionary,
                    words = words,
                    units = units,
                    unitWordMap = unitWordMap,
                    studyStates = studyStates,
                    wordIdToSpelling = wordIdToSpelling
                )

                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(json)
                } ?: throw IllegalStateException("无法写入文件")

                _uiState.update { it.copy(isLoading = false, message = "导出成功") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "导出失败：${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}
