package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "practice_records",
    foreignKeys = [
        ForeignKey(
            entity = QuestionItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["question_item_id", "practiced_at"])
    ]
)
data class PracticeRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "question_item_id")
    val questionItemId: Long,
    @ColumnInfo(name = "user_answer")
    val userAnswer: String,
    @ColumnInfo(name = "is_correct")
    val isCorrect: Int,
    @ColumnInfo(name = "practiced_at")
    val practicedAt: Long
)
