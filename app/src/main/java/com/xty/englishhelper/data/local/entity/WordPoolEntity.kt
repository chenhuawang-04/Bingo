package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_pools",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["focus_word_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("dictionary_id"), Index("focus_word_id")]
)
data class WordPoolEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: Long,
    @ColumnInfo(name = "focus_word_id")
    val focusWordId: Long? = null,
    val strategy: String,
    @ColumnInfo(name = "algorithm_version")
    val algorithmVersion: String
)
