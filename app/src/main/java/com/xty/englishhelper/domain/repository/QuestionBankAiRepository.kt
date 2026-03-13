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
        sourceUrl: String, passageExcerpt: String,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): VerifyResult

    // ── Generate answers ──
    suspend fun generateAnswers(
        passageText: String, questions: List<QuestionItem>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): List<AnswerResult>

    // ── Scan answers ──
    suspend fun scanAnswers(
        images: List<ByteArray>, questionNumbers: List<Int>,
        apiKey: String, model: String, baseUrl: String, provider: AiProvider
    ): List<AnswerResult>
}

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
