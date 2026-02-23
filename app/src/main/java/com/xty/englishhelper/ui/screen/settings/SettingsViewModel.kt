package com.xty.englishhelper.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.usecase.ai.TestAiConnectionUseCase
import com.xty.englishhelper.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val testAiConnection: TestAiConnectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.provider.collect { provider ->
                _uiState.update { it.copy(provider = provider) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.apiKey.collect { key ->
                _uiState.update { it.copy(apiKey = key) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.baseUrl.collect { url ->
                _uiState.update { it.copy(baseUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.model.collect { model ->
                _uiState.update { it.copy(selectedModel = model) }
            }
        }
    }

    fun onProviderChange(provider: AiProvider) {
        if (provider == _uiState.value.provider) return
        _uiState.update { it.copy(provider = provider) }
        viewModelScope.launch {
            settingsDataStore.setProvider(provider)
        }
    }

    fun onApiKeyChange(key: String) {
        val provider = _uiState.value.provider
        _uiState.update { it.copy(apiKey = key) }
        viewModelScope.launch { settingsDataStore.setApiKey(provider, key) }
    }

    fun onBaseUrlChange(url: String) {
        val provider = _uiState.value.provider
        _uiState.update { it.copy(baseUrl = url) }
        viewModelScope.launch { settingsDataStore.setBaseUrl(provider, url) }
    }

    fun onModelChange(model: String) {
        val provider = _uiState.value.provider
        _uiState.update { it.copy(selectedModel = model) }
        viewModelScope.launch { settingsDataStore.setModel(provider, model) }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请先输入 API Key") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null, error = null) }
            try {
                val model = state.selectedModel.ifBlank {
                    when (state.provider) {
                        AiProvider.ANTHROPIC -> Constants.DEFAULT_MODEL
                        AiProvider.OPENAI_COMPATIBLE -> Constants.DEFAULT_OPENAI_MODEL
                    }
                }
                val baseUrl = state.baseUrl.ifBlank {
                    when (state.provider) {
                        AiProvider.ANTHROPIC -> Constants.ANTHROPIC_BASE_URL
                        AiProvider.OPENAI_COMPATIBLE -> Constants.OPENAI_BASE_URL
                    }
                }
                val success = testAiConnection(state.apiKey, model, baseUrl, state.provider)
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = if (success) "连接成功！" else "连接失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isTesting = false, error = "连接失败：${e.message}")
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(testResult = null, error = null) }
    }
}
