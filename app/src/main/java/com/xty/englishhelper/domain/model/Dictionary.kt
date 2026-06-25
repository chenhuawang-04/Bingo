package com.xty.englishhelper.domain.model

import java.util.UUID

data class Dictionary(
    val id: Long = 0,
    val dictionaryUid: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val color: Int = 0xFF4A6FA5.toInt(),
    val wordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
