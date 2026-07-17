package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.DictionaryDao
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    private val dao: DictionaryDao,
    private val wordEdgeDao: WordEdgeDao,
    private val transactionRunner: TransactionRunner
) : DictionaryRepository {

    override fun getAllDictionaries(): Flow<List<Dictionary>> =
        dao.getAllDictionaries().map { list -> list.map { it.toDomain() } }

    override suspend fun getDictionaryById(id: Long): Dictionary? =
        dao.getDictionaryById(id)?.toDomain()

    override suspend fun insertDictionary(dictionary: Dictionary): Long {
        val inserted = dao.insert(dictionary.toEntity())
        if (inserted != -1L) return inserted
        return dao.getDictionaryByUid(dictionary.dictionaryUid)?.id
            ?: throw IllegalStateException("Dictionary insert conflict but row not found for uid=${dictionary.dictionaryUid}")
    }

    override suspend fun updateDictionary(dictionary: Dictionary) =
        dao.update(dictionary.toEntity())

    override suspend fun deleteDictionary(id: Long) {
        transactionRunner.runInTransaction {
            wordEdgeDao.deleteByDictionary(id)
            wordEdgeDao.deleteExcludedByDictionary(id)
            dao.deleteById(id)
        }
    }

    override suspend fun updateWordCount(dictionaryId: Long) =
        dao.updateWordCount(dictionaryId)
}
