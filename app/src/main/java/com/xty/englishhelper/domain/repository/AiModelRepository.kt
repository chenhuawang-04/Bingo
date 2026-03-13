package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AiProvider

interface AiModelRepository {
    suspend fun fetchModels(provider: AiProvider, apiKey: String, baseUrl: String): List<String>
}
