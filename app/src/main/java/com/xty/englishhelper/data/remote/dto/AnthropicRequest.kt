package com.xty.englishhelper.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AnthropicRequest(
    val model: String,
    @Json(name = "max_tokens")
    val maxTokens: Int = 2048,
    val system: String? = null,
    val messages: List<MessageDto>
)

@JsonClass(generateAdapter = true)
data class MessageDto(
    val role: String,
    val content: String
)
