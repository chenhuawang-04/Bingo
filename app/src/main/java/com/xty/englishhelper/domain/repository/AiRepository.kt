package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AiOrganizeResult

interface AiRepository {
    suspend fun organizeWord(word: String, apiKey: String, model: String, baseUrl: String): AiOrganizeResult
    suspend fun testConnection(apiKey: String, model: String, baseUrl: String): Boolean
}
