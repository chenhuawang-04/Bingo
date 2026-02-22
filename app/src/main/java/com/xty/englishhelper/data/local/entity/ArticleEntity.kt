package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index("updated_at"),
        Index("title")
    ]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val domain: String = "",
    @ColumnInfo(name = "difficulty_ai")
    val difficultyAi: Float = 0f,
    @ColumnInfo(name = "difficulty_local")
    val difficultyLocal: Float = 0f,
    @ColumnInfo(name = "difficulty_final")
    val difficultyFinal: Float = 0f,
    @ColumnInfo(name = "source_type")
    val sourceType: Int = 1,
    @ColumnInfo(name = "parse_status")
    val parseStatus: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
