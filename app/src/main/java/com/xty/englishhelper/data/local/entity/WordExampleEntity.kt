package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_examples",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("word_id"),
        Index(value = ["word_id", "source_type", "source_article_id", "source_sentence_id"], unique = true)
    ]
)
data class WordExampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    val sentence: String,
    @ColumnInfo(name = "source_type")
    val sourceType: Int = 0,
    @ColumnInfo(name = "source_article_id")
    val sourceArticleId: Long? = null,
    @ColumnInfo(name = "source_sentence_id")
    val sourceSentenceId: Long? = null,
    @ColumnInfo(name = "source_label")
    val sourceLabel: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
