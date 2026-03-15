package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.BackgroundTaskDao
import com.xty.englishhelper.data.local.entity.BackgroundTaskEntity
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskPayload
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.QuestionAnswerGeneratePayload
import com.xty.englishhelper.domain.model.QuestionSourceVerifyPayload
import com.xty.englishhelper.domain.model.QuestionWritingSamplePayload
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.model.WordOrganizePayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundTaskRepositoryImpl @Inject constructor(
    private val dao: BackgroundTaskDao
) : BackgroundTaskRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeAllTasks(): Flow<List<BackgroundTask>> {
        return dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }
    }

    override suspend fun getTaskById(id: Long): BackgroundTask? {
        return dao.getById(id)?.toDomain()
    }

    override suspend fun getTaskByDedupeKey(dedupeKey: String): BackgroundTask? {
        return dao.getByDedupeKey(dedupeKey)?.toDomain()
    }

    override suspend fun insertTask(
        type: BackgroundTaskType,
        payload: BackgroundTaskPayload,
        dedupeKey: String
    ): Long {
        val now = System.currentTimeMillis()
        val payloadJson = encodePayload(type, payload)
        val entity = BackgroundTaskEntity(
            type = type.name,
            status = BackgroundTaskStatus.PENDING.name,
            payloadJson = payloadJson,
            createdAt = now,
            updatedAt = now,
            dedupeKey = dedupeKey
        )
        return dao.insert(entity)
    }

    override suspend fun getTasksByStatuses(
        statuses: List<BackgroundTaskStatus>,
        limit: Int
    ): List<BackgroundTask> {
        val entities = dao.getByStatuses(statuses.map { it.name }, limit)
        val results = mutableListOf<BackgroundTask>()
        for (entity in entities) {
            val domain = entity.toDomain()
            if (domain?.payload == null && domain != null) {
                dao.updateStatus(
                    entity.id,
                    BackgroundTaskStatus.FAILED.name,
                    "任务参数缺失",
                    System.currentTimeMillis()
                )
                continue
            }
            if (domain != null) {
                results.add(domain)
            }
        }
        return results
    }

    override suspend fun updateStatus(id: Long, status: BackgroundTaskStatus, errorMessage: String?) {
        dao.updateStatus(id, status.name, errorMessage, System.currentTimeMillis())
    }

    override suspend fun updateProgress(id: Long, current: Int, total: Int) {
        dao.updateProgress(id, current, total, System.currentTimeMillis())
    }

    override suspend fun incrementAttempt(id: Long) {
        dao.incrementAttempt(id, System.currentTimeMillis())
    }

    override suspend fun updatePayload(id: Long, type: BackgroundTaskType, payload: BackgroundTaskPayload) {
        val payloadJson = encodePayload(type, payload)
        dao.updatePayload(id, payloadJson, System.currentTimeMillis())
    }

    override suspend fun updateStatusByStatus(fromStatus: BackgroundTaskStatus, toStatus: BackgroundTaskStatus) {
        dao.updateStatusByStatus(fromStatus.name, toStatus.name, System.currentTimeMillis())
    }

    override suspend fun deleteTask(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun deleteTasksByStatuses(statuses: List<BackgroundTaskStatus>) {
        dao.deleteByStatuses(statuses.map { it.name })
    }

    private fun encodePayload(type: BackgroundTaskType, payload: BackgroundTaskPayload): String {
        return when (type) {
            BackgroundTaskType.WORD_ORGANIZE -> json.encodeToString(payload as WordOrganizePayload)
            BackgroundTaskType.WORD_POOL_REBUILD -> json.encodeToString(payload as WordPoolRebuildPayload)
            BackgroundTaskType.QUESTION_ANSWER_GENERATE -> json.encodeToString(payload as QuestionAnswerGeneratePayload)
            BackgroundTaskType.QUESTION_SOURCE_VERIFY -> json.encodeToString(payload as QuestionSourceVerifyPayload)
            BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH -> json.encodeToString(payload as QuestionWritingSamplePayload)
            BackgroundTaskType.UNKNOWN -> "{}"
        }
    }

    private fun parsePayload(type: BackgroundTaskType, raw: String): BackgroundTaskPayload? {
        return runCatching {
            when (type) {
                BackgroundTaskType.WORD_ORGANIZE -> json.decodeFromString<WordOrganizePayload>(raw)
                BackgroundTaskType.WORD_POOL_REBUILD -> json.decodeFromString<WordPoolRebuildPayload>(raw)
                BackgroundTaskType.QUESTION_ANSWER_GENERATE -> json.decodeFromString<QuestionAnswerGeneratePayload>(raw)
                BackgroundTaskType.QUESTION_SOURCE_VERIFY -> json.decodeFromString<QuestionSourceVerifyPayload>(raw)
                BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH -> json.decodeFromString<QuestionWritingSamplePayload>(raw)
                BackgroundTaskType.UNKNOWN -> null
            }
        }.getOrNull()
    }

    private fun BackgroundTaskEntity.toDomain(): BackgroundTask? {
        val typeEnum = runCatching { BackgroundTaskType.valueOf(type) }.getOrElse { BackgroundTaskType.UNKNOWN }
        val statusEnum = runCatching { BackgroundTaskStatus.valueOf(status) }.getOrElse { BackgroundTaskStatus.FAILED }
        return BackgroundTask(
            id = id,
            type = typeEnum,
            status = statusEnum,
            payload = parsePayload(typeEnum, payloadJson),
            progressCurrent = progressCurrent,
            progressTotal = progressTotal,
            attempt = attempt,
            errorMessage = errorMessage,
            createdAt = createdAt,
            updatedAt = updatedAt,
            dedupeKey = dedupeKey
        )
    }
}
