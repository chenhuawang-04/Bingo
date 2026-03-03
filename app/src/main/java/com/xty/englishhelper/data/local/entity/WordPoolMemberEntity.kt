package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "word_pool_members",
    primaryKeys = ["word_id", "pool_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordPoolEntity::class,
            parentColumns = ["id"],
            childColumns = ["pool_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pool_id"), Index("word_id")]
)
data class WordPoolMemberEntity(
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "pool_id")
    val poolId: Long
)
