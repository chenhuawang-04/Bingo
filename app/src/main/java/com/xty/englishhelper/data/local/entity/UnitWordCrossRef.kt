package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "unit_word_cross_ref",
    primaryKeys = ["unit_id", "word_id"],
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("unit_id"), Index("word_id")]
)
data class UnitWordCrossRef(
    @ColumnInfo(name = "unit_id")
    val unitId: Long,
    @ColumnInfo(name = "word_id")
    val wordId: Long
)
