package com.xty.englishhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.xty.englishhelper.data.local.entity.WordClusterEntity
import com.xty.englishhelper.data.local.entity.WordClusterMemberEntity

@Dao
interface WordClusterDao {
    @Query("""
        SELECT c.id, c.dictionary_id AS dictionaryId, c.name, COUNT(m.word_id) AS memberCount
        FROM word_clusters c LEFT JOIN word_cluster_members m ON m.cluster_id = c.id
        WHERE c.dictionary_id = :dictionaryId
        GROUP BY c.id ORDER BY c.normalized_name
    """)
    suspend fun getClusters(dictionaryId: Long): List<WordClusterProjection>

    @Query("""
        SELECT c.id, c.dictionary_id AS dictionaryId, c.name, COUNT(all_members.word_id) AS memberCount
        FROM word_clusters c
        INNER JOIN word_cluster_members m ON m.cluster_id = c.id
        LEFT JOIN word_cluster_members all_members ON all_members.cluster_id = c.id
        WHERE m.word_id = :wordId
        GROUP BY c.id ORDER BY c.normalized_name
    """)
    suspend fun getClustersForWord(wordId: Long): List<WordClusterProjection>

    @Query("""
        SELECT c.id, c.dictionary_id AS dictionaryId, c.name, COUNT(all_members.word_id) AS memberCount
        FROM word_clusters c
        INNER JOIN word_cluster_members member ON member.cluster_id = c.id AND member.word_id = :wordId
        LEFT JOIN word_cluster_members all_members ON all_members.cluster_id = c.id
        WHERE c.id = :clusterId GROUP BY c.id
    """)
    suspend fun getClusterForMember(clusterId: Long, wordId: Long): WordClusterProjection?

    @Query("SELECT word_id FROM word_cluster_members WHERE cluster_id = :clusterId ORDER BY created_at, word_id")
    suspend fun getMemberIds(clusterId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCluster(cluster: WordClusterEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMember(member: WordClusterMemberEntity)

    @Query("DELETE FROM word_cluster_members WHERE cluster_id = :clusterId AND word_id = :wordId")
    suspend fun removeMember(clusterId: Long, wordId: Long)

    @Query("DELETE FROM word_clusters WHERE id = :clusterId")
    suspend fun deleteCluster(clusterId: Long)

    @Transaction
    suspend fun createAndAttach(cluster: WordClusterEntity, wordId: Long): Long {
        val id = insertCluster(cluster)
        insertMember(WordClusterMemberEntity(id, wordId, cluster.createdAt))
        return id
    }
}

data class WordClusterProjection(
    val id: Long,
    val dictionaryId: Long,
    val name: String,
    val memberCount: Int
)
