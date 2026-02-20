package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "word_associations",
    primaryKeys = ["word_id", "associated_word_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["associated_word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("word_id"), Index("associated_word_id")]
)
data class WordAssociationEntity(
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "associated_word_id") val associatedWordId: Long,
    val similarity: Float,
    @ColumnInfo(name = "common_segments_json") val commonSegmentsJson: String = "[]"
)
