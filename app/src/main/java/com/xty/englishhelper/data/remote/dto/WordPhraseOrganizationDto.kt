package com.xty.englishhelper.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WordPhraseOrganizationResponseDto(
    val phrases: List<WordPhraseItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WordPhraseItemDto(
    val phrase: String = "",
    val meaning: String = "",
    val example: String = "",
    val usageNote: String = "",
    val register: String? = null,
    val difficulty: String? = null,
    val confidence: Float = 0.8f,
    val tags: List<WordPhraseTagDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WordPhraseTagDto(
    val name: String = "",
    val description: String = ""
)
