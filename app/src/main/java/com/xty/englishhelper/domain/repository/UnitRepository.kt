package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails
import kotlinx.coroutines.flow.Flow

interface UnitRepository {
    fun getUnitsWithWordCount(dictionaryId: Long): Flow<List<StudyUnit>>
    suspend fun getUnitById(unitId: Long): StudyUnit?
    suspend fun getUnitsByDictionary(dictionaryId: Long): List<StudyUnit>
    suspend fun insertUnit(unit: StudyUnit): Long
    suspend fun updateUnitName(unitId: Long, name: String)
    suspend fun updateRepeatCount(unitId: Long, repeatCount: Int)
    suspend fun deleteUnit(unitId: Long)
    suspend fun addWordsToUnit(unitId: Long, wordIds: List<Long>)
    suspend fun removeWordsFromUnit(unitId: Long, wordIds: List<Long>)
    suspend fun getWordIdsInUnit(unitId: Long): List<Long>
    suspend fun getUnitIdsForWord(wordId: Long): List<Long>
    fun getWordsInUnit(unitId: Long): Flow<List<WordDetails>>
}
