package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DictionaryJsonModel(
    val name: String = "",
    val description: String = "",
    val version: String = "1.0",
    val words: List<WordJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WordJsonModel(
    val spelling: String = "",
    val phonetic: String = "",
    val meanings: List<MeaningJsonModel> = emptyList(),
    val rootExplanation: String = "",
    val synonyms: List<SynonymJsonModel> = emptyList(),
    val similarWords: List<SimilarWordJsonModel> = emptyList(),
    val cognates: List<CognateJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MeaningJsonModel(
    val pos: String = "",
    val definition: String = ""
)

@JsonClass(generateAdapter = true)
data class SynonymJsonModel(
    val word: String = "",
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class SimilarWordJsonModel(
    val word: String = "",
    val meaning: String = "",
    val explanation: String = ""
)

@JsonClass(generateAdapter = true)
data class CognateJsonModel(
    val word: String = "",
    val meaning: String = "",
    val sharedRoot: String = ""
)
