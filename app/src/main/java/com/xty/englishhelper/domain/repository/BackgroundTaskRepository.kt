package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskPayload
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import kotlinx.coroutines.flow.Flow

interface BackgroundTaskRepository {

    fun observeAllTasks(): Flow<List<BackgroundTask>>

    suspend fun getTaskById(id: Long): BackgroundTask?

    suspend fun getTaskByDedupeKey(dedupeKey: String): BackgroundTask?

    suspend fun insertTask(
        type: BackgroundTaskType,
        payload: BackgroundTaskPayload,
        dedupeKey: String
    ): Long

    suspend fun getTasksByStatuses(statuses: List<BackgroundTaskStatus>, limit: Int): List<BackgroundTask>

    suspend fun updateStatus(id: Long, status: BackgroundTaskStatus, errorMessage: String? = null)

    suspend fun updateProgress(id: Long, current: Int, total: Int)

    suspend fun incrementAttempt(id: Long)

    suspend fun updatePayload(id: Long, type: BackgroundTaskType, payload: BackgroundTaskPayload)

    suspend fun updateStatusByStatus(fromStatus: BackgroundTaskStatus, toStatus: BackgroundTaskStatus)

    suspend fun deleteTask(id: Long)

    suspend fun deleteTasksByStatuses(statuses: List<BackgroundTaskStatus>)
}
