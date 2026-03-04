package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.ArticleOcrResult
import com.xty.englishhelper.domain.model.SentenceAnalysisResult

interface ArticleAiRepository {
    suspend fun extractArticleFromImages(
        imageBytes: List<ByteArray>,
        hint: String?,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): ArticleOcrResult

    suspend fun analyzeSentence(
        sentence: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): SentenceAnalysisResult

    suspend fun extractWordsFromImages(
        imageBytes: List<ByteArray>,
        conditions: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): List<String>
}
