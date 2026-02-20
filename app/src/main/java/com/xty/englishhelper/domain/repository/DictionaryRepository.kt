package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.Dictionary
import kotlinx.coroutines.flow.Flow

interface DictionaryRepository {
    fun getAllDictionaries(): Flow<List<Dictionary>>
    suspend fun getDictionaryById(id: Long): Dictionary?
    suspend fun insertDictionary(dictionary: Dictionary): Long
    suspend fun updateDictionary(dictionary: Dictionary)
    suspend fun deleteDictionary(id: Long)
    suspend fun updateWordCount(dictionaryId: Long)
}
