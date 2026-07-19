package com.xty.englishhelper.domain.model

enum class ExamPaperCompositionMode {
    MANUAL,
    AUTOMATIC
}

enum class AutoPaperSelectionStatus {
    NOT_STARTED,
    SELECTING,
    COMPLETED,
    FAILED
}

enum class ExamPaperSlotSelectionStatus {
    PENDING,
    SELECTING,
    SELECTED,
    EMPTY,
    FAILED
}

data class ExamPaperSlotSelection(
    val id: Long = 0,
    val examPaperId: Long,
    val slotKey: String,
    val questionType: QuestionType,
    val variant: String? = null,
    val status: ExamPaperSlotSelectionStatus,
    val articleId: Long? = null,
    val articleUid: String? = null,
    val articleTitle: String? = null,
    val selectedScore: Int? = null,
    val candidateCount: Int = 0,
    val reason: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
