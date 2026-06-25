package com.xty.englishhelper.domain.model

import java.util.UUID

data class StudyUnit(
    val id: Long = 0,
    val dictionaryId: Long,
    val unitUid: String = UUID.randomUUID().toString(),
    val name: String,
    val defaultRepeatCount: Int = 2,
    val wordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
