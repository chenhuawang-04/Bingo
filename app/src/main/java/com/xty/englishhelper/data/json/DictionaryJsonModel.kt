package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DictionaryJsonModel(
    val name: String = "",
    val description: String = "",
    val schemaVersion: Int = 0,
    val words: List<WordJsonModel> = emptyList(),
    val units: List<UnitJsonModel> = emptyList(),
    val studyStates: List<StudyStateJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WordJsonModel(
    val spelling: String = "",
    val phonetic: String = "",
    val wordUid: String = "",
    val meanings: List<MeaningJsonModel> = emptyList(),
    val rootExplanation: String = "",
    val decomposition: List<DecompositionPartJsonModel> = emptyList(),
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

@JsonClass(generateAdapter = true)
data class DecompositionPartJsonModel(
    val segment: String = "",
    val role: String = "",
    val meaning: String = ""
)

@JsonClass(generateAdapter = true)
data class UnitJsonModel(
    val name: String = "",
    val repeatCount: Int = 2,
    val wordUids: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StudyStateJsonModel(
    val wordUid: String = "",
    val state: Int = 2,
    val step: Int? = null,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0,
    val lastReviewAt: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0
)
