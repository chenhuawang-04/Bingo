package com.xty.englishhelper.domain.model

enum class ParagraphType {
    TEXT, IMAGE, QUOTE, HEADING, LIST
}

data class ArticleParagraph(
    val id: Long = 0,
    val articleId: Long = 0,
    val paragraphIndex: Int = 0,
    val text: String = "",
    val imageUri: String? = null,
    val imageUrl: String? = null,
    val paragraphType: ParagraphType = ParagraphType.TEXT
)
