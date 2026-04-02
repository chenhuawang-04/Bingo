package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.xty.englishhelper.data.local.entity.PlanDayRecordEntity
import com.xty.englishhelper.data.local.entity.PlanEventLogEntity
import com.xty.englishhelper.data.local.entity.PlanItemEntity
import com.xty.englishhelper.data.local.entity.PlanTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    @Query("SELECT * FROM plan_templates ORDER BY updated_at DESC, id DESC")
    fun observeTemplates(): Flow<List<PlanTemplateEntity>>

    @Query("SELECT * FROM plan_templates ORDER BY id ASC")
    suspend fun getAllTemplatesOnce(): List<PlanTemplateEntity>

    @Query("SELECT * FROM plan_templates WHERE is_active = 1 ORDER BY updated_at DESC LIMIT 1")
    fun observeActiveTemplate(): Flow<PlanTemplateEntity?>

    @Query("SELECT * FROM plan_templates WHERE id = :templateId LIMIT 1")
    suspend fun getTemplateById(templateId: Long): PlanTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTemplate(entity: PlanTemplateEntity): Long

    @Update
    suspend fun updateTemplate(entity: PlanTemplateEntity)

    @Query("UPDATE plan_templates SET is_active = 0")
    suspend fun clearActiveTemplate()

    @Query("UPDATE plan_templates SET is_active = 1, updated_at = :updatedAt WHERE id = :templateId")
    suspend fun setTemplateActive(templateId: Long, updatedAt: Long)

    @Query("DELETE FROM plan_templates WHERE id = :templateId")
    suspend fun deleteTemplate(templateId: Long)

    @Query("SELECT COUNT(*) FROM plan_templates")
    suspend fun countTemplates(): Int

    @Query("SELECT id FROM plan_templates ORDER BY updated_at DESC, id DESC LIMIT 1")
    suspend fun getLatestTemplateId(): Long?

    @Query("SELECT * FROM plan_items WHERE template_id = :templateId ORDER BY order_index ASC, id ASC")
    fun observeItemsByTemplate(templateId: Long): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_items WHERE template_id = :templateId ORDER BY order_index ASC, id ASC")
    suspend fun getItemsByTemplate(templateId: Long): List<PlanItemEntity>

    @Query("SELECT * FROM plan_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): PlanItemEntity?

    @Query(
        "SELECT * FROM plan_items WHERE template_id = :templateId AND auto_enabled = 1 AND auto_source = :autoSource ORDER BY order_index ASC, id ASC"
    )
    suspend fun getAutoItemsBySource(templateId: Long, autoSource: String): List<PlanItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(entity: PlanItemEntity): Long

    @Update
    suspend fun updateItem(entity: PlanItemEntity)

    @Query("DELETE FROM plan_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDayRecords(entities: List<PlanDayRecordEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventLog(entity: PlanEventLogEntity): Long

    @Query("SELECT * FROM plan_event_logs ORDER BY id ASC")
    suspend fun getAllEventLogsOnce(): List<PlanEventLogEntity>

    @Query("SELECT * FROM plan_event_logs WHERE day_start = :dayStart ORDER BY created_at DESC LIMIT :limit")
    fun observeEventLogs(dayStart: Long, limit: Int): Flow<List<PlanEventLogEntity>>

    @Query("SELECT * FROM plan_day_records WHERE day_start = :dayStart ORDER BY id ASC")
    fun observeDayRecords(dayStart: Long): Flow<List<PlanDayRecordEntity>>

    @Query("SELECT * FROM plan_day_records ORDER BY id ASC")
    suspend fun getAllDayRecordsOnce(): List<PlanDayRecordEntity>

    @Query("SELECT * FROM plan_day_records WHERE day_start = :dayStart AND item_id = :itemId LIMIT 1")
    suspend fun getDayRecord(dayStart: Long, itemId: Long): PlanDayRecordEntity?

    @Update
    suspend fun updateDayRecord(entity: PlanDayRecordEntity)

    @Query(
        """
        SELECT day_start AS dayStart,
               SUM(CASE WHEN is_completed = 1 THEN 1 ELSE 0 END) AS completedCount,
               COUNT(*) AS totalCount
        FROM plan_day_records
        GROUP BY day_start
        ORDER BY day_start DESC
        LIMIT :limitDays
        """
    )
    fun observeDaySummaries(limitDays: Int): Flow<List<PlanDaySummaryRow>>

    @Query(
        """
        SELECT r.day_start AS dayStart,
               SUM(CASE WHEN r.is_completed = 1 THEN 1 ELSE 0 END) AS completedCount,
               COUNT(*) AS totalCount
        FROM plan_day_records r
        INNER JOIN plan_items i ON i.id = r.item_id
        WHERE i.auto_enabled = :autoEnabled
        GROUP BY r.day_start
        ORDER BY r.day_start DESC
        LIMIT :limitDays
        """
    )
    fun observeDaySummariesByAuto(limitDays: Int, autoEnabled: Int): Flow<List<PlanDaySummaryRow>>

    @Query(
        """
        SELECT i.type AS taskType,
               SUM(CASE WHEN r.is_completed = 1 THEN 1 ELSE 0 END) AS completedCount,
               COUNT(*) AS totalCount
        FROM plan_day_records r
        INNER JOIN plan_items i ON i.id = r.item_id
        WHERE r.day_start >= :fromDayStart
        GROUP BY i.type
        ORDER BY totalCount DESC, taskType ASC
        """
    )
    fun observeTypeSummaries(fromDayStart: Long): Flow<List<PlanTypeSummaryRow>>

    @Query(
        """
        SELECT i.type AS taskType,
               SUM(CASE WHEN r.is_completed = 1 THEN 1 ELSE 0 END) AS completedCount,
               COUNT(*) AS totalCount
        FROM plan_day_records r
        INNER JOIN plan_items i ON i.id = r.item_id
        WHERE r.day_start >= :fromDayStart AND i.auto_enabled = :autoEnabled
        GROUP BY i.type
        ORDER BY totalCount DESC, taskType ASC
        """
    )
    fun observeTypeSummariesByAuto(fromDayStart: Long, autoEnabled: Int): Flow<List<PlanTypeSummaryRow>>

    @Query("SELECT id FROM plan_templates WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveTemplateId(): Long?

    @Query("DELETE FROM plan_event_logs")
    suspend fun deleteAllEventLogs()

    @Query("DELETE FROM plan_templates")
    suspend fun deleteAllTemplates()

    @Transaction
    suspend fun activateTemplate(templateId: Long, updatedAt: Long) {
        clearActiveTemplate()
        setTemplateActive(templateId, updatedAt)
    }
}

data class PlanDaySummaryRow(
    val dayStart: Long,
    val completedCount: Int,
    val totalCount: Int
)

data class PlanTypeSummaryRow(
    val taskType: String,
    val completedCount: Int,
    val totalCount: Int
)
