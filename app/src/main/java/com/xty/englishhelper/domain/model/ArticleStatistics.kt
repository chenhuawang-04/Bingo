package com.xty.englishhelper.domain.model

data class ArticleStatistics(
    val wordCount: Int = 0,
    val sentenceCount: Int = 0,
    val charCount: Int = 0,
    val uniqueWordCount: Int = 0,
    val topFrequencies: List<Pair<String, Int>> = emptyList(),
    val difficultyFinal: Float = 0f
)
