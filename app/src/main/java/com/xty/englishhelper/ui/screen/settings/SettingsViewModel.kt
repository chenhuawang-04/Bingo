package com.xty.englishhelper.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.debug.AiDebugManager
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiProviderProfile
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.usecase.ai.FetchAiModelsUseCase
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
    private val ttsManager: TtsManager,
    private val aiDebugManager: AiDebugManager,
    private val testAiConnection: TestAiConnectionUseCase,
    private val fetchAiModels: FetchAiModelsUseCase,
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
            settingsDataStore.providersWithKeys.collect { list ->
                val summaries = list.map { summary ->
                    ProviderSummary(
                        name = summary.profile.name,
                        format = summary.profile.provider,
                        baseUrl = summary.profile.baseUrl,
                        hasApiKey = summary.hasApiKey
                    )
                }
                _uiState.update { it.copy(providers = summaries) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.defaultProviderName.collect { name ->
                _uiState.update { it.copy(defaultProviderName = name) }
            }
        }
        AiSettingsScope.values().forEach { scope ->
            viewModelScope.launch {
                settingsDataStore.scopeConfig(scope).collect { config ->
                    _uiState.update { state ->
                        state.copy(scopeConfigs = state.scopeConfigs + (scope to config))
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsDataStore.guardianDetailConcurrency.collect { value ->
                _uiState.update { it.copy(guardianDetailConcurrency = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.ttsRate.collect { value ->
                _uiState.update { it.copy(ttsRate = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.ttsPitch.collect { value ->
                _uiState.update { it.copy(ttsPitch = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.ttsLocale.collect { value ->
                _uiState.update { it.copy(ttsLocale = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.ttsAutoStudy.collect { value ->
                _uiState.update { it.copy(ttsAutoStudy = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.aiDebugMode.collect { value ->
                _uiState.update { it.copy(aiDebugMode = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.imageCompressionEnabled.collect { value ->
                _uiState.update { it.copy(imageCompressionEnabled = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.imageCompressionTargetBytes.collect { value ->
                _uiState.update { it.copy(imageCompressionTargetBytes = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.ttsPrewarmConcurrency.collect { value ->
                _uiState.update { it.copy(ttsPrewarmConcurrency = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.ttsPrewarmRetry.collect { value ->
                _uiState.update { it.copy(ttsPrewarmRetry = value) }
            }
        }
        initCloudSync()
    }

    fun startCreateProvider() {
        _uiState.update {
            it.copy(
                providerEditor = ProviderEditorState(
                    mode = ProviderEditorMode.CREATE,
                    name = "",
                    format = AiProvider.ANTHROPIC,
                    baseUrl = defaultBaseUrl(AiProvider.ANTHROPIC)
                )
            )
        }
    }

    fun startEditProvider(providerName: String) {
        viewModelScope.launch {
            val profile = settingsDataStore.getProvider(providerName) ?: return@launch
            val apiKey = settingsDataStore.getProviderApiKey(providerName)
            _uiState.update {
                it.copy(
                    providerEditor = ProviderEditorState(
                        mode = ProviderEditorMode.EDIT,
                        originalName = profile.name,
                        name = profile.name,
                        format = profile.provider,
                        baseUrl = profile.baseUrl,
                        apiKey = apiKey
                    )
                )
            }
        }
    }

    fun cancelProviderEditor() {
        _uiState.update { it.copy(providerEditor = ProviderEditorState()) }
    }

    fun onProviderNameChange(value: String) {
        _uiState.update { it.copy(providerEditor = it.providerEditor.copy(name = value)) }
    }

    fun onProviderFormatChange(format: AiProvider) {
        _uiState.update {
            it.copy(providerEditor = it.providerEditor.copy(format = format))
        }
    }

    fun onProviderBaseUrlChange(value: String) {
        _uiState.update { it.copy(providerEditor = it.providerEditor.copy(baseUrl = value)) }
    }

    fun onProviderApiKeyChange(value: String) {
        _uiState.update { it.copy(providerEditor = it.providerEditor.copy(apiKey = value)) }
    }

    fun saveProvider() {
        val editor = _uiState.value.providerEditor
        val rawName = editor.name.trim()
        if (rawName.isBlank()) {
            _uiState.update { it.copy(error = "请输入提供商名称") }
            return
        }
        if (editor.mode == ProviderEditorMode.CREATE) {
            val exists = _uiState.value.providers.any { it.name.equals(rawName, ignoreCase = true) }
            if (exists) {
                _uiState.update { it.copy(error = "提供商名称已存在") }
                return
            }
        }
        val finalName = editor.originalName ?: rawName
        val profile = AiProviderProfile(
            name = finalName,
            provider = editor.format,
            baseUrl = editor.baseUrl.trim()
        )

        viewModelScope.launch {
            if (editor.mode == ProviderEditorMode.CREATE) {
                settingsDataStore.addProvider(profile)
            } else if (editor.mode == ProviderEditorMode.EDIT) {
                settingsDataStore.updateProvider(profile)
            }
            settingsDataStore.setProviderApiKey(finalName, editor.apiKey)
            _uiState.update {
                it.copy(
                    providerEditor = ProviderEditorState(),
                    message = "已保存提供商配置"
                )
            }
        }
    }

    fun setDefaultProvider(providerName: String) {
        viewModelScope.launch {
            settingsDataStore.setDefaultProvider(providerName)
            _uiState.update { it.copy(message = "已设置默认提供商") }
        }
    }

    fun requestDeleteProvider(providerName: String) {
        viewModelScope.launch {
            val scopes = settingsDataStore.getScopesUsingProvider(providerName)
            _uiState.update { it.copy(pendingDelete = PendingDeleteProvider(providerName, scopes)) }
        }
    }

    fun confirmDeleteProvider() {
        val pending = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            val removed = settingsDataStore.deleteProvider(pending.name)
            if (removed) {
                _uiState.update { it.copy(pendingDelete = null, message = "已删除提供商") }
            } else {
                _uiState.update {
                    it.copy(
                        pendingDelete = null,
                        error = "删除失败，提供商不存在或至少保留一个提供商"
                    )
                }
            }
        }
    }

    fun dismissDeleteProvider() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    fun testProviderConnection() {
        val editor = _uiState.value.providerEditor
        if (editor.apiKey.isBlank()) {
            _uiState.update { it.copy(error = "请先输入 API Key") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(providerEditor = editor.copy(isTesting = true, testResult = null)) }
            try {
                val model = defaultModelFor(editor.format)
                val baseUrl = editor.baseUrl.ifBlank { defaultBaseUrl(editor.format) }
                val success = testAiConnection(editor.apiKey, model, baseUrl, editor.format)
                val result = if (success) "连接成功" else "连接失败"
                _uiState.update {
                    it.copy(providerEditor = it.providerEditor.copy(isTesting = false, testResult = result))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(providerEditor = it.providerEditor.copy(isTesting = false, testResult = "连接失败: ${e.message}"))
                }
            }
        }
    }

    fun fetchModelsForEditor() {
        val editor = _uiState.value.providerEditor
        if (editor.name.isBlank()) {
            _uiState.update { it.copy(error = "请先填写提供商名称") }
            return
        }
        fetchModels(editor.name.trim(), editor.format, editor.baseUrl, editor.apiKey)
    }

    fun fetchModelsForProvider(providerName: String) {
        val provider = _uiState.value.providers.firstOrNull { it.name == providerName }
        if (provider == null) {
            _uiState.update { it.copy(error = "提供商不存在") }
            return
        }
        fetchModels(providerName, provider.format, provider.baseUrl, null)
    }

    private fun fetchModels(providerName: String, format: AiProvider, baseUrl: String, apiKeyOverride: String?) {
        viewModelScope.launch {
            val apiKey = apiKeyOverride ?: settingsDataStore.getProviderApiKey(providerName)
            if (apiKey.isBlank()) {
                _uiState.update {
                    it.copy(modelError = it.modelError + (providerName to "请先配置 API Key"))
                }
                return@launch
            }
            val effectiveBaseUrl = baseUrl.ifBlank { defaultBaseUrl(format) }
            _uiState.update {
                it.copy(
                    modelLoading = it.modelLoading + providerName,
                    modelError = it.modelError - providerName
                )
            }
            try {
                val models = fetchAiModels(apiKey, format, effectiveBaseUrl)
                _uiState.update {
                    it.copy(
                        modelOptions = it.modelOptions + (providerName to models),
                        modelLoading = it.modelLoading - providerName
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        modelLoading = it.modelLoading - providerName,
                        modelError = it.modelError + (providerName to (e.message ?: "拉取失败"))
                    )
                }
            }
        }
    }

    fun onScopeProviderChange(scope: AiSettingsScope, providerName: String) {
        val provider = _uiState.value.providers.firstOrNull { it.name == providerName }
        if (provider == null) {
            _uiState.update { it.copy(error = "提供商不存在") }
            return
        }
        viewModelScope.launch {
            settingsDataStore.setScopeConfig(scope, providerName, defaultModelFor(provider.format))
        }
    }

    fun onScopeModelChange(scope: AiSettingsScope, model: String) {
        val providerName = _uiState.value.scopeConfigs[scope]?.providerName
            ?: _uiState.value.defaultProviderName
        if (providerName.isBlank()) {
            _uiState.update { it.copy(error = "请先选择提供商") }
            return
        }
        viewModelScope.launch {
            settingsDataStore.setScopeConfig(scope, providerName, model)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    fun onGuardianDetailConcurrencyChange(value: Int) {
        _uiState.update { it.copy(guardianDetailConcurrency = value) }
        viewModelScope.launch { settingsDataStore.setGuardianDetailConcurrency(value) }
    }

    fun onTtsRateChange(value: Float) {
        val clamped = value.coerceIn(0.5f, 2.0f)
        _uiState.update { it.copy(ttsRate = clamped) }
        viewModelScope.launch { settingsDataStore.setTtsRate(clamped) }
    }

    fun onTtsPitchChange(value: Float) {
        val clamped = value.coerceIn(0.5f, 2.0f)
        _uiState.update { it.copy(ttsPitch = clamped) }
        viewModelScope.launch { settingsDataStore.setTtsPitch(clamped) }
    }

    fun onTtsLocaleChange(value: String) {
        _uiState.update { it.copy(ttsLocale = value) }
        viewModelScope.launch { settingsDataStore.setTtsLocale(value) }
    }

    fun onTtsAutoStudyChange(value: Boolean) {
        _uiState.update { it.copy(ttsAutoStudy = value) }
        viewModelScope.launch { settingsDataStore.setTtsAutoStudy(value) }
    }

    fun onAiDebugModeChange(value: Boolean) {
        _uiState.update { it.copy(aiDebugMode = value) }
        aiDebugManager.setEnabled(value)
        viewModelScope.launch { settingsDataStore.setAiDebugMode(value) }
    }

    fun onImageCompressionEnabledChange(value: Boolean) {
        _uiState.update { it.copy(imageCompressionEnabled = value) }
        viewModelScope.launch { settingsDataStore.setImageCompressionEnabled(value) }
    }

    fun onImageCompressionTargetBytesChange(value: Int) {
        _uiState.update { it.copy(imageCompressionTargetBytes = value) }
        viewModelScope.launch { settingsDataStore.setImageCompressionTargetBytes(value) }
    }

    fun onTtsPrewarmConcurrencyChange(value: Int) {
        val clamped = value.coerceIn(1, 6)
        _uiState.update { it.copy(ttsPrewarmConcurrency = clamped) }
        viewModelScope.launch { settingsDataStore.setTtsPrewarmConcurrency(clamped) }
    }

    fun onTtsPrewarmRetryChange(value: Int) {
        val clamped = value.coerceIn(0, 5)
        _uiState.update { it.copy(ttsPrewarmRetry = clamped) }
        viewModelScope.launch { settingsDataStore.setTtsPrewarmRetry(clamped) }
    }

    fun playTtsSample() {
        viewModelScope.launch {
            ttsManager.speakWord(
                wordId = 0L,
                text = "This is a sample of English speech."
            )
        }
    }

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
                val result = if (success) "连接成功" else "连接失败: 仓库不存在或无权限"
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(connectionTestResult = result)) }
                if (success) {
                    try {
                        val manifest = getCloudManifestUseCase()
                        _uiState.update { it.copy(cloudSync = it.cloudSync.copy(cloudManifest = manifest)) }
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(connectionTestResult = "连接失败: ${e.message}")) }
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
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = false, syncProgress = null, error = "同步失败: ${e.message}")) }
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
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = false, syncProgress = null, error = "上传失败: ${e.message}")) }
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
                _uiState.update { it.copy(cloudSync = it.cloudSync.copy(isSyncing = false, syncProgress = null, error = "下载失败: ${e.message}")) }
            }
        }
    }

    fun clearSyncError() {
        _uiState.update { it.copy(cloudSync = it.cloudSync.copy(error = null, connectionTestResult = null)) }
    }

    private fun defaultModelFor(provider: AiProvider): String {
        return when (provider) {
            AiProvider.ANTHROPIC -> Constants.DEFAULT_MODEL
            AiProvider.OPENAI_COMPATIBLE -> Constants.DEFAULT_OPENAI_MODEL
        }
    }

    private fun defaultBaseUrl(provider: AiProvider): String {
        return when (provider) {
            AiProvider.ANTHROPIC -> Constants.ANTHROPIC_BASE_URL
            AiProvider.OPENAI_COMPATIBLE -> Constants.OPENAI_BASE_URL
        }
    }
}
