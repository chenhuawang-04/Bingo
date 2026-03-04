package com.xty.englishhelper.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiSettingsScope
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
        // Scoped settings
        initScopedSettings(AiSettingsScope.POOL) { state, scoped -> state.copy(poolAiSettings = scoped) }
        initScopedSettings(AiSettingsScope.OCR) { state, scoped -> state.copy(ocrAiSettings = scoped) }
    }

    private fun initScopedSettings(
        scope: AiSettingsScope,
        updater: (SettingsUiState, ScopedAiSettingsState) -> SettingsUiState
    ) {
        viewModelScope.launch {
            settingsDataStore.scopedProvider(scope).collect { provider ->
                _uiState.update { state ->
                    val scoped = getScopedState(state, scope)
                    updater(state, scoped.copy(enabled = provider != null, provider = provider ?: AiProvider.ANTHROPIC))
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.scopedApiKey(scope).collect { key ->
                _uiState.update { state ->
                    val scoped = getScopedState(state, scope)
                    updater(state, scoped.copy(apiKey = key))
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.scopedModel(scope).collect { model ->
                _uiState.update { state ->
                    val scoped = getScopedState(state, scope)
                    updater(state, scoped.copy(selectedModel = model))
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.scopedBaseUrl(scope).collect { url ->
                _uiState.update { state ->
                    val scoped = getScopedState(state, scope)
                    updater(state, scoped.copy(baseUrl = url))
                }
            }
        }
    }

    private fun getScopedState(state: SettingsUiState, scope: AiSettingsScope): ScopedAiSettingsState {
        return when (scope) {
            AiSettingsScope.POOL -> state.poolAiSettings
            AiSettingsScope.OCR -> state.ocrAiSettings
            AiSettingsScope.MAIN -> ScopedAiSettingsState()
        }
    }

    // ── Main settings ──

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

    // ── Scoped settings ──

    fun onScopedToggle(scope: AiSettingsScope, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // Initialize with MAIN's provider
                val mainProvider = _uiState.value.provider
                settingsDataStore.setScopedProvider(scope, mainProvider)
            } else {
                settingsDataStore.clearScopedSettings(scope)
            }
        }
    }

    fun onScopedProviderChange(scope: AiSettingsScope, provider: AiProvider) {
        // Update local state immediately to prevent race with subsequent key/model writes
        val scoped = getScopedState(_uiState.value, scope)
        updateScopedState(scope, scoped.copy(provider = provider))
        viewModelScope.launch {
            settingsDataStore.setScopedProvider(scope, provider)
        }
    }

    fun onScopedApiKeyChange(scope: AiSettingsScope, key: String) {
        val scoped = getScopedState(_uiState.value, scope)
        updateScopedState(scope, scoped.copy(apiKey = key))
        viewModelScope.launch {
            // Re-read from latest state to use correct provider
            val currentProvider = getScopedState(_uiState.value, scope).provider
            settingsDataStore.setScopedApiKey(scope, currentProvider, key)
        }
    }

    fun onScopedBaseUrlChange(scope: AiSettingsScope, url: String) {
        val scoped = getScopedState(_uiState.value, scope)
        updateScopedState(scope, scoped.copy(baseUrl = url))
        viewModelScope.launch {
            val currentProvider = getScopedState(_uiState.value, scope).provider
            settingsDataStore.setScopedBaseUrl(scope, currentProvider, url)
        }
    }

    fun onScopedModelChange(scope: AiSettingsScope, model: String) {
        val scoped = getScopedState(_uiState.value, scope)
        updateScopedState(scope, scoped.copy(selectedModel = model))
        viewModelScope.launch {
            val currentProvider = getScopedState(_uiState.value, scope).provider
            settingsDataStore.setScopedModel(scope, currentProvider, model)
        }
    }

    fun testScopedConnection(scope: AiSettingsScope) {
        val scoped = getScopedState(_uiState.value, scope)
        if (scoped.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请先输入 API Key") }
            return
        }

        viewModelScope.launch {
            updateScopedState(scope, scoped.copy(isTesting = true, testResult = null))
            try {
                val model = scoped.selectedModel.ifBlank {
                    when (scoped.provider) {
                        AiProvider.ANTHROPIC -> Constants.DEFAULT_MODEL
                        AiProvider.OPENAI_COMPATIBLE -> Constants.DEFAULT_OPENAI_MODEL
                    }
                }
                val baseUrl = scoped.baseUrl.ifBlank {
                    when (scoped.provider) {
                        AiProvider.ANTHROPIC -> Constants.ANTHROPIC_BASE_URL
                        AiProvider.OPENAI_COMPATIBLE -> Constants.OPENAI_BASE_URL
                    }
                }
                val success = testAiConnection(scoped.apiKey, model, baseUrl, scoped.provider)
                val result = if (success) "连接成功！" else "连接失败"
                updateScopedState(scope, getScopedState(_uiState.value, scope).copy(isTesting = false, testResult = result))
            } catch (e: Exception) {
                updateScopedState(scope, getScopedState(_uiState.value, scope).copy(isTesting = false, testResult = "连接失败：${e.message}"))
            }
        }
    }

    private fun updateScopedState(scope: AiSettingsScope, scoped: ScopedAiSettingsState) {
        _uiState.update { state ->
            when (scope) {
                AiSettingsScope.POOL -> state.copy(poolAiSettings = scoped)
                AiSettingsScope.OCR -> state.copy(ocrAiSettings = scoped)
                AiSettingsScope.MAIN -> state
            }
        }
    }
}
