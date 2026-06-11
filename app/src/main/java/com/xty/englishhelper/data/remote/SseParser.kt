package com.xty.englishhelper.data.remote

/**
 * 极简的 Server-Sent Events 解析工具。
 *
 * AI 服务端可能在我们未显式请求流式时也以 `text/event-stream` 返回（常见于各类
 * OpenAI 兼容网关 / 反代）。本工具用于「自动判断响应是否为流式」并把流式响应里
 * 逐块的 `data:` 负载抽出来，交由各 Client 按自身协议（OpenAI delta / Anthropic
 * content_block_delta）聚合成最终文本。
 *
 * 注意：这里只做「聚合成完整文本」的兼容，不做逐 token 的渐进式 UI 推送——上层所有
 * AI 调用都只关心最终整段文本（JSON 抽取类任务），故聚合即可满足需求。
 */
object SseParser {

    /** 依据 Content-Type 或响应体前缀判断是否为 SSE 流式响应。 */
    fun looksLikeEventStream(contentType: String?, body: String): Boolean {
        if (contentType?.contains("event-stream", ignoreCase = true) == true) return true
        val trimmed = body.trimStart()
        return trimmed.startsWith("data:") || trimmed.startsWith("event:")
    }

    /**
     * 抽出 SSE 响应体中每条 `data:` 行的 JSON 负载，跳过空行、注释、`event:` 行
     * 以及表示流结束的 `[DONE]` 哨兵。OpenAI / Anthropic 每个事件的 data 均为单行
     * JSON，故按行解析足够健壮。
     */
    fun dataPayloads(body: String): List<String> {
        return body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotEmpty() && it != "[DONE]" }
            .toList()
    }
}
