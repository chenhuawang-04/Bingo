package com.xty.englishhelper.domain.model

data class AssociatedWordInfo(
    val wordId: Long,
    val spelling: String,
    val similarity: Float,
    val commonSegments: List<String>
)
