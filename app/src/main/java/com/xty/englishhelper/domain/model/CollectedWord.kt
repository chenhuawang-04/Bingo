package com.xty.englishhelper.domain.model

data class CollectedWord(
    val word: String,
    val contextSentence: String,
    val analysis: QuickWordAnalysis? = null,
    val isAnalyzing: Boolean = false
)
