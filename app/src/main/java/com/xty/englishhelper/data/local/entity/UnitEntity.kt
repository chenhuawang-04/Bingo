package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "units",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("dictionary_id")]
)
data class UnitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: Long,
    val name: String,
    @ColumnInfo(name = "default_repeat_count", defaultValue = "2")
    val defaultRepeatCount: Int = 2,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
