package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncManifest(
    val appVersion: String = "",
    val schemaVersion: Int = 5,
    val syncedAt: Long = 0,
    val deviceName: String = "",
    val dictionaries: List<String> = emptyList(),
    val hasArticles: Boolean = false,
    val hasQuestionBank: Boolean = false
)
