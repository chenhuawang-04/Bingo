package com.xty.englishhelper.ui.screen.settings

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = "",
    val selectedModel: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val error: String? = null
)
