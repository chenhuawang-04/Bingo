package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.WordDetails
import kotlinx.coroutines.flow.Flow

interface WordRepository {
    fun getWordsByDictionary(dictionaryId: Long): Flow<List<WordDetails>>
    fun searchWords(dictionaryId: Long, query: String): Flow<List<WordDetails>>
    suspend fun getWordById(wordId: Long): WordDetails?
    suspend fun insertWord(word: WordDetails): Long
    suspend fun updateWord(word: WordDetails)
    suspend fun deleteWord(wordId: Long)
}
