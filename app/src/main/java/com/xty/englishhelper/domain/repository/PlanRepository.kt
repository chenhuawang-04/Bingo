package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.PlanDaySummary
import com.xty.englishhelper.domain.model.PlanAutoEventLog
import com.xty.englishhelper.domain.model.PlanItem
import com.xty.englishhelper.domain.model.PlanTaskProgress
import com.xty.englishhelper.domain.model.PlanAutoSource
import com.xty.englishhelper.domain.model.PlanStatsMode
import com.xty.englishhelper.domain.model.PlanTaskType
import com.xty.englishhelper.domain.model.PlanTemplate
import com.xty.englishhelper.domain.model.PlanTypeSummary
import com.xty.englishhelper.domain.model.PlanBackup
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    fun observeTemplates(): Flow<List<PlanTemplate>>
    fun observeActiveTemplate(): Flow<PlanTemplate?>
    fun observeItemsByTemplate(templateId: Long): Flow<List<PlanItem>>
    fun observeTodayTasks(dayStart: Long): Flow<List<PlanTaskProgress>>
    fun observeTodayEventLogs(dayStart: Long, limit: Int): Flow<List<PlanAutoEventLog>>
    fun observeDaySummaries(limitDays: Int, mode: PlanStatsMode = PlanStatsMode.ALL): Flow<List<PlanDaySummary>>
    fun observeTypeSummaries(limitDays: Int, mode: PlanStatsMode = PlanStatsMode.ALL): Flow<List<PlanTypeSummary>>

    suspend fun ensureDefaultTemplate()
    suspend fun setActiveTemplate(templateId: Long)
    suspend fun createTemplate(name: String): Long
    suspend fun renameTemplate(templateId: Long, name: String)
    suspend fun deleteTemplate(templateId: Long)

    suspend fun addItem(item: PlanItem): Long
    suspend fun updateItem(item: PlanItem)
    suspend fun deleteItem(itemId: Long)

    suspend fun ensureDayRecords(dayStart: Long)
    suspend fun updateTaskProgress(dayStart: Long, itemId: Long, doneCount: Int)
    suspend fun setTaskCompleted(dayStart: Long, itemId: Long, completed: Boolean)
    suspend fun consumeAutoProgress(source: PlanAutoSource, eventKey: String, delta: Int = 1): Boolean
    suspend fun exportBackup(): PlanBackup
    suspend fun replaceFromBackup(backup: PlanBackup)
}
