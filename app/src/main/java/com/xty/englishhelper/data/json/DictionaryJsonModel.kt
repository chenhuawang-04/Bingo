package com.xty.englishhelper.data.json

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DictionaryJsonModel(
    val dictionaryUid: String = "",
    val name: String = "",
    val description: String = "",
    val color: Int = 0xFF4A6FA5.toInt(),
    val schemaVersion: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val words: List<WordJsonModel> = emptyList(),
    val units: List<UnitJsonModel> = emptyList(),
    val studyStates: List<StudyStateJsonModel> = emptyList(),
    val wordPools: List<WordPoolJsonModel> = emptyList(),
    val phraseTags: List<WordPhraseTagJsonModel> = emptyList(),
    val wordPhrases: List<WordPhraseJsonModel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WordJsonModel(
    val spelling: String = "",
    val phonetic: String = "",
    val wordUid: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val meanings: List<MeaningJsonModel> = emptyList(),
    val rootExplanation: String = "",
    val decomposition: List<DecompositionPartJsonModel> = emptyList(),
    val synonyms: List<SynonymJsonModel> = emptyList(),
    val similarWords: List<SimilarWordJsonModel> = emptyList(),
    val cognates: List<CognateJsonModel> = emptyList(),
    val inflections: List<InflectionJsonModel> = emptyList()
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
    val unitUid: String = "",
    val name: String = "",
    val repeatCount: Int = 2,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val wordUids: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StudyStateJsonModel(
    val wordUid: String = "",
    val mode: String = "NORMAL",
    val state: Int = 2,
    val step: Int? = null,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0,
    val lastReviewAt: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0
)

@JsonClass(generateAdapter = true)
data class InflectionJsonModel(
    val form: String = "",
    val formType: String = ""
)

@JsonClass(generateAdapter = true)
data class WordPoolJsonModel(
    val focusWordUid: String? = null,
    val memberWordUids: List<String> = emptyList(),
    val strategy: String = "",
    val algorithmVersion: String = "",
    val updatedAt: Long = 0,
    val qualityScore: Int? = null
)

@JsonClass(generateAdapter = true)
data class WordPhraseTagJsonModel(
    val tagUid: String = "",
    val name: String = "",
    val normalizedName: String = "",
    val description: String = "",
    val source: String = "AI",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@JsonClass(generateAdapter = true)
data class WordPhraseJsonModel(
    val phraseUid: String = "",
    val wordUid: String = "",
    val phrase: String = "",
    val normalizedPhrase: String = "",
    val meaning: String = "",
    val example: String = "",
    val usageNote: String = "",
    val register: String? = null,
    val difficulty: String? = null,
    val confidence: Float = 0.8f,
    val source: String = "AI",
    val model: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val organizedAt: Long = 0,
    val tagUids: List<String> = emptyList()
)
