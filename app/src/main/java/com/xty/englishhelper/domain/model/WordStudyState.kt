package com.xty.englishhelper.domain.model

data class WordStudyState(
    val wordId: Long,
    val remainingReviews: Int,
    val easeLevel: Int = 0,
    val nextReviewAt: Long = 0,
    val lastReviewedAt: Long = 0
)
