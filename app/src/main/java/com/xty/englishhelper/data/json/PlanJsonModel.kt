package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlanExportJsonModel(
    val schemaVersion: Int = 1,
    val exportedAt: Long = 0,
    val templates: List<PlanTemplateJsonModel> = emptyList(),
    val items: List<PlanItemJsonModel> = emptyList(),
    val dayRecords: List<PlanDayRecordJsonModel> = emptyList(),
    val eventLogs: List<PlanEventLogJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PlanTemplateJsonModel(
    val id: Long = 0,
    val name: String = "",
    val isActive: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@JsonClass(generateAdapter = true)
data class PlanItemJsonModel(
    val id: Long = 0,
    val templateId: Long = 0,
    val taskType: String = "",
    val title: String = "",
    val targetCount: Int = 1,
    val autoEnabled: Boolean = false,
    val autoSource: String? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@JsonClass(generateAdapter = true)
data class PlanDayRecordJsonModel(
    val id: Long = 0,
    val dayStart: Long = 0,
    val itemId: Long = 0,
    val doneCount: Int = 0,
    val isCompleted: Boolean = false,
    val updatedAt: Long = 0,
    val completedAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class PlanEventLogJsonModel(
    val id: Long = 0,
    val dayStart: Long = 0,
    val eventKey: String = "",
    val source: String = "",
    val createdAt: Long = 0
)
