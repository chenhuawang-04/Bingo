package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_study_state",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WordStudyStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "state", defaultValue = "2")
    val state: Int = 2,
    @ColumnInfo(name = "step")
    val step: Int? = null,
    @ColumnInfo(name = "stability", defaultValue = "0.0")
    val stability: Double = 0.0,
    @ColumnInfo(name = "difficulty", defaultValue = "0.0")
    val difficulty: Double = 0.0,
    @ColumnInfo(name = "due", defaultValue = "0")
    val due: Long = 0,
    @ColumnInfo(name = "last_review_at", defaultValue = "0")
    val lastReviewAt: Long = 0,
    @ColumnInfo(name = "reps", defaultValue = "0")
    val reps: Int = 0,
    @ColumnInfo(name = "lapses", defaultValue = "0")
    val lapses: Int = 0
)
