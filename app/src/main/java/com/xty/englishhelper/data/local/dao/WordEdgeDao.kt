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

    /**
     * 删除某词与指定若干「相对词」之间的边（双向匹配：该词可能落在 word_id_a 或 word_id_b）。
     * 用于块级续传清理：只清掉待重做块覆盖的前驱区残边，保留已提交块的边。
     * 注意 SQLite IN(...) 参数上限约 999，调用方需把 otherIds 分批（≤900）。
     */
    @Query(
        "DELETE FROM word_edges WHERE dictionary_id = :dictionaryId AND (" +
            "(word_id_a = :wordId AND word_id_b IN (:otherIds)) OR " +
            "(word_id_b = :wordId AND word_id_a IN (:otherIds)))"
    )
    suspend fun deleteEdgesForWordAgainst(dictionaryId: Long, wordId: Long, otherIds: List<Long>)

    /**
     * 统计某词与指定若干「相对词」之间已存在的边数（双向匹配）。
     * 用于块级续传时把"已提交块"已落库的边数作为本词计数起点，避免显示从 0 起、误以为前面的块白做了。
     * 同样受 SQLite IN(...) ≈999 上限约束，调用方需把 otherIds 分批（≤900）后求和。
     */
    @Query(
        "SELECT COUNT(*) FROM word_edges WHERE dictionary_id = :dictionaryId AND (" +
            "(word_id_a = :wordId AND word_id_b IN (:otherIds)) OR " +
            "(word_id_b = :wordId AND word_id_a IN (:otherIds)))"
    )
    suspend fun countEdgesForWordAgainst(dictionaryId: Long, wordId: Long, otherIds: List<Long>): Int

    @Query("SELECT * FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun getAllEdgesFull(dictionaryId: Long): List<WordEdgeEntity>

    @Query("SELECT COUNT(*) FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun countEdges(dictionaryId: Long): Int

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
