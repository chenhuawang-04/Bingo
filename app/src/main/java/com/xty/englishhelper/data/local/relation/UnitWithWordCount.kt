package com.xty.englishhelper.data.local.relation

import androidx.room.ColumnInfo

data class UnitWithWordCount(
    val id: Long,
    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: Long,
    @ColumnInfo(name = "unit_uid")
    val unitUid: String,
    val name: String,
    @ColumnInfo(name = "default_repeat_count")
    val defaultRepeatCount: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "word_count")
    val wordCount: Int
)
