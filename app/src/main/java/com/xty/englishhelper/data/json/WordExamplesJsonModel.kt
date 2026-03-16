package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WordExamplesExportModel(
    val schemaVersion: Int = 1,
    val examples: List<WordExampleJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WordExampleJsonModel(
    val wordUid: String = "",
    val sentence: String = "",
    val sourceType: Int = 0,
    val sourceArticleUid: String? = null,
    val sourceSentenceIndex: Int? = null,
    val sourceLabel: String? = null,
    val createdAt: Long = 0
)
