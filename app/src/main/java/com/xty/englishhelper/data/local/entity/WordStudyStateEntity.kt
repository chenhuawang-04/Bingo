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
    @ColumnInfo(name = "remaining_reviews")
    val remainingReviews: Int,
    @ColumnInfo(name = "ease_level", defaultValue = "0")
    val easeLevel: Int = 0,
    @ColumnInfo(name = "next_review_at", defaultValue = "0")
    val nextReviewAt: Long = 0,
    @ColumnInfo(name = "last_reviewed_at", defaultValue = "0")
    val lastReviewedAt: Long = 0
)
