package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_phrase_tags",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("dictionary_id"),
        Index(value = ["dictionary_id", "tag_uid"], unique = true),
        Index(value = ["dictionary_id", "normalized_name"], unique = true)
    ]
)
data class WordPhraseTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "tag_uid")
    val tagUid: String,
    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: Long,
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    val description: String = "",
    val source: String = "AI",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "word_phrases",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("word_id"),
        Index("dictionary_id"),
        Index(value = ["dictionary_id", "phrase_uid"], unique = true),
        Index(value = ["word_id", "normalized_phrase"], unique = true)
    ]
)
data class WordPhraseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "phrase_uid")
    val phraseUid: String,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: Long,
    val phrase: String,
    @ColumnInfo(name = "normalized_phrase")
    val normalizedPhrase: String,
    val meaning: String = "",
    val example: String = "",
    @ColumnInfo(name = "usage_note")
    val usageNote: String = "",
    val register: String? = null,
    val difficulty: String? = null,
    val confidence: Float = 0.8f,
    val source: String = "AI",
    val model: String? = null,
    @ColumnInfo(name = "practice_count", defaultValue = "0")
    val practiceCount: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "organized_at")
    val organizedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "word_phrase_tag_cross_refs",
    primaryKeys = ["phrase_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordPhraseEntity::class,
            parentColumns = ["id"],
            childColumns = ["phrase_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordPhraseTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("tag_id")
    ]
)
data class WordPhraseTagCrossRef(
    @ColumnInfo(name = "phrase_id")
    val phraseId: Long,
    @ColumnInfo(name = "tag_id")
    val tagId: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "word_phrase_organize_marks",
    primaryKeys = ["word_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("dictionary_id"),
        Index("status")
    ]
)
data class WordPhraseOrganizeMarkEntity(
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: Long,
    val status: String,
    @ColumnInfo(name = "phrase_count")
    val phraseCount: Int = 0,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    val model: String? = null,
    @ColumnInfo(name = "organized_at")
    val organizedAt: Long = System.currentTimeMillis()
)
