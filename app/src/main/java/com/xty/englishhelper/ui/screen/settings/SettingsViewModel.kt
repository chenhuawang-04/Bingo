package com.xty.englishhelper.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.usecase.ai.TestAiConnectionUseCase
import com.xty.englishhelper.domain.usecase.sync.ForceDownloadUseCase
import com.xty.englishhelper.domain.usecase.sync.ForceUploadUseCase
import com.xty.englishhelper.domain.usecase.sync.GetCloudManifestUseCase
import com.xty.englishhelper.domain.usecase.sync.SyncUseCase
import com.xty.englishhelper.domain.usecase.sync.TestSyncConnectionUseCase
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
    private val testAiConnection: TestAiConnectionUseCase,
    private val syncUseCase: SyncUseCase,
    private val forceUploadUseCase: ForceUploadUseCase,
    private val forceDownloadUseCase: ForceDownloadUseCase,
    private val testSyncConnectionUseCase: TestSyncConnectionUseCase,
    private val getCloudManifestUseCase: GetCloudManifestUseCase
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
        viewModelScope.launch {
            settingsDataStore.fastModel.collect { model ->
                _uiState.update { it.copy(fastModel = model) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.guardianDetailConcurrency.collect { value ->
                _uiState.update { it.copy(guardianDetailConcurrency = value) }
            }
        }
        // Scoped settings
        initScopedSettings(AiSettingsScope.POOL) { state, scoped -> state.copy(poolAiSettings = scoped) }
        initScopedSettings(AiSettingsScope.OCR) { state, scoped -> state.copy(ocrAiSettings = scoped) }

        // Cloud sync settings
        initCloudSync()
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
            AiSettingsScope.ARTICLE -> state.articleAiSettings
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

    fun onFastModelChange(model: String) {
        val provider = _uiState.value.provider
        _uiState.update { it.copy(fastModel = model) }
        viewModelScope.launch { settingsDataStore.setFastModel(provider, model) }
    }

    fun onGuardianDetailConcurrencyChange(value: Int) {
        _uiState.update { it.copy(guardianDetailConcurrency = value) }
        viewModelScope.launch { settingsDataStore.setGuardianDetailConcurrency(value) }
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
                AiSettingsScope.ARTICLE -> state.copy(articleAiSettings = scoped)
                AiSettingsScope.MAIN -> state
            }
        }
    }

    // ── Cloud Sync ──

    private fun initCloudSync() {
        viewModelScope.launch {
            settingsDataStore.githubOwner.collect { owner ->
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(githubOwner = owner)) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.githubRepo.collect { repo ->
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(githubRepo = repo)) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.lastSyncAt.collect { ts ->
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(lastSyncAt = ts)) }
            }
        }
        // Load PAT once
        _uiState.update { it.copy(cloudSync = it.cloudSync.copy(pat = settingsDataStore.getGitHubPat())) }
    }

    fun onGitHubOwnerChange(owner: String) {
        _uiState.update { it.copy(cloudSync = it.cloudSync.copy(githubOwner = owner)) }
        viewModelScope.launch { settingsDataStore.setGitHubOwner(owner) }
    }

    fun onGitHubRepoChange(repo: String) {
        _uiState.update { it.copy(cloudSync = it.cloudSync.copy(githubRepo = repo)) }
        viewModelScope.launch { settingsDataStore.setGitHubRepo(repo) }
    }

    fun onGitHubPatChange(pat: String) {
        _uiState.update { it.copy(cloudSync = it.cloudSync.copy(pat = pat)) }
        settingsDataStore.setGitHubPat(pat)
    }

    fun testSyncConnection() {
        val sync = _uiState.value.cloudSync
        if (sync.pat.isBlank() || sync.githubOwner.isBlank() || sync.githubRepo.isBlank()) {
            _uiState.update { it.copy(cloudSync = it.cloudSync.copy(connectionTestResult = "请填写完整配置")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSync = it.cloudSync.copy(connectionTestResult = null, error = null)) }
            try {
                val success = testSyncConnectionUseCase()
                val result = if (success) "连接成功" else "连接失败：仓库不存在或无权限"
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(connectionTestResult = result)) }
                if (success) {
                    try {
                        val manifest = getCloudManifestUseCase()
                        _uiState.update { it.copy(cloudSync = it.cloudSync.copy(cloudManifest = manifest)) }
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(connectionTestResult = "连接失败：${e.message}")) }
            }
        }
    }

    fun performSync() {
        if (_uiState.value.cloudSync.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = true, error = null, syncProgress = null)) }
            try {
                syncUseCase { progress ->
                    _uiState.update { it.copy(cloudSync = it.cloudSync.copy(syncProgress = progress)) }
                }
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = false, syncProgress = null)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = false, syncProgress = null, error = "同步失败：${e.message}")) }
            }
        }
    }

    fun performForceUpload() {
        if (_uiState.value.cloudSync.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = true, error = null, syncProgress = null)) }
            try {
                forceUploadUseCase { progress ->
                    _uiState.update { it.copy(cloudSync = it.cloudSync.copy(syncProgress = progress)) }
                }
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = false, syncProgress = null)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = false, syncProgress = null, error = "上传失败：${e.message}")) }
            }
        }
    }

    fun performForceDownload() {
        if (_uiState.value.cloudSync.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = true, error = null, syncProgress = null)) }
            try {
                forceDownloadUseCase { progress ->
                    _uiState.update { it.copy(cloudSync = it.cloudSync.copy(syncProgress = progress)) }
                }
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = false, syncProgress = null)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = false, syncProgress = null, error = "下载失败：${e.message}")) }
            }
        }
    }

    fun clearSyncError() {
        _uiState.update { it.copy(cloudSync = it.cloudSync.copy(error = null, connectionTestResult = null)) }
    }
}
