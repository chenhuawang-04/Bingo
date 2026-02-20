package com.xty.englishhelper.domain.model

data class AiOrganizeResult(
    val phonetic: String = "",
    val meanings: List<Meaning> = emptyList(),
    val rootExplanation: String = "",
    val synonyms: List<SynonymInfo> = emptyList(),
    val similarWords: List<SimilarWordInfo> = emptyList(),
    val cognates: List<CognateInfo> = emptyList()
)
