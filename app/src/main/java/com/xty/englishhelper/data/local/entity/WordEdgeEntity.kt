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
        Index(value = ["word_id_b"]),
        Index(value = ["dictionary_id", "status"])
    ]
)
data class WordEdgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word_id_a") val wordIdA: Long,
    @ColumnInfo(name = "word_id_b") val wordIdB: Long,
    @ColumnInfo(name = "edge_type") val edgeType: String,
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "status", defaultValue = "core") val status: String = "core",
    @ColumnInfo(name = "learning_value", defaultValue = "3") val learningValue: Int = 3,
    @ColumnInfo(name = "relation_strength", defaultValue = "3") val relationStrength: Int = 3,
    @ColumnInfo(name = "confidence", defaultValue = "0.5") val confidence: Double = 0.5,
    @ColumnInfo(name = "reason") val reason: String? = null,
    @ColumnInfo(name = "warning_note") val warningNote: String? = null,
    @ColumnInfo(name = "evidence_source") val evidenceSource: String? = null,
    @ColumnInfo(name = "register") val register: String? = null,
    @ColumnInfo(name = "example_sentence") val exampleSentence: String? = null,
    @ColumnInfo(name = "difficulty_cefr") val difficultyCefr: String? = null
)
