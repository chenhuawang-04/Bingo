package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.xty.englishhelper.data.local.entity.BackgroundTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackgroundTaskDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: BackgroundTaskEntity): Long

    @Update
    suspend fun update(task: BackgroundTaskEntity)

    @Query("SELECT * FROM background_tasks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BackgroundTaskEntity>>

    @Query("SELECT * FROM background_tasks WHERE type IN (:types) ORDER BY created_at DESC")
    fun observeByTypes(types: List<String>): Flow<List<BackgroundTaskEntity>>

    @Query("SELECT * FROM background_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BackgroundTaskEntity?

    @Query("SELECT * FROM background_tasks WHERE dedupe_key = :dedupeKey LIMIT 1")
    suspend fun getByDedupeKey(dedupeKey: String): BackgroundTaskEntity?

    @Query(
        """
        SELECT * FROM background_tasks
        WHERE status IN (:statuses)
        ORDER BY created_at ASC
        LIMIT :limit
        """
    )
    suspend fun getByStatuses(statuses: List<String>, limit: Int): List<BackgroundTaskEntity>

    @Query(
        """
        UPDATE background_tasks
        SET status = :status, error_message = :errorMessage, updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateStatus(id: Long, status: String, errorMessage: String?, updatedAt: Long)

    @Query(
        """
        UPDATE background_tasks
        SET progress_current = :current, progress_total = :total, updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateProgress(id: Long, current: Int, total: Int, updatedAt: Long)

    @Query(
        """
        UPDATE background_tasks
        SET payload_json = :payloadJson, updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updatePayload(id: Long, payloadJson: String, updatedAt: Long)

    @Query(
        """
        UPDATE background_tasks
        SET attempt = attempt + 1, updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun incrementAttempt(id: Long, updatedAt: Long)

    @Query(
        """
        UPDATE background_tasks
        SET status = :toStatus, updated_at = :updatedAt
        WHERE status = :fromStatus
        """
    )
    suspend fun updateStatusByStatus(fromStatus: String, toStatus: String, updatedAt: Long)

    @Query("DELETE FROM background_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM background_tasks WHERE status IN (:statuses)")
    suspend fun deleteByStatuses(statuses: List<String>)
}
