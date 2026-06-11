package com.xty.englishhelper.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * OpenAI 兼容接口在流式（stream=true 或网关强制流式）下逐块返回的增量片段：
 * `data: {"choices":[{"delta":{"content":"..."}}]}`。
 */
@JsonClass(generateAdapter = true)
data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenAiStreamChoice(
    val index: Int = 0,
    val delta: OpenAiStreamDelta = OpenAiStreamDelta(),
    @Json(name = "finish_reason")
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiStreamDelta(
    val role: String? = null,
    val content: String? = null
)
