package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_notifications",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["event_key"], unique = true),
        Index(value = ["is_read", "created_at"]),
        Index("source_task_id")
    ]
)
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uid: String,
    @ColumnInfo(name = "event_key")
    val eventKey: String,
    val category: String,
    val title: String,
    val message: String,
    @ColumnInfo(name = "target_type")
    val targetType: String,
    @ColumnInfo(name = "target_id")
    val targetId: Long? = null,
    @ColumnInfo(name = "target_aux")
    val targetAux: String? = null,
    @ColumnInfo(name = "source_task_id")
    val sourceTaskId: Long? = null,
    @ColumnInfo(name = "source_task_type")
    val sourceTaskType: String? = null,
    @ColumnInfo(name = "source_task_status")
    val sourceTaskStatus: String? = null,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "read_at")
    val readAt: Long? = null
)
