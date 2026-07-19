package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ArticlesExportModel(
    val schemaVersion: Int = 1,
    val articles: List<ArticleJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArticleJsonModel(
    val articleUid: String = "",
    val title: String = "",
    val content: String = "",
    val domain: String = "",
    val difficultyAi: Float = 0f,
    val sourceType: String = "MANUAL",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val summary: String = "",
    val author: String = "",
    val source: String = "",
    val coverImageUri: String? = null,
    val coverImageUrl: String? = null,
    val wordCount: Int = 0,
    val categoryId: Long = com.xty.englishhelper.domain.model.ArticleCategoryDefaults.DEFAULT_ID,
    val difficultyLocal: Float = 0f,
    val difficultyFinal: Float = 0f,
    val sourceTypeV2: String = "LOCAL",
    val isSaved: Boolean = true,
    val suitabilityScore: Int? = null,
    val suitabilityReason: String = "",
    val suitabilityUpdatedAt: Long? = null,
    val suitabilityModel: String = "",
    val advancedScores: List<ArticleAdvancedScoreJsonModel> = emptyList(),
    val paragraphs: List<ArticleParagraphJsonModel> = emptyList(),
    val images: List<ArticleImageJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArticleAdvancedScoreJsonModel(
    val questionType: String = "",
    val variant: String = "",
    val score: Int = 0,
    val reason: String = "",
    val basicScore: Int = 0,
    val wordCount: Int = 0,
    val modelKey: String = "",
    val promptVersion: Int = 1,
    val scoredAt: Long = 0,
    val updatedAt: Long = 0
)

@JsonClass(generateAdapter = true)
data class ArticleParagraphJsonModel(
    val paragraphIndex: Int = 0,
    val text: String = "",
    val paragraphType: String = "TEXT",
    val imageUri: String? = null,
    val imageUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ArticleImageJsonModel(
    val localUri: String = "",
    val orderIndex: Int = 0,
    val imageUrl: String? = null
)
