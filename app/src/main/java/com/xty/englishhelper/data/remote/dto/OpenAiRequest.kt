package com.xty.englishhelper.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenAiRequest(
    val model: String,
    @Json(name = "max_tokens")
    val maxTokens: Int,
    val messages: List<OpenAiMessageDto>
)

@JsonClass(generateAdapter = true)
data class OpenAiMessageDto(
    val role: String,
    val content: Any // String or List<OpenAiContentPart>
)

@JsonClass(generateAdapter = true)
data class OpenAiContentPart(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url")
    val imageUrl: OpenAiImageUrl? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiImageUrl(
    val url: String
)
