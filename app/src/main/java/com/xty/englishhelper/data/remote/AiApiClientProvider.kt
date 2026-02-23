package com.xty.englishhelper.data.remote

import com.xty.englishhelper.domain.model.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiApiClientProvider @Inject constructor(
    private val anthropicClient: AnthropicApiClient,
    private val openAiClient: OpenAiCompatibleApiClient
) {
    fun getClient(provider: AiProvider): AiApiClient = when (provider) {
        AiProvider.ANTHROPIC -> anthropicClient
        AiProvider.OPENAI_COMPATIBLE -> openAiClient
    }
}
