package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "exam_paper_answer_drafts",
    primaryKeys = ["exam_paper_id", "question_item_id"],
    foreignKeys = [
        ForeignKey(
            entity = ExamPaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_paper_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = QuestionItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("question_item_id")]
)
data class ExamPaperAnswerDraftEntity(
    @ColumnInfo(name = "exam_paper_id")
    val examPaperId: Long,
    @ColumnInfo(name = "question_item_id")
    val questionItemId: Long,
    @ColumnInfo(name = "user_answer")
    val userAnswer: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "exam_paper_practice_progress",
    primaryKeys = ["exam_paper_id", "question_group_id"],
    foreignKeys = [
        ForeignKey(
            entity = ExamPaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_paper_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = QuestionGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("question_group_id")]
)
data class ExamPaperPracticeProgressEntity(
    @ColumnInfo(name = "exam_paper_id")
    val examPaperId: Long,
    @ColumnInfo(name = "question_group_id")
    val questionGroupId: Long,
    @ColumnInfo(name = "submitted_at")
    val submittedAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
