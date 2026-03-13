package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "question_groups",
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
        Index("exam_paper_id")
    ]
)
data class QuestionGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uid: String,
    @ColumnInfo(name = "exam_paper_id")
    val examPaperId: Long,
    @ColumnInfo(name = "question_type")
    val questionType: String,
    @ColumnInfo(name = "section_label")
    val sectionLabel: String? = null,
    @ColumnInfo(name = "order_in_paper")
    val orderInPaper: Int = 0,
    val directions: String? = null,
    @ColumnInfo(name = "passage_text", defaultValue = "")
    val passageText: String = "",
    @ColumnInfo(name = "source_info")
    val sourceInfo: String? = null,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    @ColumnInfo(name = "source_author")
    val sourceAuthor: String? = null,
    @ColumnInfo(name = "source_verified", defaultValue = "0")
    val sourceVerified: Int = 0,
    @ColumnInfo(name = "source_verify_error")
    val sourceVerifyError: String? = null,
    @ColumnInfo(name = "word_count", defaultValue = "0")
    val wordCount: Int = 0,
    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: String? = null,
    @ColumnInfo(name = "difficulty_score")
    val difficultyScore: Float? = null,
    @ColumnInfo(name = "has_ai_answer", defaultValue = "0")
    val hasAiAnswer: Int = 0,
    @ColumnInfo(name = "has_scanned_answer", defaultValue = "0")
    val hasScannedAnswer: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
