package com.xty.englishhelper.ui.screen.settings

import com.xty.englishhelper.data.json.SyncManifest
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiScopeConfig
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.OnlineReadingSource
import com.xty.englishhelper.domain.repository.SyncProgress

data class SettingsUiState(
    val providers: List<ProviderSummary> = emptyList(),
    val defaultProviderName: String = "",
    val scopeConfigs: Map<AiSettingsScope, AiScopeConfig> = emptyMap(),
    val providerEditor: ProviderEditorState = ProviderEditorState(),
    val pendingDelete: PendingDeleteProvider? = null,
    val modelOptions: Map<String, List<String>> = emptyMap(),
    val modelLoading: Set<String> = emptySet(),
    val modelError: Map<String, String> = emptyMap(),
    val message: String? = null,
    val error: String? = null,
    val guardianDetailConcurrency: Int = 5,
    val onlineReadingSource: OnlineReadingSource = OnlineReadingSource.GUARDIAN,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsLocale: String = "system",
    val ttsAutoStudy: Boolean = true,
    val aiDebugMode: Boolean = false,
    val aiResponseUnwrapEnabled: Boolean = false,
    val aiJsonRepairEnabled: Boolean = false,
    val imageCompressionEnabled: Boolean = true,
    val imageCompressionTargetBytes: Int = 1_000_000,
    val ttsPrewarmConcurrency: Int = 2,
    val ttsPrewarmRetry: Int = 2,
    val cloudSync: CloudSyncState = CloudSyncState()
)

data class ProviderSummary(
    val name: String,
    val format: AiProvider,
    val baseUrl: String,
    val hasApiKey: Boolean
)

enum class ProviderEditorMode { NONE, CREATE, EDIT }

data class ProviderEditorState(
    val mode: ProviderEditorMode = ProviderEditorMode.NONE,
    val originalName: String? = null,
    val name: String = "",
    val format: AiProvider = AiProvider.ANTHROPIC,
    val baseUrl: String = "",
    val apiKey: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null
)

data class PendingDeleteProvider(
    val name: String,
    val affectedScopes: List<AiSettingsScope>
)

data class CloudSyncState(
    val githubOwner: String = "",
    val githubRepo: String = "",
    val pat: String = "",
    val isSyncing: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val lastSyncAt: Long = 0,
    val cloudManifest: SyncManifest? = null,
    val error: String? = null,
    val connectionTestResult: String? = null
)
