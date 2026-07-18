package com.xty.englishhelper.domain.model

data class DictionaryPoolBackup(
    val strategies: List<WordPoolStrategyBackup> = emptyList(),
    val edges: List<WordEdgeBackup> = emptyList()
)

data class WordPoolStrategyBackup(
    val strategy: String,
    val updatedAt: Long,
    val pools: List<WordPoolBackup> = emptyList()
)

data class WordPoolBackup(
    val focusWordUid: String? = null,
    val memberWordUids: List<String>,
    val algorithmVersion: String,
    val updatedAt: Long,
    val qualityScore: Int? = null
)

data class WordEdgeBackup(
    val wordUidA: String,
    val wordUidB: String,
    val edgeType: String,
    val status: String,
    val learningValue: Int,
    val relationStrength: Int,
    val confidence: Double,
    val reason: String? = null,
    val warningNote: String? = null,
    val evidenceSource: String? = null,
    val register: String? = null,
    val exampleSentence: String? = null,
    val difficultyCefr: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
