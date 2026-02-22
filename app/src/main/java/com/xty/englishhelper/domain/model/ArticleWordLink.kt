package com.xty.englishhelper.domain.model

data class ArticleWordLink(
    val id: Long = 0,
    val articleId: Long,
    val sentenceId: Long,
    val wordId: Long,
    val dictionaryId: Long,
    val matchedToken: String
)
