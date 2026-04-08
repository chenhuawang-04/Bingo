package com.xty.englishhelper.domain.model

enum class QuickDictionarySource {
    CAMBRIDGE,
    OED
}

data class QuickDictionaryEntry(
    val source: QuickDictionarySource,
    val sourceLabel: String,
    val headword: String,
    val variant: String,
    val pronunciation: String?,
    val timeRange: String? = null,
    val tags: List<String> = emptyList(),
    val etymologySummary: String? = null,
    val summary: String,
    val senses: List<String>,
    val sourceUrl: String
)
