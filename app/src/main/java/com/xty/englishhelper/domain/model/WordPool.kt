package com.xty.englishhelper.domain.model

data class WordPool(
    val poolId: Long,
    val focusWordId: Long?,
    val strategy: String,
    val algorithmVersion: String,
    val members: List<WordDetails>
)
