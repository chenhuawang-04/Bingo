package com.xty.englishhelper.ui.screen.settings

import com.xty.englishhelper.data.json.SyncManifest
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.repository.SyncProgress

data class SettingsUiState(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val apiKey: String = "",
    val baseUrl: String = "",
    val selectedModel: String = "",
    val fastModel: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val error: String? = null,
    val poolAiSettings: ScopedAiSettingsState = ScopedAiSettingsState(),
    val ocrAiSettings: ScopedAiSettingsState = ScopedAiSettingsState(),
    val articleAiSettings: ScopedAiSettingsState = ScopedAiSettingsState(),
    val searchAiSettings: ScopedAiSettingsState = ScopedAiSettingsState(),
    val guardianDetailConcurrency: Int = 5,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsLocale: String = "system",
    val ttsAutoStudy: Boolean = true,
    val ttsPrewarmConcurrency: Int = 2,
    val ttsPrewarmRetry: Int = 2,
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
