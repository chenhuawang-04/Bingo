package com.xty.englishhelper.domain.model

enum class WordReferenceSource {
    FAST,
    SEARCH
}

data class WordResearchItem(
    val word: String = "",
    val note: String = "",
    val examImportance: String = ""
)

data class WordResearchReference(
    val hasUsefulReference: Boolean = false,
    val examFocusSummary: String = "",
    val confusionWords: List<WordResearchItem> = emptyList(),
    val synonymWords: List<WordResearchItem> = emptyList(),
    val similarWords: List<WordResearchItem> = emptyList(),
    val cognateWords: List<WordResearchItem> = emptyList(),
    val webFindings: List<String> = emptyList(),
    val confidence: Float = 0f
)

data class WordOrganizeProgress(
    val current: Int,
    val total: Int,
    val label: String
)
