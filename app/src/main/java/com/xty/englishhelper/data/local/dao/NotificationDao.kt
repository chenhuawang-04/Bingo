package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xty.englishhelper.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM app_notifications ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE is_read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AppNotificationEntity): Long

    @Query("UPDATE app_notifications SET is_read = 1, read_at = :readAt WHERE id = :id")
    suspend fun markRead(id: Long, readAt: Long)

    @Query("UPDATE app_notifications SET is_read = 1, read_at = :readAt WHERE is_read = 0")
    suspend fun markAllRead(readAt: Long)

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM app_notifications WHERE is_read = 1")
    suspend fun clearRead()
}
