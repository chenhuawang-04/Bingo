package com.xty.englishhelper.domain.usecase.importexport

import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.DictionaryImportExporter
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.repository.WordRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

class ImportDictionaryUseCase @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    private val wordRepository: WordRepository,
    private val unitRepository: UnitRepository,
    private val studyRepository: StudyRepository,
    private val importExporter: DictionaryImportExporter,
    private val transactionRunner: TransactionRunner
) {
    suspend operator fun invoke(json: String): String {
        val result = importExporter.importFromJson(json)

        return transactionRunner.runInTransaction {
            val dictId = dictionaryRepository.insertDictionary(result.dictionary)

            // Insert words and build wordUid -> wordId map
            val wordUidToId = mutableMapOf<String, Long>()
            result.words.forEach { word ->
                val wordWithDict = word.copy(
                    dictionaryId = dictId,
                    normalizedSpelling = word.spelling.trim().lowercase(),
                    wordUid = word.wordUid.ifBlank { UUID.randomUUID().toString() }
                )
                val wordId = wordRepository.insertWord(wordWithDict)
                wordUidToId[wordWithDict.wordUid] = wordId
            }
            dictionaryRepository.updateWordCount(dictId)

            // Import units
            result.units.forEach { unitData ->
                val unitId = unitRepository.insertUnit(
                    StudyUnit(
                        dictionaryId = dictId,
                        name = unitData.name,
                        defaultRepeatCount = unitData.repeatCount
                    )
                )
                val wordIds = unitData.wordUids.mapNotNull { wordUidToId[it] }
                if (wordIds.isNotEmpty()) {
                    unitRepository.addWordsToUnit(unitId, wordIds)
                }
            }

            // Import study states
            result.studyStates.forEach { stateData ->
                val wordId = wordUidToId[stateData.wordUid] ?: return@forEach
                studyRepository.upsertStudyState(
                    WordStudyState(
                        wordId = wordId,
                        remainingReviews = stateData.remainingReviews,
                        easeLevel = stateData.easeLevel,
                        nextReviewAt = stateData.nextReviewAt,
                        lastReviewedAt = stateData.lastReviewedAt
                    )
                )
            }

            // Recompute word associations for the entire dictionary
            wordRepository.recomputeAllAssociationsForDictionary(dictId)

            "导入成功：${result.dictionary.name}（${result.words.size} 个单词）"
        }
    }
}

class ExportDictionaryUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val unitRepository: UnitRepository,
    private val studyRepository: StudyRepository,
    private val importExporter: DictionaryImportExporter
) {
    suspend operator fun invoke(dictionaryId: Long, dictionaryName: String, dictionaryDescription: String): String {
        val words = wordRepository.getWordsByDictionary(dictionaryId).first()
        val units = unitRepository.getUnitsByDictionary(dictionaryId)
        val studyStates = studyRepository.getStudyStatesForDictionary(dictionaryId)

        // Build wordId -> wordUid map
        val wordIdToUid = words.associate { it.id to it.wordUid }

        // Build unitId -> list of wordUids
        val unitWordMap = mutableMapOf<Long, List<String>>()
        for (unit in units) {
            val wordIds = unitRepository.getWordIdsInUnit(unit.id)
            unitWordMap[unit.id] = wordIds.mapNotNull { wordIdToUid[it] }
        }

        return importExporter.exportToJson(
            dictionary = Dictionary(
                name = dictionaryName,
                description = dictionaryDescription
            ),
            words = words,
            units = units,
            unitWordMap = unitWordMap,
            studyStates = studyStates,
            wordIdToUid = wordIdToUid
        )
    }
}
