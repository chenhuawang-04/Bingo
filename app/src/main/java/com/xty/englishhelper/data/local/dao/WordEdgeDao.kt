package com.xty.englishhelper.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.local.entity.WordEdgeExcludedEntity

@Dao
interface WordEdgeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEdges(edges: List<WordEdgeEntity>)

    @Query("DELETE FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun deleteByDictionary(dictionaryId: Long)

    @Query("DELETE FROM word_edges WHERE dictionary_id = :dictionaryId AND (word_id_a = :wordId OR word_id_b = :wordId)")
    suspend fun deleteEdgesForWord(dictionaryId: Long, wordId: Long)

    @Query("SELECT * FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun getAllEdgesFull(dictionaryId: Long): List<WordEdgeEntity>

    @Query("SELECT word_id_a, word_id_b, edge_type, status, learning_value, relation_strength, confidence, reason, warning_note, evidence_source, register, example_sentence, difficulty_cefr FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun getAllEdges(dictionaryId: Long): List<EdgeProjection>

    @Query("UPDATE word_edges SET status = :status, confidence = :confidence WHERE id = :id")
    suspend fun updateEdgeStatus(id: Long, status: String, confidence: Double)

    @Query("DELETE FROM word_edges WHERE id = :id")
    suspend fun deleteEdgeById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExcluded(excluded: List<WordEdgeExcludedEntity>)

    @Query("DELETE FROM word_edge_excluded WHERE dictionary_id = :dictionaryId")
    suspend fun deleteExcludedByDictionary(dictionaryId: Long)
}

data class EdgeProjection(
    @ColumnInfo(name = "word_id_a") val wordIdA: Long,
    @ColumnInfo(name = "word_id_b") val wordIdB: Long,
    @ColumnInfo(name = "edge_type") val edgeType: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "learning_value") val learningValue: Int,
    @ColumnInfo(name = "relation_strength") val relationStrength: Int,
    @ColumnInfo(name = "confidence") val confidence: Double,
    @ColumnInfo(name = "reason") val reason: String?,
    @ColumnInfo(name = "warning_note") val warningNote: String?,
    @ColumnInfo(name = "evidence_source") val evidenceSource: String?,
    @ColumnInfo(name = "register") val register: String?,
    @ColumnInfo(name = "example_sentence") val exampleSentence: String?,
    @ColumnInfo(name = "difficulty_cefr") val difficultyCefr: String?
)
