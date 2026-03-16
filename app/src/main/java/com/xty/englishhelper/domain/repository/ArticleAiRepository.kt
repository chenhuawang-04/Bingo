package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.ArticleOcrResult
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.QuickWordAnalysis
import com.xty.englishhelper.domain.model.SentenceAnalysisResult

data class ArticleSuitabilityResult(
    val score: Int = 0,
    val reason: String = ""
)

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

    suspend fun analyzeParagraph(
        paragraphText: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): ParagraphAnalysisResult

    suspend fun extractWordsFromImages(
        imageBytes: List<ByteArray>,
        conditions: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): List<String>

    suspend fun translateParagraph(
        paragraphText: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): String

    suspend fun quickAnalyzeWord(
        word: String,
        contextSentence: String?,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): QuickWordAnalysis

    suspend fun evaluateArticleSuitability(
        title: String,
        excerpt: String,
        trailText: String?,
        source: String?,
        section: String?,
        wordCount: Int?,
        url: String?,
        apiKey: String,
        model: String,
        baseUrl: String,
        provider: AiProvider
    ): ArticleSuitabilityResult
}
