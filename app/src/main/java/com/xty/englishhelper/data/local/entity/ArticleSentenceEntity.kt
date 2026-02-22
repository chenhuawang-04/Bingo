package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_sentences",
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
        Index(value = ["article_id", "sentence_index"], unique = true)
    ]
)
data class ArticleSentenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "article_id")
    val articleId: Long,
    @ColumnInfo(name = "sentence_index")
    val sentenceIndex: Int,
    val text: String,
    @ColumnInfo(name = "char_start")
    val charStart: Int,
    @ColumnInfo(name = "char_end")
    val charEnd: Int
)
