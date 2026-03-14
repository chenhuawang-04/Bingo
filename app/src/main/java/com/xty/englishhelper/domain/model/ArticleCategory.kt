package com.xty.englishhelper.domain.model

data class ArticleCategory(
    val id: Long = 0,
    val name: String,
    val isSystem: Boolean = false
)

object ArticleCategoryDefaults {
    const val DEFAULT_ID: Long = 1L
    const val SOURCE_ID: Long = 2L
    const val DEFAULT_NAME: String = "普通文章"
    const val SOURCE_NAME: String = "题目来源"
}
