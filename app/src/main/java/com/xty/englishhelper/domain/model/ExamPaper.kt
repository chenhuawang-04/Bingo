package com.xty.englishhelper.domain.model

data class ExamPaper(
    val id: Long = 0,
    val uid: String,
    val title: String,
    val description: String? = null,
    val totalQuestions: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val paperType: ExamPaperType = ExamPaperType.IMPORTED,
    val status: ExamPaperStatus = ExamPaperStatus.READY_TO_PRACTICE,
    val dayKey: String? = null,
    val dailySequence: Int = 0,
    val profile: ExamPaperProfile = ExamPaperProfile.ENGLISH_ONE,
    val blueprintVersion: Int = ExamPaperBlueprint.CURRENT_VERSION,
    val specialQuestionType: QuestionType? = null,
    val compositionMode: ExamPaperCompositionMode = ExamPaperCompositionMode.MANUAL,
    val selectionStatus: AutoPaperSelectionStatus = AutoPaperSelectionStatus.NOT_STARTED,
    val selectionError: String? = null,
    val selectionStartedAt: Long? = null,
    val selectionCompletedAt: Long? = null,
    val generationError: String? = null,
    val generationStartedAt: Long? = null,
    val generationCompletedAt: Long? = null
)

enum class ExamPaperType {
    IMPORTED,
    COMPOSED
}

enum class ExamPaperStatus {
    COLLECTING,
    READY,
    GENERATING,
    READY_TO_PRACTICE,
    FAILED
}

enum class ExamPaperProfile {
    ENGLISH_ONE,
    ENGLISH_TWO
}

enum class ExamPaperSourceStatus {
    COLLECTED,
    GENERATING,
    GENERATED,
    FAILED
}

data class ExamPaperSource(
    val id: Long = 0,
    val uid: String,
    val examPaperId: Long,
    val articleId: Long,
    val articleUid: String,
    val slotKey: String,
    val questionType: QuestionType,
    val variant: String? = null,
    val orderInPaper: Int,
    val startQuestionNumber: Int,
    val status: ExamPaperSourceStatus = ExamPaperSourceStatus.COLLECTED,
    val questionGroupId: Long? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class ExamPaperSummary(
    val paper: ExamPaper,
    val collectedSourceCount: Int,
    val generatedGroupCount: Int
) {
    val requiredSourceCount: Int
        get() = if (paper.paperType == ExamPaperType.COMPOSED) {
            ExamPaperBlueprint.forPaper(paper).slots.size
        } else {
            generatedGroupCount
        }
}

sealed interface ExamPaperCollectionResult {
    val paper: ExamPaper

    data class Added(
        override val paper: ExamPaper,
        val source: ExamPaperSource,
        val collectedCount: Int,
        val requiredCount: Int,
        val becameReady: Boolean
    ) : ExamPaperCollectionResult

    data class Duplicate(
        override val paper: ExamPaper,
        val source: ExamPaperSource
    ) : ExamPaperCollectionResult

    data class TargetFull(
        override val paper: ExamPaper,
        val questionType: QuestionType,
        val variant: String?
    ) : ExamPaperCollectionResult
}
