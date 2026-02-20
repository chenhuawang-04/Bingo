package com.xty.englishhelper.domain.model

data class WordDetails(
    val id: Long = 0,
    val dictionaryId: Long,
    val spelling: String,
    val phonetic: String = "",
    val meanings: List<Meaning> = emptyList(),
    val rootExplanation: String = "",
    val normalizedSpelling: String = "",
    val wordUid: String = "",
    val synonyms: List<SynonymInfo> = emptyList(),
    val similarWords: List<SimilarWordInfo> = emptyList(),
    val cognates: List<CognateInfo> = emptyList(),
    val decomposition: List<DecompositionPart> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SynonymInfo(
    val id: Long = 0,
    val word: String,
    val explanation: String = ""
)

data class SimilarWordInfo(
    val id: Long = 0,
    val word: String,
    val meaning: String = "",
    val explanation: String = ""
)

data class CognateInfo(
    val id: Long = 0,
    val word: String,
    val meaning: String = "",
    val sharedRoot: String = ""
)
