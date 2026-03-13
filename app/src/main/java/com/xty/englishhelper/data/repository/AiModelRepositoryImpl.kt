package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.AnthropicApiService
import com.xty.englishhelper.data.remote.OpenAiApiService
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.repository.AiModelRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiModelRepositoryImpl @Inject constructor(
    private val anthropicApiService: AnthropicApiService,
    private val openAiApiService: OpenAiApiService
) : AiModelRepository {

    override suspend fun fetchModels(provider: AiProvider, apiKey: String, baseUrl: String): List<String> {
        return when (provider) {
            AiProvider.ANTHROPIC -> fetchAnthropicModels(apiKey, baseUrl)
            AiProvider.OPENAI_COMPATIBLE -> fetchOpenAiModels(apiKey, baseUrl)
        }
    }

    private suspend fun fetchAnthropicModels(apiKey: String, baseUrl: String): List<String> {
        val url = buildModelsUrl(baseUrl)
        val response = anthropicApiService.listModels(url, apiKey)
        return response.data.mapNotNull { it.id }.filter { it.isNotBlank() }.sorted()
    }

    private suspend fun fetchOpenAiModels(apiKey: String, baseUrl: String): List<String> {
        val url = buildModelsUrl(baseUrl)
        val response = openAiApiService.listModels(url, "Bearer $apiKey")
        return response.data.mapNotNull { it.id }.filter { it.isNotBlank() }.sorted()
    }

    private fun buildModelsUrl(baseUrl: String): String {
        var base = baseUrl.trim().trimEnd('/')
        if (!base.startsWith("http://", ignoreCase = true) &&
            !base.startsWith("https://", ignoreCase = true)) {
            base = "http://$base"
        }
        val lower = base.lowercase()
        val v1Index = lower.indexOf("/v1")
        if (v1Index >= 0) {
            base = base.substring(0, v1Index + 3)
        }
        return if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
    }
}
