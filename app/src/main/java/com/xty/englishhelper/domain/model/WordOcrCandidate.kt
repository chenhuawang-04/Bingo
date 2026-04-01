package com.xty.englishhelper.domain.model

data class WordOcrCandidate(
    val spelling: String,
    val references: List<String> = emptyList()
)

