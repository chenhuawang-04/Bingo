package com.xty.englishhelper.domain.model

enum class ArticleSourceType {
    MANUAL, AI
}

enum class ArticleParseStatus {
    PENDING, PROCESSING, DONE, FAILED
}

data class Article(
    val id: Long = 0,
    val title: String,
    val content: String,
    val domain: String = "",
    val difficultyAi: Float = 0f,
    val difficultyLocal: Float = 0f,
    val difficultyFinal: Float = 0f,
    val sourceType: ArticleSourceType = ArticleSourceType.MANUAL,
    val parseStatus: ArticleParseStatus = ArticleParseStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
