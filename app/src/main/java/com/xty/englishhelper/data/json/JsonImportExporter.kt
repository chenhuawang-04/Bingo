package com.xty.englishhelper.data.json

import com.squareup.moshi.Moshi
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.DictionaryImportExporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonImportExporter @Inject constructor(
    private val moshi: Moshi
) : DictionaryImportExporter {

    private val adapter = moshi.adapter(DictionaryJsonModel::class.java).indent("  ")

    override fun exportToJson(
        dictionary: Dictionary,
        words: List<WordDetails>,
        units: List<StudyUnit>,
        unitWordMap: Map<Long, List<String>>,
        studyStates: List<WordStudyState>,
        wordIdToUid: Map<Long, String>
    ): String {
        val model = DictionaryJsonModel(
            name = dictionary.name,
            description = dictionary.description,
            schemaVersion = 3,
            words = words.map { word ->
                WordJsonModel(
                    spelling = word.spelling,
                    phonetic = word.phonetic,
                    wordUid = word.wordUid,
                    meanings = word.meanings.map { MeaningJsonModel(it.pos, it.definition) },
                    rootExplanation = word.rootExplanation,
                    decomposition = word.decomposition.map {
                        DecompositionPartJsonModel(it.segment, it.role.name, it.meaning)
                    },
                    synonyms = word.synonyms.map { SynonymJsonModel(it.word, it.explanation) },
                    similarWords = word.similarWords.map {
                        SimilarWordJsonModel(it.word, it.meaning, it.explanation)
                    },
                    cognates = word.cognates.map {
                        CognateJsonModel(it.word, it.meaning, it.sharedRoot)
                    }
                )
            },
            units = units.map { unit ->
                UnitJsonModel(
                    name = unit.name,
                    repeatCount = unit.defaultRepeatCount,
                    wordUids = unitWordMap[unit.id] ?: emptyList()
                )
            },
            studyStates = studyStates.mapNotNull { state ->
                val uid = wordIdToUid[state.wordId] ?: return@mapNotNull null
                StudyStateJsonModel(
                    wordUid = uid,
                    remainingReviews = state.remainingReviews,
                    easeLevel = state.easeLevel,
                    nextReviewAt = state.nextReviewAt,
                    lastReviewedAt = state.lastReviewedAt
                )
            }
        )
        return adapter.toJson(model)
    }

    override fun importFromJson(json: String): DictionaryImportExporter.ImportResult {
        val model = adapter.fromJson(json) ?: throw IllegalArgumentException("Invalid JSON")

        // Validate schema version
        if (model.schemaVersion != 3) {
            throw IllegalArgumentException("不支持的文件格式（需要 schemaVersion: 3）")
        }

        // Validate no empty spellings
        model.words.forEachIndexed { index, word ->
            if (word.spelling.isBlank()) {
                throw IllegalArgumentException("第 ${index + 1} 个单词的 spelling 为空")
            }
        }

        // Validate no duplicate normalized spellings
        val normalizedSet = mutableSetOf<String>()
        model.words.forEach { word ->
            val normalized = word.spelling.trim().lowercase()
            if (!normalizedSet.add(normalized)) {
                throw IllegalArgumentException("文件中存在重复拼写：${word.spelling}")
            }
        }

        // Validate wordUid: when present, must be unique
        val uidSet = mutableSetOf<String>()
        model.words.forEach { word ->
            if (word.wordUid.isNotBlank() && !uidSet.add(word.wordUid)) {
                throw IllegalArgumentException("文件中存在重复 wordUid：${word.wordUid}")
            }
        }

        val dictionary = Dictionary(
            name = model.name,
            description = model.description,
            wordCount = model.words.size
        )
        val words = model.words.map { word ->
            WordDetails(
                dictionaryId = 0,
                spelling = word.spelling,
                phonetic = word.phonetic,
                wordUid = word.wordUid,
                normalizedSpelling = word.spelling.trim().lowercase(),
                meanings = word.meanings.map { Meaning(it.pos, it.definition) },
                rootExplanation = word.rootExplanation,
                decomposition = word.decomposition.map {
                    DecompositionPart(
                        segment = it.segment,
                        role = runCatching { MorphemeRole.valueOf(it.role) }.getOrDefault(MorphemeRole.OTHER),
                        meaning = it.meaning
                    )
                },
                synonyms = word.synonyms.map { SynonymInfo(word = it.word, explanation = it.explanation) },
                similarWords = word.similarWords.map {
                    SimilarWordInfo(word = it.word, meaning = it.meaning, explanation = it.explanation)
                },
                cognates = word.cognates.map {
                    CognateInfo(word = it.word, meaning = it.meaning, sharedRoot = it.sharedRoot)
                }
            )
        }
        return DictionaryImportExporter.ImportResult(
            dictionary = dictionary,
            words = words,
            units = model.units.map {
                DictionaryImportExporter.ImportedUnit(
                    name = it.name,
                    repeatCount = it.repeatCount,
                    wordUids = it.wordUids
                )
            },
            studyStates = model.studyStates.map {
                DictionaryImportExporter.ImportedStudyState(
                    wordUid = it.wordUid,
                    remainingReviews = it.remainingReviews,
                    easeLevel = it.easeLevel,
                    nextReviewAt = it.nextReviewAt,
                    lastReviewedAt = it.lastReviewedAt
                )
            }
        )
    }
}
