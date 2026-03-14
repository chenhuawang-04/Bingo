package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ArticleCategoriesExportModel(
    val schemaVersion: Int = 1,
    val categories: List<ArticleCategoryJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ArticleCategoryJsonModel(
    val id: Long = 0,
    val name: String = "",
    val isSystem: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
