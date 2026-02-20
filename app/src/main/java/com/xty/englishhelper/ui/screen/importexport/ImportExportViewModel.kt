package com.xty.englishhelper.ui.screen.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.json.JsonImportExporter
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.repository.DictionaryRepository
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

                val (dictionary, words) = jsonImportExporter.importFromJson(json)
                val dictId = dictionaryRepository.insertDictionary(dictionary)

                words.forEach { word ->
                    wordRepository.insertWord(word.copy(dictionaryId = dictId))
                }
                dictionaryRepository.updateWordCount(dictId)

                _uiState.update {
                    it.copy(isLoading = false, message = "导入成功：${dictionary.name}（${words.size} 个单词）")
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
                val json = jsonImportExporter.exportToJson(dictionary, words)

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
