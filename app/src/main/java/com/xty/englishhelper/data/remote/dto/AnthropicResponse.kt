package com.xty.englishhelper.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AnthropicResponse(
    val id: String = "",
    val type: String = "",
    val role: String = "",
    val content: List<ContentBlock> = emptyList(),
    val model: String = "",
    @Json(name = "stop_reason")
    val stopReason: String? = null
)

@JsonClass(generateAdapter = true)
data class ContentBlock(
    val type: String = "",
    val text: String = ""
)

// AI response JSON model for word analysis
@JsonClass(generateAdapter = true)
data class AiWordAnalysis(
    val phonetic: String = "",
    val meanings: List<AiMeaning> = emptyList(),
    val rootExplanation: String = "",
    val synonyms: List<AiSynonym> = emptyList(),
    val similarWords: List<AiSimilarWord> = emptyList(),
    val cognates: List<AiCognate> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AiMeaning(
    val pos: String = "",
    val definition: String = ""
)

@JsonClass(generateAdapter = true)
data class AiSynonym(
    val word: String = "",
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class AiSimilarWord(
    val word: String = "",
    val meaning: String = "",
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class AiCognate(
    val word: String = "",
    val meaning: String = "",
    val sharedRoot: String = ""
)
