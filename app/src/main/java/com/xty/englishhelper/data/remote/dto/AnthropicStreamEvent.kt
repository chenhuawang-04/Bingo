package com.xty.englishhelper.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Anthropic Messages 流式事件。文本增量出现在
 * `event: content_block_delta` / `data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}`。
 * 其余事件（message_start / content_block_start / ping / message_stop 等）无文本，忽略即可。
 */
@JsonClass(generateAdapter = true)
data class AnthropicStreamEvent(
    val type: String = "",
    val delta: AnthropicStreamDelta? = null
)

@JsonClass(generateAdapter = true)
data class AnthropicStreamDelta(
    val type: String = "",
    val text: String? = null
)
