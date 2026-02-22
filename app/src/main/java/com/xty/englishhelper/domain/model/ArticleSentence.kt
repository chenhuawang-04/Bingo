package com.xty.englishhelper.domain.model

data class ArticleSentence(
    val id: Long = 0,
    val articleId: Long,
    val sentenceIndex: Int,
    val text: String,
    val charStart: Int,
    val charEnd: Int
)
