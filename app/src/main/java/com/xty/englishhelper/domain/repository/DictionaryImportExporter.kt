package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.DictionaryPoolBackup
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPhraseSyncSnapshot
import com.xty.englishhelper.domain.model.WordStudyState

/**
 * Domain-layer abstraction for dictionary import/export serialization.
 */
interface DictionaryImportExporter {

    data class ImportedUnit(
        val unitUid: String,
        val name: String,
        val repeatCount: Int,
        val createdAt: Long,
        val updatedAt: Long,
        val wordUids: List<String>
    )

    data class ImportedStudyState(
        val wordUid: String,
        val studyMode: StudyMode,
        val state: Int,
        val step: Int?,
        val stability: Double,
        val difficulty: Double,
        val due: Long,
        val lastReviewAt: Long,
        val reps: Int,
        val lapses: Int
    )

    data class ImportResult(
        val dictionary: Dictionary,
        val words: List<WordDetails>,
        val units: List<ImportedUnit>,
        val studyStates: List<ImportedStudyState>,
        val wordPhraseSnapshot: WordPhraseSyncSnapshot = WordPhraseSyncSnapshot(),
        val poolBackup: DictionaryPoolBackup = DictionaryPoolBackup()
    )

    fun exportToJson(
        dictionary: Dictionary,
        words: List<WordDetails>,
        units: List<StudyUnit>,
        unitWordMap: Map<Long, List<String>>,
        studyStates: List<WordStudyState>,
        wordIdToUid: Map<Long, String>,
        wordPhraseSnapshot: WordPhraseSyncSnapshot = WordPhraseSyncSnapshot(),
        poolBackup: DictionaryPoolBackup = DictionaryPoolBackup()
    ): String

    fun importFromJson(json: String): ImportResult
}
