package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "paragraph_analysis_cache",
    indices = [
        Index(
            value = ["article_id", "paragraph_id", "paragraph_hash", "model_key"],
            unique = true
        )
    ]
)
data class ParagraphAnalysisCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "article_id")
    val articleId: Long,
    @ColumnInfo(name = "paragraph_id")
    val paragraphId: Long,
    @ColumnInfo(name = "paragraph_hash")
    val paragraphHash: String,
    @ColumnInfo(name = "model_key")
    val modelKey: String,
    @ColumnInfo(name = "meaning_zh")
    val meaningZh: String,
    @ColumnInfo(name = "grammar_json")
    val grammarJson: String,
    @ColumnInfo(name = "keywords_json")
    val keywordsJson: String,
    @ColumnInfo(name = "breakdowns_json")
    val breakdownsJson: String = "[]",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
