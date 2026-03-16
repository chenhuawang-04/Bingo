package com.xty.englishhelper.domain.model

enum class ArticleSourceType {
    MANUAL, AI
}

enum class ArticleSourceTypeV2 {
    LOCAL, ONLINE
}

enum class ArticleParseStatus {
    PENDING, PROCESSING, DONE, FAILED
}

data class Article(
    val id: Long = 0,
    val articleUid: String = "",
    val title: String,
    val content: String,
    val domain: String = "",
    val difficultyAi: Float = 0f,
    val difficultyLocal: Float = 0f,
    val difficultyFinal: Float = 0f,
    val sourceType: ArticleSourceType = ArticleSourceType.MANUAL,
    val parseStatus: ArticleParseStatus = ArticleParseStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val summary: String = "",
    val author: String = "",
    val source: String = "",
    val coverImageUri: String? = null,
    val coverImageUrl: String? = null,
    val wordCount: Int = 0,
    val isSaved: Boolean = true,
    val categoryId: Long = ArticleCategoryDefaults.DEFAULT_ID,
    val sourceTypeV2: ArticleSourceTypeV2 = ArticleSourceTypeV2.LOCAL,
    val suitabilityScore: Int? = null,
    val suitabilityReason: String = "",
    val suitabilityUpdatedAt: Long? = null,
    val suitabilityModel: String = ""
)
