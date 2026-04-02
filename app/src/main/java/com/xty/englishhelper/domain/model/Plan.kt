package com.xty.englishhelper.domain.model

enum class PlanTaskType {
    REVIEW_DUE_WORDS,
    STUDY_NEW_WORDS,
    READ_ARTICLE,
    PRACTICE_QUESTIONS,
    CUSTOM
}

enum class PlanAutoSource {
    STUDY_DUE_SESSION,
    STUDY_NEW_SESSION,
    ARTICLE_OPEN,
    ARTICLE_TTS_FINISHED,
    QUESTION_SUBMIT
}

enum class PlanStatsMode {
    ALL,
    AUTO,
    MANUAL
}

data class PlanTemplate(
    val id: Long = 0,
    val name: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PlanItem(
    val id: Long = 0,
    val templateId: Long,
    val taskType: PlanTaskType,
    val title: String,
    val targetCount: Int = 1,
    val autoEnabled: Boolean = false,
    val autoSource: PlanAutoSource? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PlanDayRecord(
    val id: Long = 0,
    val dayStart: Long,
    val itemId: Long,
    val doneCount: Int = 0,
    val isCompleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

data class PlanTaskProgress(
    val item: PlanItem,
    val record: PlanDayRecord
) {
    val progress: Float
        get() = if (item.targetCount <= 0) 0f else record.doneCount.toFloat() / item.targetCount.toFloat()
}

data class PlanDaySummary(
    val dayStart: Long,
    val completedCount: Int,
    val totalCount: Int
) {
    val completionRate: Float
        get() = if (totalCount <= 0) 0f else completedCount.toFloat() / totalCount.toFloat()
}

data class PlanTypeSummary(
    val taskType: PlanTaskType,
    val completedCount: Int,
    val totalCount: Int
) {
    val completionRate: Float
        get() = if (totalCount <= 0) 0f else completedCount.toFloat() / totalCount.toFloat()
}

data class PlanAutoEventLog(
    val id: Long = 0,
    val dayStart: Long,
    val eventKey: String,
    val source: PlanAutoSource,
    val createdAt: Long
)
