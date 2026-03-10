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
    val coverImageUrl: String? = null,
    val wordCount: Int = 0
)
