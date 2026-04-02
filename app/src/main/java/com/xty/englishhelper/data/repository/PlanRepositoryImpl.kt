package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.PlanDao
import com.xty.englishhelper.data.local.entity.PlanDayRecordEntity
import com.xty.englishhelper.data.local.entity.PlanEventLogEntity
import com.xty.englishhelper.data.local.entity.PlanItemEntity
import com.xty.englishhelper.data.local.entity.PlanTemplateEntity
import com.xty.englishhelper.domain.model.PlanDayRecord
import com.xty.englishhelper.domain.model.PlanDaySummary
import com.xty.englishhelper.domain.model.PlanAutoEventLog
import com.xty.englishhelper.domain.model.PlanBackup
import com.xty.englishhelper.domain.model.PlanDayRecordBackup
import com.xty.englishhelper.domain.model.PlanItem
import com.xty.englishhelper.domain.model.PlanItemBackup
import com.xty.englishhelper.domain.model.PlanEventLogBackup
import com.xty.englishhelper.domain.model.PlanAutoSource
import com.xty.englishhelper.domain.model.PlanTaskProgress
import com.xty.englishhelper.domain.model.PlanStatsMode
import com.xty.englishhelper.domain.model.PlanTaskType
import com.xty.englishhelper.domain.model.PlanTemplate
import com.xty.englishhelper.domain.model.PlanTemplateBackup
import com.xty.englishhelper.domain.model.PlanTypeSummary
import com.xty.englishhelper.domain.plan.PlanProgressRules
import com.xty.englishhelper.domain.repository.PlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PlanRepositoryImpl @Inject constructor(
    private val dao: PlanDao
) : PlanRepository {

    override fun observeTemplates(): Flow<List<PlanTemplate>> =
        dao.observeTemplates().map { entities -> entities.map { it.toDomain() } }

    override fun observeActiveTemplate(): Flow<PlanTemplate?> =
        dao.observeActiveTemplate().map { it?.toDomain() }

    override fun observeItemsByTemplate(templateId: Long): Flow<List<PlanItem>> =
        dao.observeItemsByTemplate(templateId).map { entities -> entities.map { it.toDomain() } }

    override fun observeTodayTasks(dayStart: Long): Flow<List<PlanTaskProgress>> {
        val recordsFlow = dao.observeDayRecords(dayStart)
        return dao.observeActiveTemplate()
            .flatMapLatest { template ->
                if (template == null) {
                    flowOf(emptyList())
                } else {
                    combine(
                        dao.observeItemsByTemplate(template.id),
                        recordsFlow
                    ) { items, records ->
                        val recordByItem = records.associateBy { it.itemId }
                        items.map { item ->
                            val record = recordByItem[item.id] ?: PlanDayRecordEntity(
                                dayStart = dayStart,
                                itemId = item.id,
                                doneCount = 0,
                                isCompleted = 0,
                                updatedAt = System.currentTimeMillis(),
                                completedAt = null
                            )
                            PlanTaskProgress(
                                item = item.toDomain(),
                                record = record.toDomain()
                            )
                        }
                    }
                }
            }
    }

    override fun observeDaySummaries(limitDays: Int, mode: PlanStatsMode): Flow<List<PlanDaySummary>> {
        val source = when (mode) {
            PlanStatsMode.ALL -> dao.observeDaySummaries(limitDays)
            PlanStatsMode.AUTO -> dao.observeDaySummariesByAuto(limitDays, 1)
            PlanStatsMode.MANUAL -> dao.observeDaySummariesByAuto(limitDays, 0)
        }
        return source.map { rows ->
            rows.map {
                PlanDaySummary(
                    dayStart = it.dayStart,
                    completedCount = it.completedCount,
                    totalCount = it.totalCount
                )
            }
        }
    }

    override fun observeTodayEventLogs(dayStart: Long, limit: Int): Flow<List<PlanAutoEventLog>> =
        dao.observeEventLogs(dayStart, limit).map { rows ->
            rows.mapNotNull { row ->
                runCatching { PlanAutoSource.valueOf(row.taskType) }
                    .getOrNull()
                    ?.let { source ->
                        PlanAutoEventLog(
                            id = row.id,
                            dayStart = row.dayStart,
                            eventKey = row.eventKey,
                            source = source,
                            createdAt = row.createdAt
                        )
                    }
            }
        }

    override fun observeTypeSummaries(limitDays: Int, mode: PlanStatsMode): Flow<List<PlanTypeSummary>> {
        val from = startOfDay(
            System.currentTimeMillis() - (limitDays.toLong() - 1L).coerceAtLeast(0L) * DAY_MS
        )
        val source = when (mode) {
            PlanStatsMode.ALL -> dao.observeTypeSummaries(from)
            PlanStatsMode.AUTO -> dao.observeTypeSummariesByAuto(from, 1)
            PlanStatsMode.MANUAL -> dao.observeTypeSummariesByAuto(from, 0)
        }
        return source.map { rows ->
            rows.mapNotNull { row ->
                runCatching { PlanTaskType.valueOf(row.taskType) }
                    .getOrNull()
                    ?.let { taskType ->
                        PlanTypeSummary(
                            taskType = taskType,
                            completedCount = row.completedCount,
                            totalCount = row.totalCount
                        )
                    }
            }
        }
    }

    override suspend fun ensureDefaultTemplate() {
        val templateCount = dao.countTemplates()
        if (templateCount > 0) {
            if (dao.getActiveTemplateId() == null) {
                dao.getLatestTemplateId()?.let { fallbackId ->
                    dao.activateTemplate(fallbackId, System.currentTimeMillis())
                }
            }
            return
        }

        val now = System.currentTimeMillis()
        val templateId = dao.insertTemplate(
            PlanTemplateEntity(
                name = "默认计划",
                isActive = 1,
                createdAt = now,
                updatedAt = now
            )
        )
        listOf(
            PlanItemEntity(
                templateId = templateId,
                type = PlanTaskType.REVIEW_DUE_WORDS.name,
                title = "复习到期单词",
                targetCount = 1,
                autoEnabled = 1,
                autoSource = PlanAutoSource.STUDY_DUE_SESSION.name,
                orderIndex = 0,
                createdAt = now,
                updatedAt = now
            ),
            PlanItemEntity(
                templateId = templateId,
                type = PlanTaskType.STUDY_NEW_WORDS.name,
                title = "学习新词单元",
                targetCount = 1,
                autoEnabled = 1,
                autoSource = PlanAutoSource.STUDY_NEW_SESSION.name,
                orderIndex = 1,
                createdAt = now,
                updatedAt = now
            ),
            PlanItemEntity(
                templateId = templateId,
                type = PlanTaskType.READ_ARTICLE.name,
                title = "阅读文章",
                targetCount = 1,
                autoEnabled = 1,
                autoSource = PlanAutoSource.ARTICLE_OPEN.name,
                orderIndex = 2,
                createdAt = now,
                updatedAt = now
            ),
            PlanItemEntity(
                templateId = templateId,
                type = PlanTaskType.PRACTICE_QUESTIONS.name,
                title = "题库练习",
                targetCount = 1,
                autoEnabled = 1,
                autoSource = PlanAutoSource.QUESTION_SUBMIT.name,
                orderIndex = 3,
                createdAt = now,
                updatedAt = now
            )
        ).forEach { dao.insertItem(it) }
    }

    override suspend fun setActiveTemplate(templateId: Long) {
        dao.activateTemplate(templateId, System.currentTimeMillis())
    }

    override suspend fun createTemplate(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertTemplate(
            PlanTemplateEntity(
                name = name,
                isActive = 0,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun renameTemplate(templateId: Long, name: String) {
        val existing = dao.getTemplateById(templateId) ?: return
        dao.updateTemplate(existing.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteTemplate(templateId: Long) {
        val existing = dao.getTemplateById(templateId) ?: return
        dao.deleteTemplate(templateId)
        if (existing.isActive == 1) {
            dao.getLatestTemplateId()?.let { fallbackId ->
                dao.activateTemplate(fallbackId, System.currentTimeMillis())
            }
        }
        if (dao.countTemplates() == 0) {
            ensureDefaultTemplate()
        }
    }

    override suspend fun addItem(item: PlanItem): Long = dao.insertItem(item.toEntity())

    override suspend fun updateItem(item: PlanItem) {
        dao.updateItem(item.toEntity())
    }

    override suspend fun deleteItem(itemId: Long) {
        dao.deleteItem(itemId)
    }

    override suspend fun ensureDayRecords(dayStart: Long) {
        val activeTemplateId = dao.getActiveTemplateId() ?: return
        val items = dao.getItemsByTemplate(activeTemplateId)
        if (items.isEmpty()) return

        val now = System.currentTimeMillis()
        val pendingRecords = items.map { item ->
            PlanDayRecordEntity(
                dayStart = dayStart,
                itemId = item.id,
                doneCount = 0,
                isCompleted = 0,
                updatedAt = now,
                completedAt = null
            )
        }
        dao.insertDayRecords(pendingRecords)
    }

    override suspend fun updateTaskProgress(dayStart: Long, itemId: Long, doneCount: Int) {
        val now = System.currentTimeMillis()
        val item = dao.getItemById(itemId) ?: return
        val target = PlanProgressRules.normalizedTarget(item.targetCount)
        val existing = dao.getDayRecord(dayStart, itemId)
        val clampedDone = doneCount.coerceAtLeast(0)
        val completed = PlanProgressRules.isCompleted(clampedDone, target)
        if (existing == null) {
            dao.insertDayRecords(
                listOf(
                    PlanDayRecordEntity(
                        dayStart = dayStart,
                        itemId = itemId,
                        doneCount = clampedDone,
                        isCompleted = if (completed) 1 else 0,
                        updatedAt = now,
                        completedAt = if (completed) now else null
                    )
                )
            )
            return
        }

        dao.updateDayRecord(
            existing.copy(
                doneCount = clampedDone,
                isCompleted = if (completed) 1 else 0,
                updatedAt = now,
                completedAt = if (completed) existing.completedAt ?: now else null
            )
        )
    }

    override suspend fun setTaskCompleted(dayStart: Long, itemId: Long, completed: Boolean) {
        val now = System.currentTimeMillis()
        val item = dao.getItemById(itemId) ?: return
        val target = PlanProgressRules.normalizedTarget(item.targetCount)
        val existing = dao.getDayRecord(dayStart, itemId)
        if (existing == null) {
            dao.insertDayRecords(
                listOf(
                    PlanDayRecordEntity(
                        dayStart = dayStart,
                        itemId = itemId,
                        doneCount = if (completed) target else 0,
                        isCompleted = if (completed) 1 else 0,
                        updatedAt = now,
                        completedAt = if (completed) now else null
                    )
                )
            )
            return
        }

        dao.updateDayRecord(
            existing.copy(
                isCompleted = if (completed) 1 else 0,
                updatedAt = now,
                completedAt = if (completed) existing.completedAt ?: now else null,
                doneCount = if (completed) {
                    PlanProgressRules.doneCountWhenMarkCompleted(existing.doneCount, target)
                } else {
                    PlanProgressRules.doneCountWhenMarkIncomplete(existing.doneCount, target)
                }
            )
        )
    }

    override suspend fun consumeAutoProgress(
        source: PlanAutoSource,
        eventKey: String,
        delta: Int
    ): Boolean {
        val safeDelta = delta.coerceAtLeast(1)
        val dayStart = startOfDay(System.currentTimeMillis())
        ensureDefaultTemplate()
        ensureDayRecords(dayStart)

        val inserted = dao.insertEventLog(
            PlanEventLogEntity(
                dayStart = dayStart,
                eventKey = eventKey,
                taskType = source.name,
                createdAt = System.currentTimeMillis()
            )
        )
        if (inserted <= 0L) return false

        val activeTemplateId = dao.getActiveTemplateId() ?: return false
        val targetItems = dao.getAutoItemsBySource(activeTemplateId, source.name)
        if (targetItems.isEmpty()) return true
        targetItems.forEach { targetItem ->
            val record = dao.getDayRecord(dayStart, targetItem.id) ?: run {
                dao.insertDayRecords(
                    listOf(
                        PlanDayRecordEntity(
                            dayStart = dayStart,
                            itemId = targetItem.id,
                            doneCount = 0,
                            isCompleted = 0,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                )
                dao.getDayRecord(dayStart, targetItem.id) ?: return@forEach
            }

            val nextDone = (record.doneCount + safeDelta).coerceAtLeast(0)
            val completed = PlanProgressRules.isCompleted(nextDone, targetItem.targetCount)
            dao.updateDayRecord(
                record.copy(
                    doneCount = nextDone,
                    isCompleted = if (completed) 1 else 0,
                    updatedAt = System.currentTimeMillis(),
                    completedAt = if (completed) record.completedAt ?: System.currentTimeMillis() else record.completedAt
                )
            )
        }
        return true
    }

    override suspend fun exportBackup(): PlanBackup {
        val templates = dao.getAllTemplatesOnce()
        val items = templates.flatMap { dao.getItemsByTemplate(it.id) }
        val records = dao.getAllDayRecordsOnce()
        val events = dao.getAllEventLogsOnce()
        return PlanBackup(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            templates = templates.map {
                PlanTemplateBackup(
                    id = it.id,
                    name = it.name,
                    isActive = it.isActive == 1,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            items = items.map {
                PlanItemBackup(
                    id = it.id,
                    templateId = it.templateId,
                    taskType = it.type,
                    title = it.title,
                    targetCount = it.targetCount,
                    autoEnabled = it.autoEnabled == 1,
                    autoSource = it.autoSource,
                    orderIndex = it.orderIndex,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            dayRecords = records.map {
                PlanDayRecordBackup(
                    id = it.id,
                    dayStart = it.dayStart,
                    itemId = it.itemId,
                    doneCount = it.doneCount,
                    isCompleted = it.isCompleted == 1,
                    updatedAt = it.updatedAt,
                    completedAt = it.completedAt
                )
            },
            eventLogs = events.map {
                PlanEventLogBackup(
                    id = it.id,
                    dayStart = it.dayStart,
                    eventKey = it.eventKey,
                    source = it.taskType,
                    createdAt = it.createdAt
                )
            }
        )
    }

    override suspend fun replaceFromBackup(backup: PlanBackup) {
        dao.deleteAllEventLogs()
        dao.deleteAllTemplates()

        val templateIdMap = linkedMapOf<Long, Long>()
        backup.templates.sortedBy { it.id }.forEach { template ->
            val insertedId = dao.insertTemplate(
                PlanTemplateEntity(
                    name = template.name,
                    isActive = if (template.isActive) 1 else 0,
                    createdAt = template.createdAt,
                    updatedAt = template.updatedAt
                )
            )
            templateIdMap[template.id] = insertedId
        }

        val itemIdMap = linkedMapOf<Long, Long>()
        backup.items.sortedBy { it.id }.forEach { item ->
            val mappedTemplateId = templateIdMap[item.templateId] ?: return@forEach
            val insertedId = dao.insertItem(
                PlanItemEntity(
                    templateId = mappedTemplateId,
                    type = item.taskType,
                    title = item.title,
                    targetCount = item.targetCount,
                    autoEnabled = if (item.autoEnabled) 1 else 0,
                    autoSource = item.autoSource,
                    orderIndex = item.orderIndex,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt
                )
            )
            itemIdMap[item.id] = insertedId
        }

        val records = backup.dayRecords.mapNotNull { record ->
            val mappedItemId = itemIdMap[record.itemId] ?: return@mapNotNull null
            PlanDayRecordEntity(
                dayStart = record.dayStart,
                itemId = mappedItemId,
                doneCount = record.doneCount,
                isCompleted = if (record.isCompleted) 1 else 0,
                updatedAt = record.updatedAt,
                completedAt = record.completedAt
            )
        }
        if (records.isNotEmpty()) {
            dao.insertDayRecords(records)
        }

        backup.eventLogs.forEach { event ->
            dao.insertEventLog(
                PlanEventLogEntity(
                    dayStart = event.dayStart,
                    eventKey = event.eventKey,
                    taskType = event.source,
                    createdAt = event.createdAt
                )
            )
        }

        if (dao.countTemplates() == 0) {
            ensureDefaultTemplate()
        } else if (dao.getActiveTemplateId() == null) {
            dao.getLatestTemplateId()?.let { fallbackId ->
                dao.activateTemplate(fallbackId, System.currentTimeMillis())
            }
        }
    }

    private fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun PlanTemplateEntity.toDomain(): PlanTemplate = PlanTemplate(
        id = id,
        name = name,
        isActive = isActive == 1,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun PlanItemEntity.toDomain(): PlanItem = PlanItem(
        id = id,
        templateId = templateId,
        taskType = runCatching { PlanTaskType.valueOf(type) }.getOrElse { PlanTaskType.CUSTOM },
        title = title,
        targetCount = targetCount,
        autoEnabled = autoEnabled == 1,
        autoSource = autoSource?.let { raw ->
            runCatching { PlanAutoSource.valueOf(raw) }.getOrNull()
        },
        orderIndex = orderIndex,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun PlanDayRecordEntity.toDomain(): PlanDayRecord = PlanDayRecord(
        id = id,
        dayStart = dayStart,
        itemId = itemId,
        doneCount = doneCount,
        isCompleted = isCompleted == 1,
        updatedAt = updatedAt,
        completedAt = completedAt
    )

    private fun PlanItem.toEntity(): PlanItemEntity = PlanItemEntity(
        id = id,
        templateId = templateId,
        type = taskType.name,
        title = title,
        targetCount = targetCount,
        autoEnabled = if (autoEnabled) 1 else 0,
        autoSource = autoSource?.name,
        orderIndex = orderIndex,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
