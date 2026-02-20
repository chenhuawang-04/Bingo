package com.xty.englishhelper.domain.model

data class WordStudyState(
    val wordId: Long,
    val state: Int = 2,            // CardState.Review.value
    val step: Int? = null,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0,
    val lastReviewAt: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0
)
