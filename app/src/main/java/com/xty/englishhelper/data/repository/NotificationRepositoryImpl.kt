package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.NotificationDao
import com.xty.englishhelper.data.local.entity.AppNotificationEntity
import com.xty.englishhelper.domain.model.AppNotification
import com.xty.englishhelper.domain.model.AppNotificationCategory
import com.xty.englishhelper.domain.model.AppNotificationTargetType
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val dao: NotificationDao
) : NotificationRepository {
    override fun observeAll(): Flow<List<AppNotification>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override suspend fun insert(notification: AppNotification): Long =
        dao.insert(notification.toEntity())

    override suspend fun markRead(id: Long) = dao.markRead(id, System.currentTimeMillis())

    override suspend fun markAllRead() = dao.markAllRead(System.currentTimeMillis())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun clearRead() = dao.clearRead()

    private fun AppNotificationEntity.toDomain() = AppNotification(
        id = id,
        uid = uid,
        eventKey = eventKey,
        category = enumValueOrDefault(category, AppNotificationCategory.SYSTEM),
        title = title,
        message = message,
        targetType = enumValueOrDefault(targetType, AppNotificationTargetType.NONE),
        targetId = targetId,
        targetAux = targetAux,
        sourceTaskId = sourceTaskId,
        sourceTaskType = sourceTaskType?.let { enumValueOrNull<BackgroundTaskType>(it) },
        sourceTaskStatus = sourceTaskStatus?.let { enumValueOrNull<BackgroundTaskStatus>(it) },
        isRead = isRead,
        createdAt = createdAt,
        readAt = readAt
    )

    private fun AppNotification.toEntity() = AppNotificationEntity(
        id = id,
        uid = uid,
        eventKey = eventKey,
        category = category.name,
        title = title,
        message = message,
        targetType = targetType.name,
        targetId = targetId,
        targetAux = targetAux,
        sourceTaskId = sourceTaskId,
        sourceTaskType = sourceTaskType?.name,
        sourceTaskStatus = sourceTaskStatus?.name,
        isRead = isRead,
        createdAt = createdAt,
        readAt = readAt
    )

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? =
        enumValues<T>().firstOrNull { it.name == raw }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValueOrNull<T>(raw) ?: fallback
}
