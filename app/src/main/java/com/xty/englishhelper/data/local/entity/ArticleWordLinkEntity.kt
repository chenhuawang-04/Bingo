package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_word_links",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["article_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArticleSentenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sentence_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("article_id"),
        Index("word_id"),
        Index("dictionary_id"),
        Index("sentence_id"),
        Index(value = ["article_id", "sentence_id", "word_id"], unique = true)
    ]
)
data class ArticleWordLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "article_id")
    val articleId: Long,
    @ColumnInfo(name = "sentence_id")
    val sentenceId: Long,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: Long,
    @ColumnInfo(name = "matched_token")
    val matchedToken: String
)
