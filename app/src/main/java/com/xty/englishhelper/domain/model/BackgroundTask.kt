package com.xty.englishhelper.domain.model

data class BackgroundTask(
    val id: Long,
    val type: BackgroundTaskType,
    val status: BackgroundTaskStatus,
    val payload: BackgroundTaskPayload?,
    val progressCurrent: Int,
    val progressTotal: Int,
    val progressMessage: String?,
    val attempt: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val dedupeKey: String
)

enum class BackgroundTaskType {
    UNKNOWN,
    WORD_ORGANIZE,
    WORD_NOTE_ORGANIZE,
    WORD_POOL_REBUILD,
    WORD_POOL_REVIEW,
    WORD_PHRASE_ORGANIZE,
    QUESTION_GENERATE,
    QUESTION_ANSWER_GENERATE,
    QUESTION_SOURCE_VERIFY,
    QUESTION_WRITING_SAMPLE_SEARCH,
    ONLINE_ARTICLE_SCAN_SCORE,
    APP_UPDATE_CHECK,
    CLOUD_SYNC
}

enum class BackgroundTaskVisibility {
    VISIBLE,
    HIDDEN
}

enum class BackgroundTaskPriority(val weight: Int) {
    MAINTENANCE(0),
    NORMAL(10),
    USER_INITIATED(20),
    CRITICAL(30)
}

val BackgroundTaskType.visibility: BackgroundTaskVisibility
    get() = when (this) {
        BackgroundTaskType.APP_UPDATE_CHECK -> BackgroundTaskVisibility.HIDDEN
        else -> BackgroundTaskVisibility.VISIBLE
    }

val BackgroundTaskType.priority: BackgroundTaskPriority
    get() = when (this) {
        BackgroundTaskType.CLOUD_SYNC -> BackgroundTaskPriority.CRITICAL
        BackgroundTaskType.WORD_ORGANIZE,
        BackgroundTaskType.WORD_NOTE_ORGANIZE,
        BackgroundTaskType.QUESTION_GENERATE,
        BackgroundTaskType.QUESTION_ANSWER_GENERATE,
        BackgroundTaskType.QUESTION_SOURCE_VERIFY,
        BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH -> BackgroundTaskPriority.USER_INITIATED
        BackgroundTaskType.APP_UPDATE_CHECK,
        BackgroundTaskType.ONLINE_ARTICLE_SCAN_SCORE -> BackgroundTaskPriority.MAINTENANCE
        else -> BackgroundTaskPriority.NORMAL
    }

val BackgroundTask.isHiddenByDefault: Boolean
    get() = type.visibility == BackgroundTaskVisibility.HIDDEN

enum class BackgroundTaskStatus {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESS,
    FAILED,
    CANCELED
}

enum class RebuildMode {
    FULL,        // Delete all existing data, rebuild from scratch
    INCREMENTAL  // Resume from last progress, keep existing pools during rebuild
}


