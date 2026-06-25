package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.UnitDao
import com.xty.englishhelper.data.local.entity.UnitWordCrossRef
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.data.mapper.toEntity
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

    override suspend fun insertUnit(unit: StudyUnit): Long {
        val inserted = unitDao.insertUnit(unit.toEntity())
        if (inserted != -1L) return inserted
        return unitDao.getUnitByUid(unit.unitUid)?.id
            ?: throw IllegalStateException("Unit insert conflict but row not found for uid=${unit.unitUid}")
    }

    override suspend fun updateUnit(unit: StudyUnit) =
        unitDao.updateUnit(unit.toEntity())

    override suspend fun updateUnitName(unitId: Long, name: String, updatedAt: Long) =
        unitDao.updateUnitName(unitId, name, updatedAt)

    override suspend fun updateRepeatCount(unitId: Long, repeatCount: Int, updatedAt: Long) =
        unitDao.updateRepeatCount(unitId, repeatCount, updatedAt)

    override suspend fun deleteUnit(unitId: Long) =
        unitDao.deleteUnit(unitId)

    override suspend fun addWordsToUnit(unitId: Long, wordIds: List<Long>, touchUpdatedAt: Boolean) {
        val crossRefs = wordIds.map { UnitWordCrossRef(unitId = unitId, wordId = it) }
        unitDao.insertCrossRefs(crossRefs)
        if (touchUpdatedAt) {
            unitDao.touchUnit(unitId, System.currentTimeMillis())
        }
    }

    override suspend fun removeWordsFromUnit(unitId: Long, wordIds: List<Long>, touchUpdatedAt: Boolean) {
        unitDao.removeCrossRefs(unitId, wordIds)
        if (touchUpdatedAt) {
            unitDao.touchUnit(unitId, System.currentTimeMillis())
        }
    }

    override suspend fun getWordIdsInUnit(unitId: Long): List<Long> =
        unitDao.getWordIdsInUnit(unitId)

    override suspend fun getUnitIdsForWord(wordId: Long): List<Long> =
        unitDao.getUnitIdsForWord(wordId)

    override fun getWordsInUnit(unitId: Long): Flow<List<WordDetails>> =
        unitDao.getWordsInUnit(unitId).map { list ->
            list.map { it.toDomain() }
        }
}
