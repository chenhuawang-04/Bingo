package com.xty.englishhelper.domain.model

data class StudyUnit(
    val id: Long = 0,
    val dictionaryId: Long,
    val name: String,
    val defaultRepeatCount: Int = 2,
    val wordCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
