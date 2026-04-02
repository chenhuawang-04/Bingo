package com.xty.englishhelper.domain.model

data class PlanBackup(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val templates: List<PlanTemplateBackup> = emptyList(),
    val items: List<PlanItemBackup> = emptyList(),
    val dayRecords: List<PlanDayRecordBackup> = emptyList(),
    val eventLogs: List<PlanEventLogBackup> = emptyList()
) {
    val latestUpdatedAt: Long
        get() {
            var latest = 0L
            templates.forEach { latest = maxOf(latest, it.updatedAt) }
            items.forEach { latest = maxOf(latest, it.updatedAt) }
            dayRecords.forEach { latest = maxOf(latest, it.updatedAt) }
            eventLogs.forEach { latest = maxOf(latest, it.createdAt) }
            return latest
        }

    fun isEmpty(): Boolean = templates.isEmpty() && items.isEmpty() && dayRecords.isEmpty() && eventLogs.isEmpty()
}

data class PlanTemplateBackup(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class PlanItemBackup(
    val id: Long,
    val templateId: Long,
    val taskType: String,
    val title: String,
    val targetCount: Int,
    val autoEnabled: Boolean,
    val autoSource: String?,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class PlanDayRecordBackup(
    val id: Long,
    val dayStart: Long,
    val itemId: Long,
    val doneCount: Int,
    val isCompleted: Boolean,
    val updatedAt: Long,
    val completedAt: Long?
)

data class PlanEventLogBackup(
    val id: Long,
    val dayStart: Long,
    val eventKey: String,
    val source: String,
    val createdAt: Long
)
