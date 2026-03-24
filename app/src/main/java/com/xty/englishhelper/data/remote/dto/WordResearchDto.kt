package com.xty.englishhelper.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WordResearchResponseDto(
    val hasUsefulReference: Boolean = false,
    val examFocusSummary: String = "",
    val confusionWords: List<WordResearchItemDto> = emptyList(),
    val synonymWords: List<WordResearchItemDto> = emptyList(),
    val similarWords: List<WordResearchItemDto> = emptyList(),
    val cognateWords: List<WordResearchItemDto> = emptyList(),
    val webFindings: List<String> = emptyList(),
    val confidence: Float = 0f
)

@JsonClass(generateAdapter = true)
data class WordResearchItemDto(
    val word: String = "",
    val note: String = "",
    val examImportance: String = ""
)
