package com.xty.englishhelper.domain.model

import kotlinx.serialization.Serializable

data class ParagraphAnalysisResult(
    val meaningZh: String = "",
    val grammarPoints: List<GrammarPoint> = emptyList(),
    val keyVocabulary: List<KeyWord> = emptyList(),
    val sentenceBreakdowns: List<SentenceBreakdown> = emptyList()
)

@Serializable
data class SentenceBreakdown(
    val sentence: String = "",
    val translation: String = "",
    val grammarNotes: String = ""
)
