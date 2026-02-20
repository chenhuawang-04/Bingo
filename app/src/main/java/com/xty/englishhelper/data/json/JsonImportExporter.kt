package com.xty.englishhelper.data.json

import com.squareup.moshi.Moshi
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.model.WordDetails
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonImportExporter @Inject constructor(
    private val moshi: Moshi
) {
    private val adapter = moshi.adapter(DictionaryJsonModel::class.java).indent("  ")

    fun exportToJson(dictionary: Dictionary, words: List<WordDetails>): String {
        val model = DictionaryJsonModel(
            name = dictionary.name,
            description = dictionary.description,
            words = words.map { word ->
                WordJsonModel(
                    spelling = word.spelling,
                    phonetic = word.phonetic,
                    meanings = word.meanings.map { MeaningJsonModel(it.pos, it.definition) },
                    rootExplanation = word.rootExplanation,
                    synonyms = word.synonyms.map { SynonymJsonModel(it.word, it.explanation) },
                    similarWords = word.similarWords.map {
                        SimilarWordJsonModel(it.word, it.meaning, it.explanation)
                    },
                    cognates = word.cognates.map {
                        CognateJsonModel(it.word, it.meaning, it.sharedRoot)
                    }
                )
            }
        )
        return adapter.toJson(model)
    }

    fun importFromJson(json: String): Pair<Dictionary, List<WordDetails>> {
        val model = adapter.fromJson(json) ?: throw IllegalArgumentException("Invalid JSON")
        val dictionary = Dictionary(
            name = model.name,
            description = model.description,
            wordCount = model.words.size
        )
        val words = model.words.map { word ->
            WordDetails(
                dictionaryId = 0, // Will be set after dictionary is created
                spelling = word.spelling,
                phonetic = word.phonetic,
                meanings = word.meanings.map { Meaning(it.pos, it.definition) },
                rootExplanation = word.rootExplanation,
                synonyms = word.synonyms.map { SynonymInfo(word = it.word, explanation = it.explanation) },
                similarWords = word.similarWords.map {
                    SimilarWordInfo(word = it.word, meaning = it.meaning, explanation = it.explanation)
                },
                cognates = word.cognates.map {
                    CognateInfo(word = it.word, meaning = it.meaning, sharedRoot = it.sharedRoot)
                }
            )
        }
        return dictionary to words
    }
}
