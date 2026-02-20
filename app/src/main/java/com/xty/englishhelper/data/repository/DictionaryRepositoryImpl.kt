package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.DictionaryDao
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    private val dao: DictionaryDao
) : DictionaryRepository {

    override fun getAllDictionaries(): Flow<List<Dictionary>> =
        dao.getAllDictionaries().map { list -> list.map { it.toDomain() } }

    override suspend fun getDictionaryById(id: Long): Dictionary? =
        dao.getDictionaryById(id)?.toDomain()

    override suspend fun insertDictionary(dictionary: Dictionary): Long =
        dao.insert(dictionary.toEntity())

    override suspend fun updateDictionary(dictionary: Dictionary) =
        dao.update(dictionary.toEntity())

    override suspend fun deleteDictionary(id: Long) =
        dao.deleteById(id)

    override suspend fun updateWordCount(dictionaryId: Long) =
        dao.updateWordCount(dictionaryId)
}
