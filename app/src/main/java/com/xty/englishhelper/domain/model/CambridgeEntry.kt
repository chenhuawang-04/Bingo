package com.xty.englishhelper.domain.model

data class CambridgeEntry(
    val headword: String,
    val partOfSpeech: String,
    val pronunciation: String?,
    val senses: List<CambridgeSense>,
    val sourceUrl: String
)

data class CambridgeSense(
    val definition: String,
    val translation: String?
)
