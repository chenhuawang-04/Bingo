package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.ArticleOcrResult
import com.xty.englishhelper.domain.model.SentenceAnalysisResult

interface ArticleAiRepository {
    suspend fun extractArticleFromImages(
        imageBytes: List<ByteArray>,
        hint: String?,
        apiKey: String,
        model: String,
        baseUrl: String
    ): ArticleOcrResult

    suspend fun analyzeSentence(
        sentence: String,
        apiKey: String,
        model: String,
        baseUrl: String
    ): SentenceAnalysisResult
}
