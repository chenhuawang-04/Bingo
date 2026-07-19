package com.xty.englishhelper.ui.screen.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.background.AppResourceCoordinator
import com.xty.englishhelper.domain.background.ForegroundResourceDemand
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.usecase.dictionary.GetAllDictionariesUseCase
import com.xty.englishhelper.domain.usecase.importexport.ExportDictionaryUseCase
import com.xty.englishhelper.domain.usecase.importexport.ExportPlanUseCase
import com.xty.englishhelper.domain.usecase.importexport.ImportDictionaryUseCase
import com.xty.englishhelper.domain.usecase.importexport.ImportPlanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val getAllDictionaries: GetAllDictionariesUseCase,
    private val importDictionaryUseCase: ImportDictionaryUseCase,
    private val exportDictionaryUseCase: ExportDictionaryUseCase,
    private val importPlanUseCase: ImportPlanUseCase,
    private val exportPlanUseCase: ExportPlanUseCase
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
                val message = AppResourceCoordinator.withResourceObservation(
                    owner = "dictionary_import",
                    demand = ForegroundResourceDemand(memoryHeavy = 1, cpuHeavy = 1, databaseWriter = 1)
                ) {
                    val json = readText(context, uri)
                    importDictionaryUseCase(json)
                }
                _uiState.update { it.copy(isLoading = false, message = message) }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "导入失败：${e.message}") }
            }
        }
    }

    fun exportDictionary(context: Context, dictionary: Dictionary, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                AppResourceCoordinator.withResourceUsage(
                    owner = "dictionary_export",
                    demand = ForegroundResourceDemand(memoryHeavy = 1, cpuHeavy = 1)
                ) {
                    val json = exportDictionaryUseCase(dictionary)
                    writeText(context, uri, json)
                }

                _uiState.update { it.copy(isLoading = false, message = "导出成功") }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "导出失败：${e.message}") }
            }
        }
    }

    fun importPlanFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val message = AppResourceCoordinator.withResourceObservation(
                    owner = "plan_import",
                    demand = ForegroundResourceDemand(memoryHeavy = 1, cpuHeavy = 1, databaseWriter = 1)
                ) {
                    importPlanUseCase(readText(context, uri))
                }
                _uiState.update { it.copy(isLoading = false, message = message) }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "计划导入失败：${e.message}") }
            }
        }
    }

    fun exportPlanToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                AppResourceCoordinator.withResourceUsage(
                    owner = "plan_export",
                    demand = ForegroundResourceDemand(memoryHeavy = 1, cpuHeavy = 1)
                ) {
                    writeText(context, uri, exportPlanUseCase())
                }
                _uiState.update { it.copy(isLoading = false, message = "计划导出成功") }
            } catch (error: CancellationException) {
                throw error
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "计划导出失败：${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(16 * 1024)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(result.length + count <= MAX_IMPORT_CHARS) { "导入文件过大" }
                result.append(buffer, 0, count)
            }
            result.toString()
        } ?: throw IllegalStateException("无法读取文件")
    }

    private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            ?: throw IllegalStateException("无法写入文件")
    }

    private companion object {
        const val MAX_IMPORT_CHARS = 50 * 1024 * 1024
    }
}
