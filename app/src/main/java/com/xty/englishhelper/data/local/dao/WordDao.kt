package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.xty.englishhelper.data.local.entity.CognateEntity
import com.xty.englishhelper.data.local.entity.SimilarWordEntity
import com.xty.englishhelper.data.local.entity.SynonymEntity
import com.xty.englishhelper.data.local.entity.WordEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Transaction
    @Query("SELECT * FROM words WHERE dictionary_id = :dictionaryId ORDER BY spelling ASC")
    fun getWordsByDictionary(dictionaryId: Long): Flow<List<WordWithDetails>>

    @Transaction
    @Query("SELECT * FROM words WHERE dictionary_id = :dictionaryId AND spelling LIKE '%' || :query || '%' ORDER BY spelling ASC")
    fun searchWords(dictionaryId: Long, query: String): Flow<List<WordWithDetails>>

    @Transaction
    @Query("SELECT * FROM words WHERE id = :wordId")
    suspend fun getWordById(wordId: Long): WordWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity): Long

    @Update
    suspend fun updateWord(word: WordEntity)

    @Query("DELETE FROM words WHERE id = :wordId")
    suspend fun deleteWord(wordId: Long)

    // Synonyms
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSynonyms(synonyms: List<SynonymEntity>)

    @Query("DELETE FROM synonyms WHERE word_id = :wordId")
    suspend fun deleteSynonymsByWordId(wordId: Long)

    // Similar words
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSimilarWords(similarWords: List<SimilarWordEntity>)

    @Query("DELETE FROM similar_words WHERE word_id = :wordId")
    suspend fun deleteSimilarWordsByWordId(wordId: Long)

    // Cognates
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCognates(cognates: List<CognateEntity>)

    @Query("DELETE FROM cognates WHERE word_id = :wordId")
    suspend fun deleteCognatesByWordId(wordId: Long)
}
