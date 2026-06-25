package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.WordStudyState

interface StudyRepository {
    suspend fun getStudyState(wordId: Long, studyMode: StudyMode = StudyMode.NORMAL): WordStudyState?
    suspend fun upsertStudyState(state: WordStudyState)
    suspend fun getDueWords(
        unitIds: List<Long>,
        now: Long,
        studyMode: StudyMode = StudyMode.NORMAL
    ): List<WordDetails>
    suspend fun getNewWords(
        unitIds: List<Long>,
        studyMode: StudyMode = StudyMode.NORMAL
    ): List<WordDetails>
    suspend fun countDueWords(
        unitId: Long,
        now: Long,
        studyMode: StudyMode = StudyMode.NORMAL
    ): Int
    suspend fun countNewWords(
        unitId: Long,
        studyMode: StudyMode = StudyMode.NORMAL
    ): Int
    suspend fun getStudyStatesForDictionary(
        dictionaryId: Long,
        studyMode: StudyMode? = null
    ): List<WordStudyState>
    suspend fun countAllDueWords(now: Long): Int
    suspend fun countReviewedToday(todayStart: Long, now: Long): Int
    suspend fun getAllActiveStudyStates(): List<WordStudyState>
}
