package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exam_papers",
    indices = [
        Index(value = ["uid"], unique = true),
        Index("day_key"),
        Index(value = ["day_key", "daily_sequence"], unique = true)
    ]
)
data class ExamPaperEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uid: String,
    val title: String,
    val description: String? = null,
    @ColumnInfo(name = "total_questions")
    val totalQuestions: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "paper_type", defaultValue = "IMPORTED")
    val paperType: String = "IMPORTED",
    @ColumnInfo(defaultValue = "READY_TO_PRACTICE")
    val status: String = "READY_TO_PRACTICE",
    @ColumnInfo(name = "day_key")
    val dayKey: String? = null,
    @ColumnInfo(name = "daily_sequence", defaultValue = "0")
    val dailySequence: Int = 0,
    @ColumnInfo(defaultValue = "ENGLISH_ONE")
    val profile: String = "ENGLISH_ONE",
    @ColumnInfo(name = "blueprint_version", defaultValue = "1")
    val blueprintVersion: Int = 1,
    @ColumnInfo(name = "special_question_type")
    val specialQuestionType: String? = null,
    @ColumnInfo(name = "composition_mode", defaultValue = "MANUAL")
    val compositionMode: String = "MANUAL",
    @ColumnInfo(name = "selection_status", defaultValue = "NOT_STARTED")
    val selectionStatus: String = "NOT_STARTED",
    @ColumnInfo(name = "selection_error")
    val selectionError: String? = null,
    @ColumnInfo(name = "selection_started_at")
    val selectionStartedAt: Long? = null,
    @ColumnInfo(name = "selection_completed_at")
    val selectionCompletedAt: Long? = null,
    @ColumnInfo(name = "generation_error")
    val generationError: String? = null,
    @ColumnInfo(name = "generation_started_at")
    val generationStartedAt: Long? = null,
    @ColumnInfo(name = "generation_completed_at")
    val generationCompletedAt: Long? = null
)
