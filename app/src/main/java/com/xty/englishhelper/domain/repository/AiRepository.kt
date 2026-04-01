package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AiOrganizeResult
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.WordResearchReference

interface AiRepository {
    suspend fun organizeWord(
        word: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider,
        reference: WordResearchReference? = null,
        supplementalReferenceHints: List<String> = emptyList()
    ): AiOrganizeResult
    suspend fun researchWord(
        word: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): WordResearchReference
    suspend fun testConnection(apiKey: String, model: String, baseUrl: String, provider: AiProvider): Boolean
}
