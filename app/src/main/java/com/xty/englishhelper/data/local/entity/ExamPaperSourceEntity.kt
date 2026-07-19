package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exam_paper_sources",
    foreignKeys = [
        ForeignKey(
            entity = ExamPaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_paper_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["exam_paper_id", "slot_key"], unique = true),
        Index("article_id"),
        Index("question_group_id")
    ]
)
data class ExamPaperSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uid: String,
    @ColumnInfo(name = "exam_paper_id")
    val examPaperId: Long,
    @ColumnInfo(name = "article_id")
    val articleId: Long,
    @ColumnInfo(name = "article_uid")
    val articleUid: String,
    @ColumnInfo(name = "slot_key")
    val slotKey: String,
    @ColumnInfo(name = "question_type")
    val questionType: String,
    val variant: String? = null,
    @ColumnInfo(name = "order_in_paper")
    val orderInPaper: Int,
    @ColumnInfo(name = "start_question_number")
    val startQuestionNumber: Int,
    val status: String,
    @ColumnInfo(name = "question_group_id")
    val questionGroupId: Long? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
