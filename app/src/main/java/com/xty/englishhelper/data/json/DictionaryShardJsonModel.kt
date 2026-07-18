package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DictionaryCloudEntryJsonModel(
    val dictionaryUid: String = "",
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
    val dictionaryUid: String = "",
    val name: String = "",
    val description: String = "",
    val color: Int = 0xFF4A6FA5.toInt(),
    val schemaVersion: Int = 1,
    val dictionarySchemaVersion: Int = 6,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val totalWords: Int = 0,
    val totalStudyStates: Int = 0,
    val totalWordPhrases: Int = 0,
    val totalWordEdges: Int = 0,
    val units: List<UnitJsonModel> = emptyList(),
    val wordPools: List<WordPoolJsonModel> = emptyList(),
    val wordPoolStrategies: List<WordPoolStrategyJsonModel> = emptyList(),
    val wordEdges: List<WordEdgeJsonModel> = emptyList(),
    val phraseTags: List<WordPhraseTagJsonModel> = emptyList(),
    val chunks: List<DictionaryChunkRefJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DictionaryChunkRefJsonModel(
    val file: String = "",
    val wordCount: Int = 0,
    val stateCount: Int = 0,
    val phraseCount: Int = 0,
    val edgeCount: Int = 0,
    val contentHash: String = ""
)

@JsonClass(generateAdapter = true)
data class DictionaryShardChunkJsonModel(
    val schemaVersion: Int = 1,
    val words: List<WordJsonModel> = emptyList(),
    val studyStates: List<StudyStateJsonModel> = emptyList(),
    val wordPhrases: List<WordPhraseJsonModel> = emptyList(),
    val wordEdges: List<WordEdgeJsonModel> = emptyList()
)
