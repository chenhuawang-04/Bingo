package com.xty.englishhelper.domain.model

data class ArticleWordStat(
    val id: Long = 0,
    val articleId: Long,
    val normalizedToken: String,
    val displayToken: String,
    val frequency: Int
)
