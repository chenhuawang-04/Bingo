package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeAll(): Flow<List<AppNotification>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun insert(notification: AppNotification): Long
    suspend fun markRead(id: Long)
    suspend fun markAllRead()
    suspend fun delete(id: Long)
    suspend fun clearRead()
}
