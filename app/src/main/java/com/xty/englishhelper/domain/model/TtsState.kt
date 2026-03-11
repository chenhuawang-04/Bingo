package com.xty.englishhelper.domain.model

enum class TtsMode {
    WORD,
    ARTICLE
}

data class TtsState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val mode: TtsMode? = null,
    val sessionId: String? = null,
    val currentIndex: Int = 0,
    val total: Int = 0,
    val error: String? = null
)
