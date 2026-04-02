package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plan_event_logs",
    indices = [
        Index(value = ["day_start", "event_key"], unique = true)
    ]
)
data class PlanEventLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "day_start")
    val dayStart: Long,
    @ColumnInfo(name = "event_key")
    val eventKey: String,
    @ColumnInfo(name = "task_type")
    val taskType: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
