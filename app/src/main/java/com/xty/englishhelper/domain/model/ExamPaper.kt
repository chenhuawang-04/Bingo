package com.xty.englishhelper.domain.model

data class ExamPaper(
    val id: Long = 0,
    val uid: String,
    val title: String,
    val description: String? = null,
    val totalQuestions: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
