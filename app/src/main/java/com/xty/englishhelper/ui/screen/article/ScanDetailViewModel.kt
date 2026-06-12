package com.xty.englishhelper.ui.screen.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanDetailUiState(
    val scanTask: BackgroundTask? = null,
    val rescoreAfterHours: Int = 24,
    val isConfigExpanded: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScanDetailViewModel @Inject constructor(
    private val backgroundTaskRepository: BackgroundTaskRepository,
    private val backgroundTaskManager: BackgroundTaskManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanDetailUiState())
    val uiState: StateFlow<ScanDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            backgroundTaskRepository.getTasksByType(BackgroundTaskType.ONLINE_ARTICLE_SCAN_SCORE)
                .collect { tasks ->
                    val task = tasks.firstOrNull { it.status != BackgroundTaskStatus.CANCELED }
                    _uiState.update { it.copy(scanTask = task) }
                }
        }
        viewModelScope.launch {
            settingsDataStore.scanRescoreAfterHours.collect { value ->
                _uiState.update { it.copy(rescoreAfterHours = value) }
            }
        }
    }

    fun toggleScanConfig() {
        _uiState.update { it.copy(isConfigExpanded = !it.isConfigExpanded) }
    }

    fun setScanRescoreAfterHours(value: Int) {
        val clamped = value.coerceIn(1, 720)
        _uiState.update { it.copy(rescoreAfterHours = clamped) }
        viewModelScope.launch { settingsDataStore.setScanRescoreAfterHours(clamped) }
    }

    fun triggerScan() {
        viewModelScope.launch {
            val config = settingsDataStore.getFastAiConfig()
            if (config.apiKey.isBlank()) {
                _uiState.update { it.copy(error = "快速模型未配置，请先在设置中配置 API Key") }
                return@launch
            }
            backgroundTaskManager.enqueueOnlineArticleScanScore(
                force = true,
                rescoreAfterHours = _uiState.value.rescoreAfterHours
            )
        }
    }

    fun cancelScan() {
        val taskId = _uiState.value.scanTask?.id ?: return
        backgroundTaskManager.cancelTask(taskId)
    }

    fun pauseScan() {
        val taskId = _uiState.value.scanTask?.id ?: return
        backgroundTaskManager.pauseTask(taskId)
    }

    fun resumeScan() {
        val taskId = _uiState.value.scanTask?.id ?: return
        backgroundTaskManager.resumeTask(taskId)
    }

    fun deleteScanTask() {
        val taskId = _uiState.value.scanTask?.id ?: return
        backgroundTaskManager.deleteTask(taskId)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
