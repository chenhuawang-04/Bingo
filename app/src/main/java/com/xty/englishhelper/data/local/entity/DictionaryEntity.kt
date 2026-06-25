package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "dictionaries",
    indices = [
        Index(value = ["dictionary_uid"], unique = true)
    ]
)
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "dictionary_uid")
    val dictionaryUid: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val color: Int = 0xFF4A6FA5.toInt(),
    @ColumnInfo(name = "word_count")
    val wordCount: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = createdAt
)
