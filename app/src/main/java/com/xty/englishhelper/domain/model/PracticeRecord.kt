package com.xty.englishhelper.domain.model

data class PracticeRecord(
    val id: Long = 0,
    val questionItemId: Long,
    val userAnswer: String,
    val isCorrect: Boolean,
    val practicedAt: Long
)
