package com.xty.englishhelper.data.remote

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.remote.dto.AnthropicRequest
import com.xty.englishhelper.data.remote.dto.AnthropicResponse
import com.xty.englishhelper.data.remote.dto.AnthropicStreamEvent
import com.xty.englishhelper.data.remote.dto.MessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnthropicApiClient @Inject constructor(
    private val apiService: AnthropicApiService,
    moshi: Moshi
) : AiApiClient {

    private val responseAdapter = moshi.adapter(AnthropicResponse::class.java)
    private val streamEventAdapter = moshi.adapter(AnthropicStreamEvent::class.java)

    override suspend fun sendMessage(
        url: String,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): String {
        val fullUrl = buildUrl(url)
        val request = AnthropicRequest(
            model = model,
            maxTokens = maxTokens,
            system = systemPrompt,
            messages = messages.map { MessageDto(role = it.role, content = it.content) }
        )
        val response = apiService.createMessage(fullUrl, apiKey, request)
        return extractContent(response)
    }

    override suspend fun sendMultimodalMessage(
        url: String,
        apiKey: String,
        model: String,
        imageBytes: List<ByteArray>,
        prompt: String,
        maxTokens: Int
    ): String {
        val fullUrl = buildUrl(url)
        val requestBody = buildMultimodalRequestBody(model, imageBytes, prompt, maxTokens)
        val response = apiService.createMultimodalMessage(fullUrl, apiKey, requestBody)
        return extractContent(response)
    }

    /**
     * 读取原始响应体并自动判断普通 JSON 还是 SSE 流式：
     * - 普通 JSON：按 [AnthropicResponse] 取 content[0].text。
     * - 流式：聚合所有 content_block_delta / text_delta 事件的 text。
     * 非 2xx 抛携带状态码的异常，保持重试分类不变。
     */
    private suspend fun extractContent(response: Response<ResponseBody>): String =
        withContext(Dispatchers.IO) {
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "HTTP ${response.code()} ${response.message()} 调用 AI 失败"
                )
            }
            val body = response.body()
                ?: throw IllegalStateException("Empty response from Anthropic API")
            val contentType = body.contentType()?.toString()
            val raw = body.string()

            if (SseParser.looksLikeEventStream(contentType, raw)) {
                val text = buildString {
                    SseParser.dataPayloads(raw).forEach { payload ->
                        val event = runCatching { streamEventAdapter.fromJson(payload) }.getOrNull()
                        if (event?.type == "content_block_delta" && event.delta?.type == "text_delta") {
                            event.delta.text?.let { append(it) }
                        }
                    }
                }
                if (text.isEmpty()) {
                    throw IllegalStateException("Empty streamed response from Anthropic API")
                }
                text
            } else {
                val parsed = runCatching { responseAdapter.fromJson(raw) }.getOrNull()
                parsed?.content?.firstOrNull()?.text
                    ?: throw IllegalStateException("Empty response from Anthropic API")
            }
        }

    private fun buildUrl(baseUrl: String): String {
        var base = baseUrl.trim().trimEnd('/')
        // Ensure URL has a scheme - without it, Retrofit treats it as relative
        // and resolves against the hardcoded HTTPS base URL
        if (!base.startsWith("http://", ignoreCase = true) &&
            !base.startsWith("https://", ignoreCase = true)) {
            base = "http://$base"
        }
        return when {
            base.endsWith("/v1/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }
    }

    private fun buildMultimodalRequestBody(
        model: String,
        imageBytes: List<ByteArray>,
        prompt: String,
        maxTokens: Int
    ): okhttp3.RequestBody {
        val sb = StringBuilder()
        sb.append("""{"model":"$model","max_tokens":$maxTokens,"messages":[{"role":"user","content":[""")

        sb.append("""{"type":"text","text":""")
        sb.append("\"${escapeJson(prompt)}\"")
        sb.append("}")

        imageBytes.forEach { bytes ->
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val mimeType = detectImageMime(bytes)
            sb.append(""",{"type":"image","source":{"type":"base64","media_type":"$mimeType","data":""")
            sb.append("\"$base64\"")
            sb.append("}}")
        }

        sb.append("""]}]}""")
        return sb.toString().toRequestBody("application/json".toMediaType())
    }

    private fun escapeJson(raw: String): String {
        val out = StringBuilder(raw.length + 32)
        raw.forEach { ch ->
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun detectImageMime(bytes: ByteArray): String {
        if (bytes.size < 4) return "image/jpeg"
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes.size >= 12 && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "image/webp"
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
