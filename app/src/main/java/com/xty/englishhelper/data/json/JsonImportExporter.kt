package com.xty.englishhelper.data.json

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.sync.DictionaryWordUidNormalizer
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPhraseSyncSnapshot
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.DictionaryImportExporter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonImportExporter @Inject constructor(
    private val moshi: Moshi,
    private val wordUidNormalizer: DictionaryWordUidNormalizer
) : DictionaryImportExporter {

    private val adapter = moshi.adapter(DictionaryJsonModel::class.java).indent("  ")

    override fun exportToJson(
        dictionary: Dictionary,
        words: List<WordDetails>,
        units: List<StudyUnit>,
        unitWordMap: Map<Long, List<String>>,
        studyStates: List<WordStudyState>,
        wordIdToUid: Map<Long, String>,
        wordPhraseSnapshot: WordPhraseSyncSnapshot
    ): String {
        words.forEach { word ->
            require(word.wordUid.isNotBlank()) {
                "导出失败：单词 ${word.spelling} 缺少 wordUid"
            }
        }
        unitWordMap.forEach { (unitId, wordUids) ->
            require(wordUids.all { it.isNotBlank() }) {
                "导出失败：单元 $unitId 包含空 wordUid 引用"
            }
        }
        studyStates.forEach { state ->
            require(wordIdToUid[state.wordId].orEmpty().isNotBlank()) {
                "导出失败：学习状态 ${state.wordId} 缺少 wordUid 引用"
            }
        }

        val model = DictionaryJsonModel(
            dictionaryUid = dictionary.dictionaryUid,
            name = dictionary.name,
            description = dictionary.description,
            color = dictionary.color,
            schemaVersion = 11,
            createdAt = dictionary.createdAt,
            updatedAt = dictionary.updatedAt,
            words = words.map { word ->
                WordJsonModel(
                    spelling = word.spelling,
                    phonetic = word.phonetic,
                    wordUid = word.wordUid,
                    createdAt = word.createdAt,
                    updatedAt = word.updatedAt,
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
                    },
                    inflections = word.inflections.map {
                        InflectionJsonModel(it.form, it.formType)
                    }
                )
            },
            units = units.map { unit ->
                UnitJsonModel(
                    unitUid = unit.unitUid,
                    name = unit.name,
                    repeatCount = unit.defaultRepeatCount,
                    createdAt = unit.createdAt,
                    updatedAt = unit.updatedAt,
                    wordUids = unitWordMap[unit.id] ?: emptyList()
                )
            },
            studyStates = studyStates.mapNotNull { state ->
                val uid = wordIdToUid[state.wordId] ?: return@mapNotNull null
                StudyStateJsonModel(
                    wordUid = uid,
                    mode = state.studyMode.name,
                    state = state.state,
                    step = state.step,
                    stability = state.stability,
                    difficulty = state.difficulty,
                    due = state.due,
                    lastReviewAt = state.lastReviewAt,
                    reps = state.reps,
                    lapses = state.lapses
                )
            },
            phraseTags = wordPhraseSnapshot.toPhraseTagJsonModels(),
            wordPhrases = wordPhraseSnapshot.toWordPhraseJsonModels()
        )
        return adapter.toJson(model)
    }

    override fun importFromJson(json: String): DictionaryImportExporter.ImportResult {
        val parsedModel = adapter.fromJson(json) ?: throw IllegalArgumentException("Invalid JSON")

        // Validate schema version
        if (parsedModel.schemaVersion !in listOf(4, 5, 6, 7, 8, 9, 10, 11)) {
            throw IllegalArgumentException("不支持的文件格式（需要 schemaVersion: 4、5、6、7、8、9、10 或 11）")
        }

        // Validate no empty spellings
        parsedModel.words.forEachIndexed { index, word ->
            if (word.spelling.isBlank()) {
                throw IllegalArgumentException("第 ${index + 1} 个单词的 spelling 为空")
            }
        }

        // Validate no duplicate normalized spellings
        val normalizedSet = mutableSetOf<String>()
        parsedModel.words.forEach { word ->
            val normalized = word.spelling.trim().lowercase()
            if (!normalizedSet.add(normalized)) {
                throw IllegalArgumentException("文件中存在重复拼写：${word.spelling}")
            }
        }

        val model = wordUidNormalizer.normalize(parsedModel)

        val unitUids = mutableSetOf<String>()
        model.units.forEachIndexed { index, unit ->
            if (unit.unitUid.isNotBlank() && !unitUids.add(unit.unitUid)) {
                throw IllegalArgumentException("第 ${index + 1} 个单元的 unitUid 重复：${unit.unitUid}")
            }
        }

        // Validate wordUid: when present, must be unique
        val uidSet = mutableSetOf<String>()
        model.words.forEach { word ->
            if (word.wordUid.isNotBlank() && !uidSet.add(word.wordUid)) {
                throw IllegalArgumentException("文件中存在重复 wordUid：${word.wordUid}")
            }
        }

        // Validate study state: state must be 1 (Learning), 2 (Review), or 3 (Relearning)
        val validStates = setOf(1, 2, 3)
        model.studyStates.forEachIndexed { index, state ->
            if (state.state !in validStates) {
                throw IllegalArgumentException("第 ${index + 1} 个学习状态的 state 值无效：${state.state}（需要 1/2/3）")
            }
            parseStudyMode(state.mode) ?: throw IllegalArgumentException(
                "第 ${index + 1} 个学习状态的 mode 值无效：${state.mode}"
            )
        }

        WordPhraseJsonValidator.validate(model)

        val now = System.currentTimeMillis()
        val dictionaryCreatedAt = model.createdAt.takeIf { it > 0 } ?: now
        val dictionaryUpdatedAt = model.updatedAt.takeIf { it > 0 } ?: dictionaryCreatedAt
        val dictionary = Dictionary(
            dictionaryUid = model.dictionaryUid.ifBlank { UUID.randomUUID().toString() },
            name = model.name,
            description = model.description,
            color = model.color,
            wordCount = model.words.size,
            createdAt = dictionaryCreatedAt,
            updatedAt = dictionaryUpdatedAt
        )
        val words = model.words.map { word ->
            WordDetails(
                dictionaryId = 0,
                spelling = word.spelling,
                phonetic = word.phonetic,
                wordUid = word.wordUid,
                createdAt = word.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                updatedAt = word.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
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
                },
                inflections = word.inflections.map {
                    Inflection(form = it.form, formType = it.formType)
                }
            )
        }
        return DictionaryImportExporter.ImportResult(
            dictionary = dictionary,
            words = words,
            units = model.units.map {
                val createdAt = it.createdAt.takeIf { value -> value > 0 } ?: now
                val updatedAt = it.updatedAt.takeIf { value -> value > 0 } ?: createdAt
                DictionaryImportExporter.ImportedUnit(
                    unitUid = it.unitUid.ifBlank { UUID.randomUUID().toString() },
                    name = it.name,
                    repeatCount = it.repeatCount,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    wordUids = it.wordUids
                )
            },
            studyStates = model.studyStates.map {
                DictionaryImportExporter.ImportedStudyState(
                    wordUid = it.wordUid,
                    studyMode = parseStudyMode(it.mode) ?: StudyMode.NORMAL,
                    state = it.state,
                    step = it.step,
                    stability = it.stability,
                    difficulty = it.difficulty,
                    due = it.due,
                    lastReviewAt = it.lastReviewAt,
                    reps = it.reps,
                    lapses = it.lapses
                )
            },
            wordPhraseSnapshot = wordPhraseSyncSnapshotFromJson(
                phraseTags = model.phraseTags,
                wordPhrases = model.wordPhrases
            )
        )
    }

    private fun parseStudyMode(raw: String): StudyMode? =
        StudyMode.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
}
