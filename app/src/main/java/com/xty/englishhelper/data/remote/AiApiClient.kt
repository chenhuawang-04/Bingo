package com.xty.englishhelper.data.remote

interface AiApiClient {
    /** Send a text message and return the AI reply text */
    suspend fun sendMessage(
        url: String,
        apiKey: String,
        model: String,
        systemPrompt: String?,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): String

    /** Send a multimodal message with images and return the AI reply text */
    suspend fun sendMultimodalMessage(
        url: String,
        apiKey: String,
        model: String,
        imageBytes: List<ByteArray>,
        prompt: String,
        maxTokens: Int
    ): String
}

data class ChatMessage(val role: String, val content: String)
