package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.WordDetails
import kotlinx.coroutines.flow.Flow

interface WordRepository {
    fun getWordsByDictionary(dictionaryId: Long): Flow<List<WordDetails>>
    fun searchWords(dictionaryId: Long, query: String): Flow<List<WordDetails>>
    suspend fun getWordById(wordId: Long): WordDetails?
    suspend fun insertWord(word: WordDetails): Long
    suspend fun updateWord(word: WordDetails)
    suspend fun deleteWord(wordId: Long)
    suspend fun findByNormalizedSpelling(dictionaryId: Long, normalizedSpelling: String): WordDetails?
    suspend fun findExistingWordIds(dictionaryId: Long, normalizedSpellings: List<String>): Map<String, Long>
    suspend fun getAssociatedWords(wordId: Long): List<AssociatedWordInfo>
    suspend fun recomputeAssociations(wordId: Long, dictionaryId: Long)
    suspend fun recomputeAllAssociationsForDictionary(dictionaryId: Long)
}
