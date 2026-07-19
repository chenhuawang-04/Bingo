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
    val paperType: String = "IMPORTED",
    val status: String = "READY_TO_PRACTICE",
    val dayKey: String? = null,
    val dailySequence: Int = 0,
    val profile: String = "ENGLISH_ONE",
    val blueprintVersion: Int = 1,
    val specialQuestionType: String? = null,
    val compositionMode: String = "MANUAL",
    val selectionStatus: String = "NOT_STARTED",
    val selectionError: String? = null,
    val selectionStartedAt: Long? = null,
    val selectionCompletedAt: Long? = null,
    val generationError: String? = null,
    val generationStartedAt: Long? = null,
    val generationCompletedAt: Long? = null,
    val sources: List<ExamPaperSourceJson> = emptyList(),
    val slotSelections: List<ExamPaperSlotSelectionJson> = emptyList(),
    val answerDrafts: List<ExamPaperAnswerDraftJson> = emptyList(),
    val completedGroupUids: List<String> = emptyList(),
    val groups: List<QuestionGroupJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExamPaperSlotSelectionJson(
    val slotKey: String = "",
    val questionType: String = "",
    val variant: String = "",
    val status: String = "PENDING",
    val articleUid: String? = null,
    val articleTitle: String? = null,
    val selectedScore: Int? = null,
    val candidateCount: Int = 0,
    val reason: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@JsonClass(generateAdapter = true)
data class ExamPaperAnswerDraftJson(
    val groupUid: String = "",
    val questionNumber: Int = 0,
    val userAnswer: String = "",
    val updatedAt: Long = 0
)

@JsonClass(generateAdapter = true)
data class ExamPaperSourceJson(
    val uid: String = "",
    val articleUid: String = "",
    val slotKey: String = "",
    val questionType: String = "",
    val variant: String? = null,
    val orderInPaper: Int = 0,
    val startQuestionNumber: Int = 1,
    val status: String = "COLLECTED",
    val questionGroupUid: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
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
    val sampleSourceTitle: String? = null,
    val sampleSourceUrl: String? = null,
    val sampleSourceInfo: String? = null,
    val practiceRecords: List<PracticeRecordJson> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PracticeRecordJson(
    val userAnswer: String = "",
    val isCorrect: Int = 0,
    val practicedAt: Long = 0
)
