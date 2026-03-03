package com.xty.englishhelper.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.xty.englishhelper.data.local.entity.WordPoolEntity
import com.xty.englishhelper.data.local.entity.WordPoolMemberEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails

@Dao
interface WordPoolDao {
    @Insert
    suspend fun insertPool(pool: WordPoolEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<WordPoolMemberEntity>)

    @Query("DELETE FROM word_pools WHERE dictionary_id = :dictionaryId AND strategy = :strategy")
    suspend fun deleteByDictionaryAndStrategy(dictionaryId: Long, strategy: String)

    @Query("SELECT * FROM word_pools WHERE id IN (:poolIds)")
    suspend fun getPoolsByIds(poolIds: List<Long>): List<WordPoolEntity>

    @Query("SELECT pool_id FROM word_pool_members WHERE word_id = :wordId")
    suspend fun getPoolIdsForWord(wordId: Long): List<Long>

    @Query("SELECT word_id, pool_id FROM word_pool_members WHERE pool_id IN (:poolIds)")
    suspend fun getMembershipsByPoolIds(poolIds: List<Long>): List<WordPoolMembership>

    @Transaction
    @Query(
        """
        SELECT w.* FROM words w
        INNER JOIN word_pool_members wpm ON w.id = wpm.word_id
        WHERE wpm.pool_id IN (:poolIds)
        """
    )
    suspend fun getWordWithDetailsByPoolIds(poolIds: List<Long>): List<WordWithDetails>

    @Query("SELECT COUNT(*) FROM word_pools WHERE dictionary_id = :dictionaryId")
    suspend fun countPools(dictionaryId: Long): Int

    @Query(
        """
        SELECT wpm.word_id, wpm.pool_id FROM word_pool_members wpm
        JOIN word_pools wp ON wpm.pool_id = wp.id
        WHERE wp.dictionary_id = :dictionaryId
        """
    )
    suspend fun getAllMemberships(dictionaryId: Long): List<WordPoolMembership>

    @Query(
        """
        SELECT DISTINCT strategy, algorithm_version
        FROM word_pools
        WHERE dictionary_id = :dictionaryId
        """
    )
    suspend fun getPoolVersionInfo(dictionaryId: Long): List<PoolVersionInfo>

    @Query(
        """
        SELECT wa.word_id, wa.associated_word_id
        FROM word_associations wa
        JOIN words w1 ON wa.word_id = w1.id
        JOIN words w2 ON wa.associated_word_id = w2.id
        WHERE w1.dictionary_id = :dictionaryId
          AND w2.dictionary_id = :dictionaryId
          AND wa.similarity >= :minSimilarity
        """
    )
    suspend fun getAssociationsInDictionary(
        dictionaryId: Long,
        minSimilarity: Float = 0.35f
    ): List<AssociationPair>
}

data class WordPoolMembership(
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "pool_id") val poolId: Long
)

data class PoolVersionInfo(
    val strategy: String,
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: String
)

data class AssociationPair(
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "associated_word_id") val associatedWordId: Long
)
