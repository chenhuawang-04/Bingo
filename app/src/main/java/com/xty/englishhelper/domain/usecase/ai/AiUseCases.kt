package com.xty.englishhelper.domain.usecase.ai

import com.xty.englishhelper.domain.model.AiOrganizeResult
import com.xty.englishhelper.domain.repository.AiRepository
import javax.inject.Inject

class OrganizeWordWithAiUseCase @Inject constructor(
    private val repository: AiRepository
) {
    suspend operator fun invoke(
        word: String,
        apiKey: String,
        model: String,
        baseUrl: String
    ): AiOrganizeResult = repository.organizeWord(word, apiKey, model, baseUrl)
}

class TestAiConnectionUseCase @Inject constructor(
    private val repository: AiRepository
) {
    suspend operator fun invoke(apiKey: String, model: String, baseUrl: String): Boolean =
        repository.testConnection(apiKey, model, baseUrl)
}
