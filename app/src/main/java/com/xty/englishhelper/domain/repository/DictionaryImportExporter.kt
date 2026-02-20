package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordStudyState

/**
 * Domain-layer abstraction for dictionary import/export serialization.
 */
interface DictionaryImportExporter {

    data class ImportedUnit(
        val name: String,
        val repeatCount: Int,
        val wordUids: List<String>
    )

    data class ImportedStudyState(
        val wordUid: String,
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
        val studyStates: List<ImportedStudyState>
    )

    fun exportToJson(
        dictionary: Dictionary,
        words: List<WordDetails>,
        units: List<StudyUnit>,
        unitWordMap: Map<Long, List<String>>,
        studyStates: List<WordStudyState>,
        wordIdToUid: Map<Long, String>
    ): String

    fun importFromJson(json: String): ImportResult
}
