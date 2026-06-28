package com.xty.englishhelper.data.remote

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.remote.dto.OpenAiContentPart
import com.xty.englishhelper.data.remote.dto.OpenAiImageUrl
import com.xty.englishhelper.data.remote.dto.OpenAiMessageDto
import com.xty.englishhelper.data.remote.dto.OpenAiRequest
import com.xty.englishhelper.data.remote.dto.OpenAiResponse
import com.xty.englishhelper.data.remote.dto.OpenAiStreamChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiCompatibleApiClient @Inject constructor(
    private val apiService: OpenAiApiService,
    moshi: Moshi
) : AiApiClient {

    private val responseAdapter = moshi.adapter(OpenAiResponse::class.java)
    private val streamChunkAdapter = moshi.adapter(OpenAiStreamChunk::class.java)

    override suspend fun sendMessage(
        url: String,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): String {
        val fullUrl = buildUrl(url)
        val allMessages = mutableListOf<OpenAiMessageDto>()

        if (!systemPrompt.isNullOrBlank()) {
            allMessages.add(OpenAiMessageDto(role = "system", content = systemPrompt))
        }
        messages.forEach {
            allMessages.add(OpenAiMessageDto(role = it.role, content = it.content))
        }

        val request = OpenAiRequest(
            model = model,
            maxTokens = maxTokens,
            messages = allMessages
        )
        val response = apiService.createChatCompletion(fullUrl, "Bearer $apiKey", request)
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
        val contentParts = mutableListOf<OpenAiContentPart>()

        contentParts.add(OpenAiContentPart(type = "text", text = prompt))

        imageBytes.forEach { bytes ->
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val mimeType = detectImageMime(bytes)
            contentParts.add(
                OpenAiContentPart(
                    type = "image_url",
                    imageUrl = OpenAiImageUrl(url = "data:$mimeType;base64,$base64")
                )
            )
        }

        val allMessages = listOf(
            OpenAiMessageDto(role = "user", content = contentParts)
        )

        val request = OpenAiRequest(
            model = model,
            maxTokens = maxTokens,
            messages = allMessages
        )
        val response = apiService.createChatCompletion(fullUrl, "Bearer $apiKey", request)
        return extractContent(response)
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
            base.endsWith("/v1/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    /**
     * 读取原始响应体并自动判断是普通 JSON 还是 SSE 流式响应：
     * - 普通 JSON：按 [OpenAiResponse] 取 choices[0].message.content。
     * - 流式（text/event-stream 或体以 data:/event: 开头）：把每个 chunk 的
     *   choices[0].delta.content 顺序拼接成完整文本。
     * 非 2xx 时抛出携带状态码的异常，保持 [com.xty.englishhelper.data.repository.pool.AiErrorUtils]
     * 基于消息中状态码的重试分类不变。
     */
    private suspend fun extractContent(response: Response<ResponseBody>): String =
        withContext(Dispatchers.IO) {
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "HTTP ${response.code()} ${response.message()} 调用 AI 失败"
                )
            }
            val body = response.body()
                ?: throw IllegalStateException("Empty response from OpenAI-compatible API")
            val contentType = body.contentType()?.toString()
            val raw = body.string()

            if (SseParser.looksLikeEventStream(contentType, raw)) {
                val text = buildString {
                    SseParser.dataPayloads(raw).forEach { payload ->
                        val chunk = runCatching { streamChunkAdapter.fromJson(payload) }.getOrNull()
                        chunk?.choices?.firstOrNull()?.delta?.content?.let { append(it) }
                    }
                }
                if (text.isEmpty()) {
                    throw IllegalStateException("Empty streamed response from OpenAI-compatible API")
                }
                text
            } else {
                val parsed = runCatching { responseAdapter.fromJson(raw) }.getOrNull()
                parsed?.choices?.firstOrNull()?.message?.content
                    ?: throw IllegalStateException("Empty response from OpenAI-compatible API")
            }
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
