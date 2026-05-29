package com.xty.englishhelper.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xty.englishhelper.data.local.entity.WordEdgeEntity

@Dao
interface WordEdgeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEdges(edges: List<WordEdgeEntity>)

    @Query("DELETE FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun deleteByDictionary(dictionaryId: Long)

    @Query("SELECT word_id_a, word_id_b, edge_type FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun getAllEdges(dictionaryId: Long): List<EdgeProjection>
}

data class EdgeProjection(
    @ColumnInfo(name = "word_id_a") val wordIdA: Long,
    @ColumnInfo(name = "word_id_b") val wordIdB: Long,
    @ColumnInfo(name = "edge_type") val edgeType: String
)
