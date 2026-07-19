package com.xty.englishhelper.domain.model

data class AppNotification(
    val id: Long = 0,
    val uid: String,
    val eventKey: String,
    val category: AppNotificationCategory,
    val title: String,
    val message: String,
    val targetType: AppNotificationTargetType = AppNotificationTargetType.NONE,
    val targetId: Long? = null,
    val targetAux: String? = null,
    val sourceTaskId: Long? = null,
    val sourceTaskType: BackgroundTaskType? = null,
    val sourceTaskStatus: BackgroundTaskStatus? = null,
    val isRead: Boolean = false,
    val createdAt: Long,
    val readAt: Long? = null
)

enum class AppNotificationCategory {
    TASK_SUCCESS,
    TASK_FAILURE,
    AUTO_PAPER,
    SYSTEM
}

enum class AppNotificationTargetType {
    NONE,
    BACKGROUND_TASKS,
    ARTICLE,
    QUESTION_GROUP,
    EXAM_PAPER,
    AUTO_PAPER,
    SETTINGS
}
