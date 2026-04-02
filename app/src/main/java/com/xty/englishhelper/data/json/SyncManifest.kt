package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncManifest(
    val appVersion: String = "",
    val schemaVersion: Int = 6,
    val syncedAt: Long = 0,
    val deviceName: String = "",
    val dictionaries: List<String> = emptyList(),
    val dictionaryEntries: List<DictionaryCloudEntryJsonModel> = emptyList(),
    val hasArticles: Boolean = false,
    val hasQuestionBank: Boolean = false,
    val hasWordExamples: Boolean = false,
    val hasPlan: Boolean = false
) {
    val dictionaryCount: Int
        get() = if (dictionaryEntries.isNotEmpty()) dictionaryEntries.size else dictionaries.size
}
