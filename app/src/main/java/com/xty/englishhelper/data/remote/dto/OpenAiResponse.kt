package com.xty.englishhelper.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenAiResponse(
    val id: String = "",
    val model: String = "",
    val choices: List<OpenAiChoice> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenAiChoice(
    val index: Int = 0,
    val message: OpenAiResponseMessage = OpenAiResponseMessage(),
    @Json(name = "finish_reason")
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiResponseMessage(
    val role: String = "",
    val content: String? = null
)
