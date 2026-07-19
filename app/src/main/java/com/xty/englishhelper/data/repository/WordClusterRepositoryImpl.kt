package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.WordClusterDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.entity.WordClusterEntity
import com.xty.englishhelper.data.local.entity.WordClusterMemberEntity
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.domain.model.WordCluster
import com.xty.englishhelper.domain.model.WordClusterReview
import com.xty.englishhelper.domain.repository.WordClusterRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordClusterRepositoryImpl @Inject constructor(
    private val clusterDao: WordClusterDao,
    private val wordDao: WordDao
) : WordClusterRepository {
    override suspend fun getClusters(dictionaryId: Long): List<WordCluster> =
        clusterDao.getClusters(dictionaryId).map { it.toModel(clusterDao.getMemberIds(it.id).size) }

    override suspend fun getClustersForWord(wordId: Long): List<WordCluster> =
        clusterDao.getClustersForWord(wordId).map { it.toModel(clusterDao.getMemberIds(it.id).size) }

    override suspend fun getClusterReview(clusterId: Long, excludeWordId: Long): WordClusterReview? {
        val entity = clusterDao.getClustersForWord(excludeWordId).firstOrNull { it.id == clusterId }
            ?: return null
        val ids = clusterDao.getMemberIds(clusterId).filterNot { it == excludeWordId }
        val wordsById = if (ids.isEmpty()) emptyMap() else wordDao.getWordsByIds(ids).associateBy { it.word.id }
        return WordClusterReview(entity.toModel(ids.size + 1), ids.mapNotNull { wordsById[it]?.toDomain() })
    }

    override suspend fun createCluster(dictionaryId: Long, name: String, initialWordId: Long): WordCluster {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        require(cleanName.isNotEmpty()) { "词簇名称不能为空" }
        require(cleanName.length <= 40) { "词簇名称不能超过 40 个字符" }
        val now = System.currentTimeMillis()
        val entity = WordClusterEntity(
            dictionaryId = dictionaryId,
            name = cleanName,
            normalizedName = cleanName.lowercase(),
            createdAt = now,
            updatedAt = now
        )
        val id = clusterDao.createAndAttach(entity, initialWordId)
        return entity.copy(id = id).toModel(1)
    }

    override suspend fun setWordMembership(clusterId: Long, wordId: Long, included: Boolean) {
        if (included) clusterDao.insertMember(WordClusterMemberEntity(clusterId, wordId, System.currentTimeMillis()))
        else clusterDao.removeMember(clusterId, wordId)
    }

    override suspend fun deleteCluster(clusterId: Long) = clusterDao.deleteCluster(clusterId)

    private fun WordClusterEntity.toModel(count: Int) = WordCluster(id, dictionaryId, name, count)
}
