package com.xty.englishhelper.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.xty.englishhelper.data.local.entity.CognateEntity
import com.xty.englishhelper.data.local.entity.SimilarWordEntity
import com.xty.englishhelper.data.local.entity.SynonymEntity
import com.xty.englishhelper.data.local.entity.WordAssociationEntity
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

    @Transaction
    @Query("SELECT * FROM words WHERE dictionary_id = :dictionaryId AND normalized_spelling = :normalizedSpelling LIMIT 1")
    suspend fun findByNormalizedSpelling(dictionaryId: Long, normalizedSpelling: String): WordWithDetails?

    // Batch query existing words (for linked word navigation)
    @Query("""
        SELECT id, normalized_spelling FROM words
        WHERE dictionary_id = :dictionaryId
        AND normalized_spelling IN (:normalizedSpellings)
    """)
    suspend fun findExistingWordIds(dictionaryId: Long, normalizedSpellings: List<String>): List<WordIdSpelling>

    // Word association CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssociations(associations: List<WordAssociationEntity>)

    @Query("DELETE FROM word_associations WHERE word_id = :wordId OR associated_word_id = :wordId")
    suspend fun deleteAssociationsForWord(wordId: Long)

    @Query("""
        SELECT wa.associated_word_id AS wordId, w.spelling, wa.similarity, wa.common_segments_json AS commonSegmentsJson
        FROM word_associations wa
        INNER JOIN words w ON w.id = wa.associated_word_id
        WHERE wa.word_id = :wordId
        ORDER BY wa.similarity DESC
        LIMIT 20
    """)
    suspend fun getAssociationsForWord(wordId: Long): List<AssociationWithSpelling>

    // Lightweight projection (for association computation)
    @Query("SELECT id, decomposition_json AS decompositionJson FROM words WHERE dictionary_id = :dictionaryId AND id != :excludeWordId AND decomposition_json != '[]'")
    suspend fun getAllDecompositionsInDictionary(dictionaryId: Long, excludeWordId: Long): List<WordDecompositionProjection>

    // Delete all associations in dictionary (for batch import)
    @Query("DELETE FROM word_associations WHERE word_id IN (SELECT id FROM words WHERE dictionary_id = :dictionaryId)")
    suspend fun deleteAllAssociationsInDictionary(dictionaryId: Long)

    // For article matching: load all words' id, dictionaryId, normalizedSpelling, inflectionsJson
    @Query("SELECT id, dictionary_id, normalized_spelling, inflections_json FROM words")
    suspend fun getAllWordsForMatching(): List<WordMatchProjection>
}

data class WordIdSpelling(val id: Long, @ColumnInfo(name = "normalized_spelling") val normalizedSpelling: String)
data class AssociationWithSpelling(val wordId: Long, val spelling: String, val similarity: Float, val commonSegmentsJson: String)
data class WordDecompositionProjection(val id: Long, val decompositionJson: String)
