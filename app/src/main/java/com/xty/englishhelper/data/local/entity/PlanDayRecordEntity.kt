package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plan_day_records",
    foreignKeys = [
        ForeignKey(
            entity = PlanItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["day_start"]),
        Index(value = ["item_id"]),
        Index(value = ["day_start", "item_id"], unique = true)
    ]
)
data class PlanDayRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "day_start")
    val dayStart: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "done_count")
    val doneCount: Int = 0,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
)
