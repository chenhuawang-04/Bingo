package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plan_items",
    foreignKeys = [
        ForeignKey(
            entity = PlanTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["template_id"]),
        Index(value = ["template_id", "order_index"])
    ]
)
data class PlanItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "template_id")
    val templateId: Long,
    val type: String,
    val title: String,
    @ColumnInfo(name = "target_count")
    val targetCount: Int = 1,
    @ColumnInfo(name = "auto_enabled")
    val autoEnabled: Int = 0,
    @ColumnInfo(name = "auto_source")
    val autoSource: String? = null,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
