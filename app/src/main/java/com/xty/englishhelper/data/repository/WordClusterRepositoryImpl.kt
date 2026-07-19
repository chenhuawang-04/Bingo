package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.WordClusterDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.entity.WordClusterEntity
import com.xty.englishhelper.data.local.entity.WordClusterMemberEntity
import com.xty.englishhelper.data.local.dao.WordClusterProjection
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.domain.model.WordCluster
import com.xty.englishhelper.domain.model.WordClusterReview
import com.xty.englishhelper.domain.model.WordClusterBackup
import com.xty.englishhelper.domain.repository.WordClusterRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordClusterRepositoryImpl @Inject constructor(
    private val clusterDao: WordClusterDao,
    private val wordDao: WordDao
) : WordClusterRepository {
    override suspend fun getClusters(dictionaryId: Long): List<WordCluster> =
        clusterDao.getClusters(dictionaryId).map { it.toModel() }

    override suspend fun getClustersForWord(wordId: Long): List<WordCluster> =
        clusterDao.getClustersForWord(wordId).map { it.toModel() }

    override suspend fun getClusterReview(clusterId: Long, excludeWordId: Long): WordClusterReview? {
        val entity = clusterDao.getClusterForMember(clusterId, excludeWordId) ?: return null
        val ids = clusterDao.getMemberIds(clusterId).filterNot { it == excludeWordId }
        val wordsById = if (ids.isEmpty()) emptyMap() else wordDao.getWordsByIds(ids).associateBy { it.word.id }
        return WordClusterReview(entity.toModel(), ids.mapNotNull { wordsById[it]?.toDomain() })
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

    override suspend fun exportBackup(
        dictionaryId: Long,
        wordIdToUid: Map<Long, String>
    ): List<WordClusterBackup> = clusterDao.getClusters(dictionaryId).map { cluster ->
        WordClusterBackup(
            name = cluster.name,
            memberWordUids = clusterDao.getMemberIds(cluster.id).mapNotNull(wordIdToUid::get)
        )
    }

    override suspend fun restoreBackup(
        dictionaryId: Long,
        backup: List<WordClusterBackup>,
        wordUidToId: Map<String, Long>
    ) {
        backup.forEach { saved ->
            val cleanName = saved.name.trim().replace(Regex("\\s+"), " ").take(40)
            if (cleanName.isEmpty()) return@forEach
            val now = System.currentTimeMillis()
            val clusterId = runCatching {
                clusterDao.insertCluster(
                    WordClusterEntity(0, dictionaryId, cleanName, cleanName.lowercase(), now, now)
                )
            }.getOrNull() ?: return@forEach
            saved.memberWordUids.mapNotNull(wordUidToId::get).distinct().forEach { wordId ->
                clusterDao.insertMember(WordClusterMemberEntity(clusterId, wordId, now))
            }
        }
    }

    private fun WordClusterEntity.toModel(count: Int) = WordCluster(id, dictionaryId, name, count)
    private fun WordClusterProjection.toModel() = WordCluster(id, dictionaryId, name, memberCount)
}
