package com.xty.englishhelper.data.remote

import com.xty.englishhelper.data.remote.dto.AnthropicRequest
import com.xty.englishhelper.data.remote.dto.MessageDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnthropicApiClient @Inject constructor(
    private val apiService: AnthropicApiService
) : AiApiClient {

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
        val response = apiService.createMessage(fullUrl, "Bearer $apiKey", request)
        return response.content.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Anthropic API")
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
        val response = apiService.createMultimodalMessage(fullUrl, "Bearer $apiKey", requestBody)
        return response.content.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Anthropic API")
    }

    private fun buildUrl(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
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
