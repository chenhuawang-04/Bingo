package com.xty.englishhelper.data.remote

import com.xty.englishhelper.data.remote.dto.OpenAiContentPart
import com.xty.englishhelper.data.remote.dto.OpenAiImageUrl
import com.xty.englishhelper.data.remote.dto.OpenAiMessageDto
import com.xty.englishhelper.data.remote.dto.OpenAiRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiCompatibleApiClient @Inject constructor(
    private val apiService: OpenAiApiService
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
        val base = baseUrl.trimEnd('/')
        return when {
            base.endsWith("/v1/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    private fun extractContent(response: com.xty.englishhelper.data.remote.dto.OpenAiResponse): String {
        return response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Empty response from OpenAI-compatible API")
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
