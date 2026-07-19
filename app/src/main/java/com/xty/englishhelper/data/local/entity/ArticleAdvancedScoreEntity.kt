package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_advanced_scores",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["article_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("article_id"),
        Index(value = ["article_id", "question_type", "variant"], unique = true),
        Index(value = ["question_type", "variant", "score"]),
        Index("updated_at")
    ]
)
data class ArticleAdvancedScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "article_id")
    val articleId: Long,
    @ColumnInfo(name = "article_uid")
    val articleUid: String,
    @ColumnInfo(name = "question_type")
    val questionType: String,
    val variant: String = "",
    val score: Int,
    val reason: String,
    @ColumnInfo(name = "basic_score")
    val basicScore: Int,
    @ColumnInfo(name = "word_count")
    val wordCount: Int,
    @ColumnInfo(name = "model_key")
    val modelKey: String,
    @ColumnInfo(name = "prompt_version")
    val promptVersion: Int,
    @ColumnInfo(name = "scored_at")
    val scoredAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
