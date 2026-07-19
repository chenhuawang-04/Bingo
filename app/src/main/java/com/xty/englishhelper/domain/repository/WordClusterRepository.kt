package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.WordCluster
import com.xty.englishhelper.domain.model.WordClusterReview
import com.xty.englishhelper.domain.model.WordClusterBackup

interface WordClusterRepository {
    suspend fun getClusters(dictionaryId: Long): List<WordCluster>
    suspend fun getClustersForWord(wordId: Long): List<WordCluster>
    suspend fun getClusterReviewsForWord(wordId: Long): List<WordClusterReview>
    suspend fun getClusterReview(clusterId: Long, excludeWordId: Long): WordClusterReview?
    suspend fun createCluster(dictionaryId: Long, name: String, initialWordId: Long): WordCluster
    suspend fun setWordMembership(clusterId: Long, wordId: Long, included: Boolean)
    suspend fun deleteCluster(clusterId: Long)
    suspend fun exportBackup(dictionaryId: Long, wordIdToUid: Map<Long, String>): List<WordClusterBackup>
    suspend fun restoreBackup(dictionaryId: Long, backup: List<WordClusterBackup>, wordUidToId: Map<String, Long>)
}
