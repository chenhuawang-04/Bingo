package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.UnitDao
import com.xty.englishhelper.data.local.entity.UnitWordCrossRef
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.ui.mapper.toDomain
import com.xty.englishhelper.ui.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnitRepositoryImpl @Inject constructor(
    private val unitDao: UnitDao
) : UnitRepository {

    override fun getUnitsWithWordCount(dictionaryId: Long): Flow<List<StudyUnit>> =
        unitDao.getUnitsWithWordCount(dictionaryId).map { list -> list.map { it.toDomain() } }

    override suspend fun getUnitById(unitId: Long): StudyUnit? =
        unitDao.getUnitById(unitId)?.toDomain()

    override suspend fun getUnitsByDictionary(dictionaryId: Long): List<StudyUnit> =
        unitDao.getUnitsByDictionary(dictionaryId).map { it.toDomain() }

    override suspend fun insertUnit(unit: StudyUnit): Long =
        unitDao.insertUnit(unit.toEntity())

    override suspend fun updateUnitName(unitId: Long, name: String) =
        unitDao.updateUnitName(unitId, name)

    override suspend fun updateRepeatCount(unitId: Long, repeatCount: Int) =
        unitDao.updateRepeatCount(unitId, repeatCount)

    override suspend fun deleteUnit(unitId: Long) =
        unitDao.deleteUnit(unitId)

    override suspend fun addWordsToUnit(unitId: Long, wordIds: List<Long>) {
        val crossRefs = wordIds.map { UnitWordCrossRef(unitId = unitId, wordId = it) }
        unitDao.insertCrossRefs(crossRefs)
    }

    override suspend fun removeWordsFromUnit(unitId: Long, wordIds: List<Long>) =
        unitDao.removeCrossRefs(unitId, wordIds)

    override suspend fun getWordIdsInUnit(unitId: Long): List<Long> =
        unitDao.getWordIdsInUnit(unitId)

    override suspend fun getUnitIdsForWord(wordId: Long): List<Long> =
        unitDao.getUnitIdsForWord(wordId)

    override fun getWordsInUnit(unitId: Long): Flow<List<WordDetails>> =
        unitDao.getWordsInUnit(unitId).map { list ->
            list.map { it.toDomain() }
        }
}
