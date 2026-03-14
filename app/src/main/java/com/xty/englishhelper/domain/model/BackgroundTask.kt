package com.xty.englishhelper.domain.model

data class BackgroundTask(
    val id: Long,
    val type: BackgroundTaskType,
    val status: BackgroundTaskStatus,
    val payload: BackgroundTaskPayload?,
    val progressCurrent: Int,
    val progressTotal: Int,
    val attempt: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val dedupeKey: String
)

enum class BackgroundTaskType {
    UNKNOWN,
    WORD_ORGANIZE,
    WORD_POOL_REBUILD,
    QUESTION_ANSWER_GENERATE,
    QUESTION_SOURCE_VERIFY
}

enum class BackgroundTaskStatus {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESS,
    FAILED,
    CANCELED
}
