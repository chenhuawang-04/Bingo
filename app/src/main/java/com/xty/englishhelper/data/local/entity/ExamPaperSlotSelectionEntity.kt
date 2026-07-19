package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exam_paper_slot_selections",
    foreignKeys = [
        ForeignKey(
            entity = ExamPaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_paper_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["exam_paper_id", "slot_key"], unique = true),
        Index("article_id"),
        Index(value = ["status", "updated_at"])
    ]
)
data class ExamPaperSlotSelectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "exam_paper_id")
    val examPaperId: Long,
    @ColumnInfo(name = "slot_key")
    val slotKey: String,
    @ColumnInfo(name = "question_type")
    val questionType: String,
    val variant: String = "",
    val status: String,
    @ColumnInfo(name = "article_id")
    val articleId: Long? = null,
    @ColumnInfo(name = "article_uid")
    val articleUid: String? = null,
    @ColumnInfo(name = "article_title")
    val articleTitle: String? = null,
    @ColumnInfo(name = "selected_score")
    val selectedScore: Int? = null,
    @ColumnInfo(name = "candidate_count")
    val candidateCount: Int = 0,
    val reason: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
