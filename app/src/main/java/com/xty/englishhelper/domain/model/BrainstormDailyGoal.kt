package com.xty.englishhelper.domain.model

data class BrainstormDailyGoal(
    val date: String,
    val targetCount: Int = 200,
    val totalLearned: Int = 0,
    val dueWordsLearned: Int = 0,
    val newWordsLearned: Int = 0,
    val isCompleted: Boolean = false,
    val continuedAfterGoal: Boolean = false
)
