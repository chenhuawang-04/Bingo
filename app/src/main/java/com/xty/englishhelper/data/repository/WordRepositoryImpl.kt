package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.ui.mapper.toCognateEntities
import com.xty.englishhelper.ui.mapper.toDomain
import com.xty.englishhelper.ui.mapper.toEntity
import com.xty.englishhelper.ui.mapper.toSimilarWordEntities
import com.xty.englishhelper.ui.mapper.toSynonymEntities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordRepositoryImpl @Inject constructor(
    private val wordDao: WordDao
) : WordRepository {

    override fun getWordsByDictionary(dictionaryId: Long): Flow<List<WordDetails>> =
        wordDao.getWordsByDictionary(dictionaryId).map { list -> list.map { it.toDomain() } }

    override fun searchWords(dictionaryId: Long, query: String): Flow<List<WordDetails>> =
        wordDao.searchWords(dictionaryId, query).map { list -> list.map { it.toDomain() } }

    override suspend fun getWordById(wordId: Long): WordDetails? =
        wordDao.getWordById(wordId)?.toDomain()

    override suspend fun insertWord(word: WordDetails): Long {
        val wordId = wordDao.insertWord(word.toEntity())
        saveRelatedEntities(word, wordId)
        return wordId
    }

    override suspend fun updateWord(word: WordDetails) {
        wordDao.updateWord(word.toEntity())
        // Clear and re-insert related entities
        wordDao.deleteSynonymsByWordId(word.id)
        wordDao.deleteSimilarWordsByWordId(word.id)
        wordDao.deleteCognatesByWordId(word.id)
        saveRelatedEntities(word, word.id)
    }

    override suspend fun deleteWord(wordId: Long) =
        wordDao.deleteWord(wordId)

    private suspend fun saveRelatedEntities(word: WordDetails, wordId: Long) {
        if (word.synonyms.isNotEmpty()) {
            wordDao.insertSynonyms(word.toSynonymEntities(wordId))
        }
        if (word.similarWords.isNotEmpty()) {
            wordDao.insertSimilarWords(word.toSimilarWordEntities(wordId))
        }
        if (word.cognates.isNotEmpty()) {
            wordDao.insertCognates(word.toCognateEntities(wordId))
        }
    }
}
