package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AiOrganizeResult
import com.xty.englishhelper.domain.model.AiProvider

interface AiRepository {
    suspend fun organizeWord(word: String, apiKey: String, model: String, baseUrl: String, provider: AiProvider): AiOrganizeResult
    suspend fun testConnection(apiKey: String, model: String, baseUrl: String, provider: AiProvider): Boolean
}
