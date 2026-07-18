package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_edge_staging",
    indices = [
        Index(value = ["dictionary_id"]),
        Index(value = ["word_id_a", "word_id_b", "edge_type"], unique = true)
    ]
)
data class WordEdgeStagingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word_id_a") val wordIdA: Long,
    @ColumnInfo(name = "word_id_b") val wordIdB: Long,
    @ColumnInfo(name = "edge_type") val edgeType: String,
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val status: String,
    @ColumnInfo(name = "learning_value") val learningValue: Int,
    @ColumnInfo(name = "relation_strength") val relationStrength: Int,
    val confidence: Double,
    val reason: String?,
    @ColumnInfo(name = "warning_note") val warningNote: String?,
    @ColumnInfo(name = "evidence_source") val evidenceSource: String?,
    val register: String?,
    @ColumnInfo(name = "example_sentence") val exampleSentence: String?,
    @ColumnInfo(name = "difficulty_cefr") val difficultyCefr: String?
)
