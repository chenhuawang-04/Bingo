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
    @Query("SELECT * FROM word_clusters WHERE dictionary_id = :dictionaryId ORDER BY normalized_name")
    suspend fun getClusters(dictionaryId: Long): List<WordClusterEntity>

    @Query("""
        SELECT c.* FROM word_clusters c
        INNER JOIN word_cluster_members m ON m.cluster_id = c.id
        WHERE m.word_id = :wordId ORDER BY c.normalized_name
    """)
    suspend fun getClustersForWord(wordId: Long): List<WordClusterEntity>

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
