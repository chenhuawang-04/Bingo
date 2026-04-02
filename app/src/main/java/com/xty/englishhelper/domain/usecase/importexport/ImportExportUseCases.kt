package com.xty.englishhelper.domain.usecase.importexport

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.json.PlanDayRecordJsonModel
import com.xty.englishhelper.data.json.PlanEventLogJsonModel
import com.xty.englishhelper.data.json.PlanExportJsonModel
import com.xty.englishhelper.data.json.PlanItemJsonModel
import com.xty.englishhelper.data.json.PlanTemplateJsonModel
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.PlanBackup
import com.xty.englishhelper.domain.model.PlanDayRecordBackup
import com.xty.englishhelper.domain.model.PlanEventLogBackup
import com.xty.englishhelper.domain.model.PlanItemBackup
import com.xty.englishhelper.domain.model.PlanTemplateBackup
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.DictionaryImportExporter
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.PlanRepository
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.usecase.word.EnsureDictionaryWordUidsUseCase
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

            val wordUidToId = mutableMapOf<String, Long>()
            result.words.forEach { word ->
                val wordWithDict = word.copy(
                    dictionaryId = dictId,
                    normalizedSpelling = word.spelling.trim().lowercase(),
                    wordUid = word.wordUid
                )
                val wordId = wordRepository.insertWord(wordWithDict)
                wordUidToId[wordWithDict.wordUid] = wordId
            }
            dictionaryRepository.updateWordCount(dictId)

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

            result.studyStates.forEach { stateData ->
                val wordId = wordUidToId[stateData.wordUid] ?: return@forEach
                studyRepository.upsertStudyState(
                    WordStudyState(
                        wordId = wordId,
                        state = stateData.state,
                        step = stateData.step,
                        stability = stateData.stability,
                        difficulty = stateData.difficulty,
                        due = stateData.due,
                        lastReviewAt = stateData.lastReviewAt,
                        reps = stateData.reps,
                        lapses = stateData.lapses
                    )
                )
            }

            wordRepository.recomputeAllAssociationsForDictionary(dictId)

            "导入成功：${result.dictionary.name}（${result.words.size} 个单词）"
        }
    }
}

class ExportDictionaryUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val unitRepository: UnitRepository,
    private val studyRepository: StudyRepository,
    private val importExporter: DictionaryImportExporter,
    private val ensureDictionaryWordUids: EnsureDictionaryWordUidsUseCase
) {
    suspend operator fun invoke(dictionaryId: Long, dictionaryName: String, dictionaryDescription: String): String {
        val words = ensureDictionaryWordUids(dictionaryId, dictionaryName)
        val units = unitRepository.getUnitsByDictionary(dictionaryId)
        val studyStates = studyRepository.getStudyStatesForDictionary(dictionaryId)

        val wordIdToUid = words.associate { it.id to it.wordUid }

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

class ExportPlanUseCase @Inject constructor(
    private val planRepository: PlanRepository,
    moshi: Moshi
) {
    private val planAdapter = moshi.adapter(PlanExportJsonModel::class.java).indent("  ")

    suspend operator fun invoke(): String {
        val backup = planRepository.exportBackup()
        return planAdapter.toJson(backup.toJsonModel())
    }
}

class ImportPlanUseCase @Inject constructor(
    private val planRepository: PlanRepository,
    moshi: Moshi
) {
    private val planAdapter = moshi.adapter(PlanExportJsonModel::class.java)

    suspend operator fun invoke(json: String): String {
        val model = planAdapter.fromJson(json)
            ?: throw IllegalStateException("计划导入失败：JSON 为空")
        if (model.schemaVersion > 1) {
            throw IllegalStateException("计划导入失败：不支持的数据版本 ${model.schemaVersion}")
        }
        planRepository.replaceFromBackup(model.toDomainBackup())
        return "计划导入成功"
    }
}

private fun PlanBackup.toJsonModel(): PlanExportJsonModel {
    return PlanExportJsonModel(
        schemaVersion = schemaVersion,
        exportedAt = exportedAt,
        templates = templates.map {
            PlanTemplateJsonModel(
                id = it.id,
                name = it.name,
                isActive = it.isActive,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        },
        items = items.map {
            PlanItemJsonModel(
                id = it.id,
                templateId = it.templateId,
                taskType = it.taskType,
                title = it.title,
                targetCount = it.targetCount,
                autoEnabled = it.autoEnabled,
                autoSource = it.autoSource,
                orderIndex = it.orderIndex,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        },
        dayRecords = dayRecords.map {
            PlanDayRecordJsonModel(
                id = it.id,
                dayStart = it.dayStart,
                itemId = it.itemId,
                doneCount = it.doneCount,
                isCompleted = it.isCompleted,
                updatedAt = it.updatedAt,
                completedAt = it.completedAt
            )
        },
        eventLogs = eventLogs.map {
            PlanEventLogJsonModel(
                id = it.id,
                dayStart = it.dayStart,
                eventKey = it.eventKey,
                source = it.source,
                createdAt = it.createdAt
            )
        }
    )
}

private fun PlanExportJsonModel.toDomainBackup(): PlanBackup {
    return PlanBackup(
        schemaVersion = schemaVersion,
        exportedAt = exportedAt,
        templates = templates.map {
            PlanTemplateBackup(
                id = it.id,
                name = it.name,
                isActive = it.isActive,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        },
        items = items.map {
            PlanItemBackup(
                id = it.id,
                templateId = it.templateId,
                taskType = it.taskType,
                title = it.title,
                targetCount = it.targetCount,
                autoEnabled = it.autoEnabled,
                autoSource = it.autoSource,
                orderIndex = it.orderIndex,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        },
        dayRecords = dayRecords.map {
            PlanDayRecordBackup(
                id = it.id,
                dayStart = it.dayStart,
                itemId = it.itemId,
                doneCount = it.doneCount,
                isCompleted = it.isCompleted,
                updatedAt = it.updatedAt,
                completedAt = it.completedAt
            )
        },
        eventLogs = eventLogs.map {
            PlanEventLogBackup(
                id = it.id,
                dayStart = it.dayStart,
                eventKey = it.eventKey,
                source = it.source,
                createdAt = it.createdAt
            )
        }
    )
}
