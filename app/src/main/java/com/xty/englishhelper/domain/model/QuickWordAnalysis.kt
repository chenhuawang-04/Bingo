package com.xty.englishhelper.domain.model

data class QuickWordAnalysis(
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val contextMeaning: String = "",
    val commonMeanings: List<String> = emptyList(),
    val examImportance: String = ""
)
