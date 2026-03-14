package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuestionBankExportModel(
    val schemaVersion: Int = 1,
    val papers: List<ExamPaperJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExamPaperJson(
    val uid: String = "",
    val title: String = "",
    val description: String? = null,
    val totalQuestions: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val groups: List<QuestionGroupJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class QuestionGroupJson(
    val uid: String = "",
    val questionType: String = "",
    val sectionLabel: String? = null,
    val orderInPaper: Int = 0,
    val directions: String? = null,
    val passageText: String = "",
    val sourceInfo: String? = null,
    val sourceUrl: String? = null,
    val sourceAuthor: String? = null,
    val sourceVerified: Int = 0,
    val sourceVerifyError: String? = null,
    val wordCount: Int = 0,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null,
    val hasAiAnswer: Int = 0,
    val hasScannedAnswer: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val linkedArticleUid: String = "",
    val paragraphs: List<ParagraphJson> = emptyList(),
    val items: List<QuestionItemJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ParagraphJson(
    val paragraphIndex: Int = 0,
    val text: String = "",
    val paragraphType: String = "TEXT",
    val imageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class QuestionItemJson(
    val questionNumber: Int = 0,
    val questionText: String = "",
    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,
    val correctAnswer: String? = null,
    val answerSource: String = "NONE",
    val explanation: String? = null,
    val orderInGroup: Int = 0,
    val wordCount: Int = 0,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null,
    val wrongCount: Int = 0,
    val extraData: String? = null,
    val practiceRecords: List<PracticeRecordJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PracticeRecordJson(
    val userAnswer: String = "",
    val isCorrect: Int = 0,
    val practicedAt: Long = 0
)
