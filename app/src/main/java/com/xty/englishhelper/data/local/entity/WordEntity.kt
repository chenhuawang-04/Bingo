package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
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
        Index("spelling"),
        Index(value = ["dictionary_id", "normalized_spelling"], unique = true)
    ]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: Long,
    val spelling: String,
    val phonetic: String = "",
    @ColumnInfo(name = "meanings_json")
    val meaningsJson: String = "[]",
    @ColumnInfo(name = "root_explanation")
    val rootExplanation: String = "",
    @ColumnInfo(name = "normalized_spelling")
    val normalizedSpelling: String = "",
    @ColumnInfo(name = "word_uid")
    val wordUid: String = "",
    @ColumnInfo(name = "decomposition_json")
    val decompositionJson: String = "[]",
    @ColumnInfo(name = "inflections_json")
    val inflectionsJson: String = "[]",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
