package com.xty.englishhelper.ui.screen.settings

import com.xty.englishhelper.domain.model.AiProvider

data class SettingsUiState(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val apiKey: String = "",
    val baseUrl: String = "",
    val selectedModel: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val error: String? = null
)
