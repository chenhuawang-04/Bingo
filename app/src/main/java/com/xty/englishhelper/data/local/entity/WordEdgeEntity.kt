package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_edges",
    indices = [
        Index(value = ["word_id_a", "word_id_b", "edge_type"], unique = true),
        Index(value = ["dictionary_id"]),
        Index(value = ["word_id_a"]),
        Index(value = ["word_id_b"])
    ]
)
data class WordEdgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word_id_a") val wordIdA: Long,
    @ColumnInfo(name = "word_id_b") val wordIdB: Long,
    @ColumnInfo(name = "edge_type") val edgeType: String,
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
