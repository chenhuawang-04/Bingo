package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.QuestionItem

interface QuestionBankAiRepository {

    // ── Scan ──
    suspend fun scanQuestions(
        images: List<ByteArray>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): ScanResult

    // ── Verify source ──
    suspend fun verifySource(
        passageText: String, referenceUrl: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): VerifyResult

    // ── Generate answers ──
    suspend fun generateAnswers(
        passageText: String, questions: List<QuestionItem>,
        questionType: String,
        apiKey: String, model: String, baseUrl: String, providerName: String, provider: AiProvider
    ): List<AnswerResult>

    // ── Scan answers ──
    suspend fun scanAnswers(
        images: List<ByteArray>, questionNumbers: List<Int>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): List<AnswerResult>
    // ── Score translations ──
    suspend fun scoreTranslations(
        items: List<TranslationScoreInput>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): List<TranslationScore>

    // ── Writing: prompt source search ──
    suspend fun searchWritingPromptSource(
        paperTitle: String,
        questionText: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): WritingPromptSourceResult

    // ── Writing: sample search ──
    suspend fun searchWritingSample(
        paperTitle: String,
        questionText: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): WritingSampleResult

    // ── Writing: OCR essay ──
    suspend fun extractWritingFromImages(
        images: List<ByteArray>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): String

    // ── Writing: scoring ──
    suspend fun scoreWriting(
        questionText: String,
        essayText: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): WritingScore
}

data class TranslationScoreInput(
    val questionNumber: Int,
    val originalText: String,
    val referenceTranslation: String,
    val userTranslation: String
)

data class TranslationScore(
    val questionNumber: Int = 0,
    val score: Float = 0f,
    val maxScore: Float = 2f,
    val feedback: String = ""
)

data class WritingSampleResult(
    val matched: Boolean = false,
    val sampleTitle: String? = null,
    val sampleText: String? = null,
    val sourceUrl: String? = null,
    val sourceInfo: String? = null,
    val confidence: Float = 0f,
    val errorMessage: String? = null
)

data class WritingPromptSourceResult(
    val matched: Boolean = false,
    val sourceUrl: String? = null,
    val sourceInfo: String? = null,
    val confidence: Float = 0f,
    val errorMessage: String? = null
)

data class WritingScore(
    val writingType: String = "UNKNOWN",
    val wordCount: Int = 0,
    val band: String = "",
    val totalScore: Float = 0f,
    val maxScore: Float = 0f,
    val subScores: WritingSubScores = WritingSubScores(),
    val deductions: List<WritingDeduction> = emptyList(),
    val summary: String = "",
    val suggestions: List<String> = emptyList()
)

data class WritingSubScores(
    val content: Float = 0f,
    val language: Float = 0f,
    val structure: Float = 0f,
    val format: Float = 0f
)

data class WritingDeduction(
    val reason: String = "",
    val score: Float = 0f
)

data class ScanResult(
    val examPaperTitle: String = "",
    val questionGroups: List<ScannedQuestionGroup> = emptyList(),
    val confidence: Float = 0f
)

data class ScannedQuestionGroup(
    val questionType: String = "READING_COMPREHENSION",
    val sectionLabel: String? = null,
    val directions: String? = null,
    val passageParagraphs: List<String> = emptyList(),
    val sourceInfo: String? = null,
    val sourceUrl: String? = null,
    val questions: List<ScannedQuestion> = emptyList(),
    val wordCount: Int = 0,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
)

data class ScannedQuestion(
    val questionNumber: Int = 0,
    val questionText: String = "",
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val wordCount: Int = 0,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
)

data class VerifyResult(
    val matched: Boolean = false,
    val errorMessage: String? = null,
    val articleTitle: String? = null,
    val articleAuthor: String? = null,
    val articleContent: String? = null,
    val articleSummary: String? = null,
    val articleParagraphs: List<String>? = null,
    val sourceUrl: String? = null
)

data class AnswerResult(
    val questionNumber: Int = 0,
    val answer: String = "",
    val explanation: String? = null,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
)
