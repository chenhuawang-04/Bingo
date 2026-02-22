package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sentence_analysis_cache",
    indices = [
        Index(value = ["article_id", "sentence_id", "sentence_hash"], unique = true)
    ]
)
data class SentenceAnalysisCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "article_id")
    val articleId: Long,
    @ColumnInfo(name = "sentence_id")
    val sentenceId: Long,
    @ColumnInfo(name = "sentence_hash")
    val sentenceHash: String,
    @ColumnInfo(name = "meaning_zh")
    val meaningZh: String,
    @ColumnInfo(name = "grammar_json")
    val grammarJson: String,
    @ColumnInfo(name = "keywords_json")
    val keywordsJson: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
