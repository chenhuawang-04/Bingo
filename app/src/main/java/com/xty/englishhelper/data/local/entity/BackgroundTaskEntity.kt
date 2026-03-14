package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "background_tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["type"]),
        Index(value = ["dedupe_key"])
    ]
)
data class BackgroundTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val status: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "progress_current")
    val progressCurrent: Int = 0,
    @ColumnInfo(name = "progress_total")
    val progressTotal: Int = 0,
    val attempt: Int = 0,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "dedupe_key")
    val dedupeKey: String
)
