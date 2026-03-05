package com.xty.englishhelper.ui.screen.settings

import com.xty.englishhelper.data.json.SyncManifest
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.repository.SyncProgress

data class SettingsUiState(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val apiKey: String = "",
    val baseUrl: String = "",
    val selectedModel: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val error: String? = null,
    val poolAiSettings: ScopedAiSettingsState = ScopedAiSettingsState(),
    val ocrAiSettings: ScopedAiSettingsState = ScopedAiSettingsState(),
    val cloudSync: CloudSyncState = CloudSyncState()
)

data class ScopedAiSettingsState(
    val enabled: Boolean = false,
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val apiKey: String = "",
    val baseUrl: String = "",
    val selectedModel: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null
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
