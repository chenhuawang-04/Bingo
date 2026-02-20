package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.xty.englishhelper.data.local.entity.DictionaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionaries ORDER BY updated_at DESC")
    fun getAllDictionaries(): Flow<List<DictionaryEntity>>

    @Query("SELECT * FROM dictionaries WHERE id = :id")
    suspend fun getDictionaryById(id: Long): DictionaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dictionary: DictionaryEntity): Long

    @Update
    suspend fun update(dictionary: DictionaryEntity)

    @Query("DELETE FROM dictionaries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE dictionaries SET word_count = (SELECT COUNT(*) FROM words WHERE dictionary_id = :dictionaryId) WHERE id = :dictionaryId")
    suspend fun updateWordCount(dictionaryId: Long)
}
