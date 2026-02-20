package com.xty.englishhelper.ui.mapper

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xty.englishhelper.data.local.converter.MeaningJson
import com.xty.englishhelper.data.local.entity.CognateEntity
import com.xty.englishhelper.data.local.entity.SimilarWordEntity
import com.xty.englishhelper.data.local.entity.SynonymEntity
import com.xty.englishhelper.data.local.entity.WordEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.model.WordDetails

private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
private val meaningListType = Types.newParameterizedType(List::class.java, MeaningJson::class.java)
private val meaningAdapter = moshi.adapter<List<MeaningJson>>(meaningListType)

fun WordWithDetails.toDomain() = WordDetails(
    id = word.id,
    dictionaryId = word.dictionaryId,
    spelling = word.spelling,
    phonetic = word.phonetic,
    meanings = parseMeanings(word.meaningsJson),
    rootExplanation = word.rootExplanation,
    synonyms = synonyms.map { it.toDomain() },
    similarWords = similarWords.map { it.toDomain() },
    cognates = cognates.map { it.toDomain() },
    createdAt = word.createdAt,
    updatedAt = word.updatedAt
)

fun WordDetails.toEntity() = WordEntity(
    id = id,
    dictionaryId = dictionaryId,
    spelling = spelling,
    phonetic = phonetic,
    meaningsJson = meaningsToJson(meanings),
    rootExplanation = rootExplanation,
    createdAt = createdAt,
    updatedAt = System.currentTimeMillis()
)

fun WordDetails.toSynonymEntities(wordId: Long) = synonyms.map {
    SynonymEntity(wordId = wordId, synonym = it.word, explanation = it.explanation)
}

fun WordDetails.toSimilarWordEntities(wordId: Long) = similarWords.map {
    SimilarWordEntity(wordId = wordId, similarWord = it.word, meaning = it.meaning, explanation = it.explanation)
}

fun WordDetails.toCognateEntities(wordId: Long) = cognates.map {
    CognateEntity(wordId = wordId, cognate = it.word, meaning = it.meaning, sharedRoot = it.sharedRoot)
}

private fun SynonymEntity.toDomain() = SynonymInfo(
    id = id, word = synonym, explanation = explanation
)

private fun SimilarWordEntity.toDomain() = SimilarWordInfo(
    id = id, word = similarWord, meaning = meaning, explanation = explanation
)

private fun CognateEntity.toDomain() = CognateInfo(
    id = id, word = cognate, meaning = meaning, sharedRoot = sharedRoot
)

private fun parseMeanings(json: String): List<Meaning> {
    return try {
        val list = meaningAdapter.fromJson(json) ?: emptyList()
        list.map { Meaning(pos = it.pos, definition = it.definition) }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun meaningsToJson(meanings: List<Meaning>): String {
    val list = meanings.map { MeaningJson(pos = it.pos, definition = it.definition) }
    return meaningAdapter.toJson(list)
}
