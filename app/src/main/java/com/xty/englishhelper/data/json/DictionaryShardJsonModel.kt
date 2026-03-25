package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DictionaryCloudEntryJsonModel(
    val name: String = "",
    val format: String = "single",
    val path: String = "",
    val totalWords: Int = 0,
    val chunkCount: Int = 0
) {
    companion object {
        const val FORMAT_SINGLE = "single"
        const val FORMAT_SHARDED = "sharded"
    }
}

@JsonClass(generateAdapter = true)
data class DictionaryShardIndexJsonModel(
    val name: String = "",
    val description: String = "",
    val schemaVersion: Int = 1,
    val dictionarySchemaVersion: Int = 6,
    val totalWords: Int = 0,
    val totalStudyStates: Int = 0,
    val units: List<UnitJsonModel> = emptyList(),
    val wordPools: List<WordPoolJsonModel> = emptyList(),
    val chunks: List<DictionaryChunkRefJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DictionaryChunkRefJsonModel(
    val file: String = "",
    val wordCount: Int = 0,
    val stateCount: Int = 0,
    val contentHash: String = ""
)

@JsonClass(generateAdapter = true)
data class DictionaryShardChunkJsonModel(
    val schemaVersion: Int = 1,
    val words: List<WordJsonModel> = emptyList(),
    val studyStates: List<StudyStateJsonModel> = emptyList()
)
