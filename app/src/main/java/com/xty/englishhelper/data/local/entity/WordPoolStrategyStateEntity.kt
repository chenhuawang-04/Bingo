package com.xty.englishhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "word_pool_strategy_states",
    primaryKeys = ["dictionary_id", "strategy"]
)
data class WordPoolStrategyStateEntity(
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    val strategy: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
