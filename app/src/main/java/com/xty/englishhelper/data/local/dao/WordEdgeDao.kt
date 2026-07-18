package com.xty.englishhelper.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.local.entity.WordEdgeExcludedEntity
import com.xty.englishhelper.data.local.entity.WordEdgeStagingEntity

@Dao
interface WordEdgeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEdges(edges: List<WordEdgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdge(edge: WordEdgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEdgeIfAbsent(edge: WordEdgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStagedEdges(edges: List<WordEdgeStagingEntity>)

    @Query("SELECT * FROM word_edge_staging WHERE dictionary_id = :dictionaryId ORDER BY id ASC")
    suspend fun getStagedEdges(dictionaryId: Long): List<WordEdgeStagingEntity>

    @Query("DELETE FROM word_edge_staging WHERE dictionary_id = :dictionaryId")
    suspend fun deleteStagedEdges(dictionaryId: Long)

    @Query("DELETE FROM word_edge_staging WHERE dictionary_id = :dictionaryId AND (word_id_a = :wordId OR word_id_b = :wordId)")
    suspend fun deleteStagedEdgesForWord(dictionaryId: Long, wordId: Long)

    @Query(
        "DELETE FROM word_edge_staging WHERE dictionary_id = :dictionaryId AND (" +
            "(word_id_a = :wordId AND word_id_b IN (:otherIds)) OR " +
            "(word_id_b = :wordId AND word_id_a IN (:otherIds)))"
    )
    suspend fun deleteStagedEdgesForWordAgainst(dictionaryId: Long, wordId: Long, otherIds: List<Long>)

    @Query(
        "SELECT COUNT(*) FROM word_edge_staging WHERE dictionary_id = :dictionaryId AND (" +
            "(word_id_a = :wordId AND word_id_b IN (:otherIds)) OR " +
            "(word_id_b = :wordId AND word_id_a IN (:otherIds)))"
    )
    suspend fun countStagedEdgesForWordAgainst(dictionaryId: Long, wordId: Long, otherIds: List<Long>): Int

    @Query("DELETE FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun deleteByDictionary(dictionaryId: Long)

    @Query(
        "DELETE FROM word_edges WHERE dictionary_id = :dictionaryId " +
            "AND (evidence_source IS NULL OR evidence_source NOT IN (:protectedSources))"
    )
    suspend fun deleteUnprotectedEdgesByDictionary(
        dictionaryId: Long,
        protectedSources: List<String>
    )

    @Query("DELETE FROM word_edges WHERE dictionary_id = :dictionaryId AND evidence_source IN (:sources)")
    suspend fun deleteGeneratedEdgesBySources(dictionaryId: Long, sources: List<String>)

    @Query("DELETE FROM word_edges WHERE dictionary_id = :dictionaryId AND (word_id_a = :wordId OR word_id_b = :wordId)")
    suspend fun deleteEdgesForWord(dictionaryId: Long, wordId: Long)

    @Query(
        "DELETE FROM word_edges WHERE dictionary_id = :dictionaryId " +
            "AND (word_id_a = :wordId OR word_id_b = :wordId) " +
            "AND (evidence_source IS NULL OR evidence_source NOT IN (:protectedSources))"
    )
    suspend fun deleteGeneratedEdgesForWord(
        dictionaryId: Long,
        wordId: Long,
        protectedSources: List<String>
    )

    @Query("DELETE FROM word_edge_excluded WHERE dictionary_id = :dictionaryId AND (word_id_a = :wordId OR word_id_b = :wordId)")
    suspend fun deleteExcludedForWord(dictionaryId: Long, wordId: Long)

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

    @Query(
        "DELETE FROM word_edges WHERE dictionary_id = :dictionaryId AND (" +
            "(word_id_a = :wordId AND word_id_b IN (:otherIds)) OR " +
            "(word_id_b = :wordId AND word_id_a IN (:otherIds))) " +
            "AND (evidence_source IS NULL OR evidence_source NOT IN (:protectedSources))"
    )
    suspend fun deleteGeneratedEdgesForWordAgainst(
        dictionaryId: Long,
        wordId: Long,
        otherIds: List<Long>,
        protectedSources: List<String>
    )

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

    @Query("SELECT * FROM word_edges WHERE dictionary_id = :dictionaryId AND evidence_source = :source ORDER BY id ASC")
    suspend fun getEdgesBySource(dictionaryId: Long, source: String): List<WordEdgeEntity>

    @Query(
        """
        SELECT * FROM word_edges
        WHERE dictionary_id = :dictionaryId
          AND (
            (word_id_a = :wordId AND word_id_b = :relatedWordId) OR
            (word_id_a = :relatedWordId AND word_id_b = :wordId)
          )
        ORDER BY id ASC
        """
    )
    suspend fun getEdgesBetweenWords(
        dictionaryId: Long,
        wordId: Long,
        relatedWordId: Long
    ): List<WordEdgeEntity>

    @Query(
        """
        SELECT * FROM word_edges
        WHERE dictionary_id = :dictionaryId
          AND word_id_a != word_id_b
          AND confidence >= :minConfidence
          AND (
            (word_id_a = :wordId) OR
            (word_id_b = :wordId)
          )
        ORDER BY confidence DESC, relation_strength DESC, learning_value DESC, id ASC
        """
    )
    suspend fun getEdgesForWord(
        dictionaryId: Long,
        wordId: Long,
        minConfidence: Double
    ): List<WordEdgeEntity>

    @Query(
        "SELECT * FROM word_edges " +
            "WHERE dictionary_id = :dictionaryId AND id > :lastId " +
            "ORDER BY id ASC LIMIT :limit"
    )
    suspend fun getEdgesPageFull(dictionaryId: Long, lastId: Long, limit: Int): List<WordEdgeEntity>

    @Query(
        "SELECT * FROM word_edges WHERE dictionary_id = :dictionaryId AND id > :lastId " +
            "AND confidence > 0 " +
            "AND (evidence_source IS NULL OR evidence_source NOT IN (:excludedSources)) " +
            "ORDER BY id ASC LIMIT :limit"
    )
    suspend fun getEdgesPageExcludingSources(
        dictionaryId: Long,
        lastId: Long,
        excludedSources: List<String>,
        limit: Int
    ): List<WordEdgeEntity>

    @Query(
        "SELECT * FROM word_edges " +
            "WHERE dictionary_id = :dictionaryId AND id > :lastId " +
            "AND confidence >= :minConfidence AND status != :excludedStatus " +
            "ORDER BY id ASC LIMIT :limit"
    )
    suspend fun getSignificantEdgesPageFull(
        dictionaryId: Long,
        lastId: Long,
        minConfidence: Double,
        excludedStatus: String,
        limit: Int
    ): List<WordEdgeEntity>

    @Query(
        "SELECT * FROM word_edges " +
            "WHERE dictionary_id = :dictionaryId AND id > :lastId " +
            "AND confidence >= :minConfidence AND status != :excludedStatus " +
            "AND (evidence_source IS NULL OR evidence_source NOT IN (:excludedSources)) " +
            "ORDER BY id ASC LIMIT :limit"
    )
    suspend fun getQualityFirstEdgesPageFull(
        dictionaryId: Long,
        lastId: Long,
        minConfidence: Double,
        excludedStatus: String,
        excludedSources: List<String>,
        limit: Int
    ): List<WordEdgeEntity>

    @Query("SELECT COUNT(*) FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun countEdges(dictionaryId: Long): Int

    @Query("SELECT id, word_id_a, word_id_b, edge_type, relation_strength, confidence FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun getGraphEdges(dictionaryId: Long): List<GraphEdgeProjection>

    @Query(
        "SELECT COUNT(*) FROM word_edges WHERE dictionary_id = :dictionaryId AND confidence > 0 " +
            "AND (evidence_source IS NULL OR evidence_source NOT IN (:excludedSources))"
    )
    suspend fun countEdgesExcludingSources(dictionaryId: Long, excludedSources: List<String>): Int

    @Query(
        "SELECT id, word_id_a, word_id_b, edge_type, relation_strength, confidence " +
            "FROM word_edges " +
            "WHERE dictionary_id = :dictionaryId AND id > :lastId " +
            "ORDER BY id ASC LIMIT :limit"
    )
    suspend fun getGraphEdgesPage(dictionaryId: Long, lastId: Long, limit: Int): List<GraphEdgeProjection>

    @Query(
        "SELECT id, word_id_a, word_id_b, edge_type, relation_strength, confidence " +
            "FROM word_edges " +
            "WHERE dictionary_id = :dictionaryId AND id > :lastId " +
            "AND word_id_a != word_id_b " +
            "AND confidence >= :minConfidence AND status != :excludedStatus " +
            "ORDER BY id ASC LIMIT :limit"
    )
    suspend fun getEffectiveGraphEdgesPage(
        dictionaryId: Long,
        lastId: Long,
        minConfidence: Double,
        excludedStatus: String,
        limit: Int
    ): List<GraphEdgeProjection>

    @Query("SELECT id, word_id_a, word_id_b, edge_type, status, learning_value, relation_strength, confidence, reason, warning_note, evidence_source, register, example_sentence, difficulty_cefr FROM word_edges WHERE dictionary_id = :dictionaryId")
    suspend fun getAllEdges(dictionaryId: Long): List<EdgeProjection>

    @Query("SELECT id, word_id_a, word_id_b, edge_type, status, learning_value, relation_strength, confidence, reason, warning_note, evidence_source, register, example_sentence, difficulty_cefr FROM word_edges WHERE id = :edgeId LIMIT 1")
    suspend fun getEdgeDetail(edgeId: Long): EdgeProjection?

    @Query(
        "UPDATE word_edges SET status = :status, confidence = :confidence, " +
            "updated_at = MAX(updated_at + 1, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)) " +
            "WHERE id = :id AND (evidence_source IS NULL OR evidence_source != 'user_note')"
    )
    suspend fun updateEdgeStatus(id: Long, status: String, confidence: Double)

    @Query(
        "UPDATE word_edges SET status = :status, confidence = :confidence, evidence_source = :evidenceSource, " +
            "updated_at = MAX(updated_at + 1, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)) " +
            "WHERE id = :id"
    )
    suspend fun promoteUserEdge(
        id: Long,
        status: String,
        confidence: Double,
        evidenceSource: String
    )

    @Query(
        "UPDATE word_edges SET dictionary_id = :dictionaryId, status = :status, " +
            "learning_value = :learningValue, relation_strength = :relationStrength, " +
            "confidence = :confidence, reason = :reason, warning_note = :warningNote, " +
            "evidence_source = :evidenceSource, register = :register, " +
            "example_sentence = :exampleSentence, difficulty_cefr = :difficultyCefr, " +
            "updated_at = MAX(updated_at + 1, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)) " +
            "WHERE id = :id"
    )
    suspend fun updateGeneratedEdge(
        id: Long,
        dictionaryId: Long,
        status: String,
        learningValue: Int,
        relationStrength: Int,
        confidence: Double,
        reason: String?,
        warningNote: String?,
        evidenceSource: String?,
        register: String?,
        exampleSentence: String?,
        difficultyCefr: String?
    )

    @Query(
        "UPDATE word_edges SET dictionary_id = :dictionaryId, status = :status, " +
            "learning_value = :learningValue, relation_strength = :relationStrength, " +
            "confidence = :confidence, reason = :reason, warning_note = :warningNote, " +
            "evidence_source = :evidenceSource, register = :register, " +
            "example_sentence = :exampleSentence, difficulty_cefr = :difficultyCefr, " +
            "created_at = :createdAt, updated_at = :updatedAt WHERE id = :id"
    )
    suspend fun updateSyncedEdge(
        id: Long,
        dictionaryId: Long,
        status: String,
        learningValue: Int,
        relationStrength: Int,
        confidence: Double,
        reason: String?,
        warningNote: String?,
        evidenceSource: String?,
        register: String?,
        exampleSentence: String?,
        difficultyCefr: String?,
        createdAt: Long,
        updatedAt: Long
    )

    @Query("DELETE FROM word_edges WHERE id = :id")
    suspend fun deleteEdgeById(id: Long)

    @Query("DELETE FROM word_edges WHERE id IN (:ids)")
    suspend fun deleteEdgesByIds(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExcluded(excluded: List<WordEdgeExcludedEntity>)

    @Query("DELETE FROM word_edge_excluded WHERE dictionary_id = :dictionaryId")
    suspend fun deleteExcludedByDictionary(dictionaryId: Long)
}

data class GraphEdgeProjection(
    val id: Long,
    @ColumnInfo(name = "word_id_a") val wordIdA: Long,
    @ColumnInfo(name = "word_id_b") val wordIdB: Long,
    @ColumnInfo(name = "edge_type") val edgeType: String,
    @ColumnInfo(name = "relation_strength") val relationStrength: Int,
    @ColumnInfo(name = "confidence") val confidence: Double
)

data class EdgeProjection(
    val id: Long,
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
