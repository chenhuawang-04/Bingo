package com.xty.englishhelper.domain.usecase.ai

import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.repository.AiModelRepository
import javax.inject.Inject

class FetchAiModelsUseCase @Inject constructor(
    private val repository: AiModelRepository
) {
    suspend operator fun invoke(apiKey: String, provider: AiProvider, baseUrl: String): List<String> {
        return repository.fetchModels(provider, apiKey, baseUrl)
    }
}
