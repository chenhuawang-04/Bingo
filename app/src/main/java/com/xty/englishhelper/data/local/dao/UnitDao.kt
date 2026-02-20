package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.xty.englishhelper.data.local.entity.UnitEntity
import com.xty.englishhelper.data.local.entity.UnitWordCrossRef
import com.xty.englishhelper.data.local.relation.UnitWithWordCount
import com.xty.englishhelper.data.local.relation.WordWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitDao {

    @Query(
        """
        SELECT u.id, u.dictionary_id, u.name, u.default_repeat_count, u.created_at,
               COUNT(ref.word_id) AS word_count
        FROM units u
        LEFT JOIN unit_word_cross_ref ref ON u.id = ref.unit_id
        WHERE u.dictionary_id = :dictionaryId
        GROUP BY u.id
        ORDER BY u.created_at ASC
        """
    )
    fun getUnitsWithWordCount(dictionaryId: Long): Flow<List<UnitWithWordCount>>

    @Query("SELECT * FROM units WHERE id = :unitId")
    suspend fun getUnitById(unitId: Long): UnitEntity?

    @Query("SELECT * FROM units WHERE dictionary_id = :dictionaryId ORDER BY created_at ASC")
    suspend fun getUnitsByDictionary(dictionaryId: Long): List<UnitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: UnitEntity): Long

    @Query("UPDATE units SET name = :name WHERE id = :unitId")
    suspend fun updateUnitName(unitId: Long, name: String)

    @Query("UPDATE units SET default_repeat_count = :repeatCount WHERE id = :unitId")
    suspend fun updateRepeatCount(unitId: Long, repeatCount: Int)

    @Query("DELETE FROM units WHERE id = :unitId")
    suspend fun deleteUnit(unitId: Long)

    // Cross-ref operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: UnitWordCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<UnitWordCrossRef>)

    @Query("DELETE FROM unit_word_cross_ref WHERE unit_id = :unitId AND word_id = :wordId")
    suspend fun removeCrossRef(unitId: Long, wordId: Long)

    @Query("DELETE FROM unit_word_cross_ref WHERE unit_id = :unitId AND word_id IN (:wordIds)")
    suspend fun removeCrossRefs(unitId: Long, wordIds: List<Long>)

    @Query("SELECT word_id FROM unit_word_cross_ref WHERE unit_id = :unitId")
    suspend fun getWordIdsInUnit(unitId: Long): List<Long>

    @Query("SELECT unit_id FROM unit_word_cross_ref WHERE word_id = :wordId")
    suspend fun getUnitIdsForWord(wordId: Long): List<Long>

    @Transaction
    @Query(
        """
        SELECT w.* FROM words w
        INNER JOIN unit_word_cross_ref ref ON w.id = ref.word_id
        WHERE ref.unit_id = :unitId
        ORDER BY w.spelling ASC
        """
    )
    fun getWordsInUnit(unitId: Long): Flow<List<WordWithDetails>>
}
