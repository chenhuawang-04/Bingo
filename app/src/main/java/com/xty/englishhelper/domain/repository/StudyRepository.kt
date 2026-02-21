package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordStudyState

interface StudyRepository {
    suspend fun getStudyState(wordId: Long): WordStudyState?
    suspend fun upsertStudyState(state: WordStudyState)
    suspend fun getDueWords(unitIds: List<Long>, now: Long): List<WordDetails>
    suspend fun getNewWords(unitIds: List<Long>): List<WordDetails>
    suspend fun countDueWords(unitId: Long, now: Long): Int
    suspend fun countNewWords(unitId: Long): Int
    suspend fun getStudyStatesForDictionary(dictionaryId: Long): List<WordStudyState>
    suspend fun countAllDueWords(now: Long): Int
    suspend fun countReviewedToday(todayStart: Long, now: Long): Int
    suspend fun getAllActiveStudyStates(): List<WordStudyState>
}
