package com.xty.englishhelper.domain.model

data class SentenceAnalysisResult(
    val meaningZh: String = "",
    val grammarPoints: List<GrammarPoint> = emptyList(),
    val keyVocabulary: List<KeyWord> = emptyList()
)

data class GrammarPoint(
    val title: String = "",
    val explanation: String = ""
)

data class KeyWord(
    val word: String = "",
    val meaning: String = ""
)

data class ArticleOcrResult(
    val title: String = "",
    val content: String = "",
    val domain: String = "",
    val difficulty: Float = 0f,
    val confidence: Float = 0f
)
