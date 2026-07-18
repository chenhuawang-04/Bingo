package com.xty.englishhelper.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.xty.englishhelper.data.local.AppDatabase
import com.xty.englishhelper.data.local.dao.GraphEdgeProjection
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.local.entity.WordEdgeStagingEntity
import com.xty.englishhelper.data.local.entity.WordPoolEntity
import com.xty.englishhelper.data.local.entity.WordPoolMemberEntity
import com.xty.englishhelper.data.local.entity.WordPoolStrategyStateEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.data.repository.pool.AiErrorUtils
import com.xty.englishhelper.data.repository.pool.EdgeParser
import com.xty.englishhelper.data.repository.pool.EdgePromptBuilder
import com.xty.englishhelper.data.repository.pool.EdgeRateLimiter
import com.xty.englishhelper.data.repository.pool.EdgeReviewer
import com.xty.englishhelper.data.repository.pool.EntryTypeClassifier
import com.xty.englishhelper.data.repository.pool.NonRetryableEdgeException
import com.xty.englishhelper.data.repository.pool.PoolBuildDataException
import com.xty.englishhelper.data.repository.pool.PoolQualityScorer
import com.xty.englishhelper.data.repository.pool.RetryableEdgeException
import java.util.concurrent.atomic.AtomicInteger
import com.xty.englishhelper.domain.background.PoolBuildLiveMonitor
import com.xty.englishhelper.domain.background.PoolEdgeWriteCoordinator
import com.xty.englishhelper.domain.background.AppResourceCoordinator
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.DictionaryPoolBackup
import com.xty.englishhelper.domain.model.EdgeCluster
import com.xty.englishhelper.domain.model.EdgeNeighbor
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.PoolRetryMode
import com.xty.englishhelper.domain.model.PoolHealthReport
import com.xty.englishhelper.domain.model.PoolRepairResult
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordEdgeBackup
import com.xty.englishhelper.domain.model.WordPoolBackup
import com.xty.englishhelper.domain.model.WordPoolStrategyBackup
import com.xty.englishhelper.domain.model.WordGraph
import com.xty.englishhelper.domain.model.WordGraphCluster
import com.xty.englishhelper.domain.model.WordGraphEdge
import com.xty.englishhelper.domain.model.WordGraphEdgeDetail
import com.xty.englishhelper.domain.model.WordGraphNode
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.pool.BalancedPoolBuild
import com.xty.englishhelper.domain.pool.UnionFind
import com.xty.englishhelper.domain.pool.PoolCandidate
import com.xty.englishhelper.domain.pool.QualityFirstPoolPlanner
import com.xty.englishhelper.domain.pool.QualityPoolEdge
import com.xty.englishhelper.domain.pool.WordPoolEngine
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.domain.repository.ManualChunkCandidate
import com.xty.englishhelper.domain.repository.ManualChunkContext
import com.xty.englishhelper.domain.repository.ManualFillResult
import com.xty.englishhelper.domain.repository.WordEdgeNeighborPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private data class BuiltPoolWithWordIds(
    val focusWordId: Long?,
    val memberWordIds: List<Long>,
    val qualityScore: Int? = null
)

private data class RebuildPoolOutput(
    val pools: List<BuiltPoolWithWordIds>,
    val generatedEdges: List<WordEdgeEntity> = emptyList()
)

private data class PoolStrategyAudit(
    val plannedPools: List<List<Long>>,
    val expectedWordIds: Set<Long>,
    val existingWordIds: Set<Long>,
    val validEdgeCount: Int,
    val storedEdgeCount: Int,
    val connectedComponentCount: Int,
    val oversizedComponentCount: Int,
    val invalidSizePoolCount: Int,
    val disconnectedPoolCount: Int,
    val invalidFocusPoolCount: Int,
    val missingSupportingEdgePoolCount: Int,
    val duplicatePoolCount: Int,
    val layoutMismatch: Boolean
)

private data class QualityEdgeSnapshot(
    val qualityFirstEdges: List<WordEdgeEntity>,
    val balancedEdges: List<WordEdgeEntity>,
    val qualityFirstStoredEdgeCount: Int,
    val balancedStoredEdgeCount: Int,
    val invalidEdgeIds: List<Long>,
    val orphanEdgeCount: Int,
    val selfLoopEdgeCount: Int,
    val unknownTypeEdgeCount: Int
) {
    val effectiveEdges: List<WordEdgeEntity>
        get() = (qualityFirstEdges + balancedEdges).distinctBy(WordEdgeEntity::normalizedKey)
}

private fun WordEdgeEntity.normalizedKey(): String =
    "${minOf(wordIdA, wordIdB)}|${maxOf(wordIdA, wordIdB)}|$edgeType"

private fun WordEdgeEntity.toStagingEntity() = WordEdgeStagingEntity(
    wordIdA = minOf(wordIdA, wordIdB),
    wordIdB = maxOf(wordIdA, wordIdB),
    edgeType = edgeType,
    dictionaryId = dictionaryId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = status,
    learningValue = learningValue,
    relationStrength = relationStrength,
    confidence = confidence,
    reason = reason,
    warningNote = warningNote,
    evidenceSource = evidenceSource,
    register = register,
    exampleSentence = exampleSentence,
    difficultyCefr = difficultyCefr
)

private fun WordEdgeStagingEntity.toLiveEntity() = WordEdgeEntity(
    wordIdA = wordIdA,
    wordIdB = wordIdB,
    edgeType = edgeType,
    dictionaryId = dictionaryId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = status,
    learningValue = learningValue,
    relationStrength = relationStrength,
    confidence = confidence,
    reason = reason,
    warningNote = warningNote,
    evidenceSource = evidenceSource,
    register = register,
    exampleSentence = exampleSentence,
    difficultyCefr = difficultyCefr
)

private fun WordEdgeBackup.toEntity(
    dictionaryId: Long,
    wordUidToId: Map<String, Long>
): WordEdgeEntity {
    val rawA = wordUidToId[wordUidA] ?: throw IllegalStateException("词池边端点不存在：$wordUidA")
    val rawB = wordUidToId[wordUidB] ?: throw IllegalStateException("词池边端点不存在：$wordUidB")
    val now = System.currentTimeMillis()
    return WordEdgeEntity(
        wordIdA = minOf(rawA, rawB),
        wordIdB = maxOf(rawA, rawB),
        edgeType = edgeType,
        dictionaryId = dictionaryId,
        createdAt = createdAt.takeIf { it > 0 } ?: now,
        updatedAt = updatedAt.takeIf { it > 0 } ?: createdAt.takeIf { it > 0 } ?: now,
        status = status,
        learningValue = learningValue,
        relationStrength = relationStrength,
        confidence = confidence,
        reason = reason,
        warningNote = warningNote,
        evidenceSource = evidenceSource,
        register = register,
        exampleSentence = exampleSentence,
        difficultyCefr = difficultyCefr
    )
}

private data class CachedWordGraph(
    val dictionaryId: Long,
    val loadedAtMs: Long,
    val graph: WordGraph
)

/**
 * 单次边生成 AI 调用的结果，把“成功但无边”和“彻底失败”彻底区分开：
 * - [Success] 表示 AI 返回了合规的 JSON 并完成解析，[edges] 可能为空（AI 判定两词无关，
 *   或边全部被硬阈值过滤）——这都属于正常成功，不应当作失败处理。
 * - [Failure] 表示重试 MAX_EDGE_RETRIES 次后仍拿不到合规数据，或遇到不可重试错误，
 *   [error] 为可展示的简短原因。出现 Failure 即应中止构建并报告。
 */
private sealed interface EdgeCallResult {
    data class Success(val edges: List<com.xty.englishhelper.data.repository.pool.ParsedEdge>) : EdgeCallResult
    /** @param attempts 实际尝试次数（用于准确的中止报告，而非硬编码 MAX_EDGE_RETRIES）。 */
    data class Failure(val error: String, val attempts: Int) : EdgeCallResult
}

/**
 * 单个分块并发计算后的产出。
 *
 * 关键设计（修复“重试仍从头开始”的真正根因）：分块失败时返回 [Failed] 而**绝不**在 async 内抛异常。
 * 内层用的是普通 `coroutineScope`（非 supervisor）；若失败块在 async 内直接抛出，结构化并发会**立即**
 * 取消整个作用域——包括正在“按块序提交前缀”的提交循环。于是当某个靠后的块**快速失败**（如首次即遇
 * 不可重试错误）、而靠前的成功块尚在跑较慢的 AI 调用时，提交循环会在把这些已成功的前缀块入库/上报**之前**
 * 就被取消，导致 `committedChunks` 停在 0、续传点退化为 0（用户所见“重试从头来”）。
 * 改为回传 [Failed] 后，失败只会在提交循环按块序 `await` 到该块时才浮现，此时其前的成功前缀必已提交并上报。
 */
private sealed interface ChunkOutcome {
    data class Ok(val edges: List<WordEdgeEntity>) : ChunkOutcome
    data class Failed(val chunkIndex: Int, val error: String, val attempts: Int) : ChunkOutcome
}

@Singleton
class WordPoolRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val wordPoolDao: WordPoolDao,
    private val wordEdgeDao: WordEdgeDao,
    private val wordDao: WordDao,
    private val aiApiClientProvider: AiApiClientProvider,
    private val settingsDataStore: SettingsDataStore,
    private val edgeReviewer: EdgeReviewer,
    private val entryTypeClassifier: EntryTypeClassifier,
    private val liveMonitor: PoolBuildLiveMonitor,
    private val poolEdgeWriteCoordinator: PoolEdgeWriteCoordinator
) : WordPoolRepository {

    private val engine = WordPoolEngine()
    private val graphCacheMutex = Mutex()
    private var graphCache: CachedWordGraph? = null

    companion object {
        private const val MAX_EDGE_RETRIES = 4
        // 宽松重试间隔：当前词累计失败次数 × 10 秒，上限 2 分钟。
        private const val LENIENT_RETRY_UNIT_MS = 10_000L
        private const val LENIENT_RETRY_MAX_MS = 120_000L
        private const val REBUILD_EDGE_PAGE_SIZE = 2_000
        private const val GRAPH_EDGE_PAGE_SIZE = 5_000
        private const val GRAPH_EDGE_INITIAL_CAPACITY_MAX = 65_536
        private const val SIGNIFICANT_EDGE_MIN_CONFIDENCE = 0.3
        private const val EXCLUDED_POOL_EDGE_STATUS = "optional"
        private val BALANCED_EDGE_SOURCES = setOf("balanced_local", "balanced_ai")
        private val PROTECTED_EDGE_SOURCES = listOf("user_note", "balanced_local", "balanced_ai")
        private val REVIEW_EXCLUDED_SOURCES = listOf("user_note", "balanced_local", "balanced_ai")
        private const val QUALITY_FIRST_STAGING_STATE = "__QUALITY_FIRST_STAGING__"
        private const val GRAPH_CACHE_TTL_MS = 30_000L
    }

    override suspend fun exportBackup(
        dictionaryId: Long,
        wordIdToUid: Map<Long, String>
    ): DictionaryPoolBackup = withEdgeWriteLock(dictionaryId) {
        db.withTransaction { exportBackupLocked(dictionaryId, wordIdToUid) }
    }

    private suspend fun exportBackupLocked(
        dictionaryId: Long,
        wordIdToUid: Map<Long, String>
    ): DictionaryPoolBackup {
        val pools = wordPoolDao.getPoolsByDictionary(dictionaryId)
        val membersByPool = wordPoolDao.getAllMemberships(dictionaryId)
            .groupBy({ it.poolId }, { it.wordId })
        val statesByStrategy = wordPoolDao.getStrategyStates(dictionaryId)
            .filter { it.strategy != QUALITY_FIRST_STAGING_STATE }
            .associateBy { it.strategy }
        val strategies = (statesByStrategy.keys + pools.map { it.strategy }).toSortedSet().map { strategy ->
            val strategyPools = pools.filter { it.strategy == strategy }
            val updatedAt = statesByStrategy[strategy]?.updatedAt
                ?: strategyPools.maxOfOrNull { it.updatedAt }
                ?: 0L
            WordPoolStrategyBackup(
                strategy = strategy,
                updatedAt = updatedAt,
                pools = strategyPools.map { pool ->
                    val memberUids = membersByPool[pool.id].orEmpty().map { wordId ->
                        wordIdToUid[wordId]
                            ?: throw IllegalStateException("词池 ${pool.id} 引用了不存在的单词 $wordId")
                    }
                    val focusUid = pool.focusWordId?.let { focusId ->
                        wordIdToUid[focusId]
                            ?: throw IllegalStateException("词池 ${pool.id} 的焦点词不存在：$focusId")
                    }
                    WordPoolBackup(
                        focusWordUid = focusUid,
                        memberWordUids = memberUids,
                        algorithmVersion = pool.algorithmVersion,
                        updatedAt = pool.updatedAt,
                        qualityScore = pool.qualityScore
                    )
                }
            )
        }
        val edges = wordEdgeDao.getAllEdgesFull(dictionaryId).map { edge ->
            val uidA = wordIdToUid[edge.wordIdA]
                ?: throw IllegalStateException("词池边 ${edge.id} 引用了不存在的单词 ${edge.wordIdA}")
            val uidB = wordIdToUid[edge.wordIdB]
                ?: throw IllegalStateException("词池边 ${edge.id} 引用了不存在的单词 ${edge.wordIdB}")
            WordEdgeBackup(
                wordUidA = minOf(uidA, uidB),
                wordUidB = maxOf(uidA, uidB),
                edgeType = edge.edgeType,
                status = edge.status,
                learningValue = edge.learningValue,
                relationStrength = edge.relationStrength,
                confidence = edge.confidence,
                reason = edge.reason,
                warningNote = edge.warningNote,
                evidenceSource = edge.evidenceSource,
                register = edge.register,
                exampleSentence = edge.exampleSentence,
                difficultyCefr = edge.difficultyCefr,
                createdAt = edge.createdAt,
                updatedAt = edge.updatedAt
            )
        }
        return DictionaryPoolBackup(strategies = strategies, edges = edges)
    }

    override suspend fun restoreBackup(
        dictionaryId: Long,
        backup: DictionaryPoolBackup,
        wordUidToId: Map<String, Long>
    ) = withEdgeWriteLock(dictionaryId) {
        val edges = backup.edges.map { edge -> edge.toEntity(dictionaryId, wordUidToId) }
        val seenStrategies = mutableSetOf<String>()
        val resolvedStrategies = backup.strategies.map { snapshot ->
            require(seenStrategies.add(snapshot.strategy)) { "词池策略重复：${snapshot.strategy}" }
            snapshot to snapshot.pools.map { pool ->
                val memberIds = pool.memberWordUids.map { uid ->
                    wordUidToId[uid] ?: throw IllegalStateException("词池引用了不存在的单词：$uid")
                }.distinct()
                val focusId = pool.focusWordUid?.let { uid ->
                    wordUidToId[uid] ?: throw IllegalStateException("词池焦点词不存在：$uid")
                }
                Triple(pool, memberIds, focusId)
            }
        }
        db.withTransaction {
            wordPoolDao.deleteByDictionary(dictionaryId)
            wordPoolDao.deleteStrategyStates(dictionaryId)
            wordEdgeDao.deleteByDictionary(dictionaryId)
            wordEdgeDao.deleteExcludedByDictionary(dictionaryId)
            wordEdgeDao.deleteStagedEdges(dictionaryId)
            edges.chunked(500).forEach { batch -> if (batch.isNotEmpty()) wordEdgeDao.insertEdges(batch) }
            resolvedStrategies.forEach { (snapshot, pools) ->
                wordPoolDao.upsertStrategyState(
                    WordPoolStrategyStateEntity(dictionaryId, snapshot.strategy, snapshot.updatedAt)
                )
                pools.forEach { (pool, memberIds, focusId) ->
                    val poolId = wordPoolDao.insertPool(
                        WordPoolEntity(
                            dictionaryId = dictionaryId,
                            focusWordId = focusId,
                            strategy = snapshot.strategy,
                            algorithmVersion = pool.algorithmVersion,
                            updatedAt = pool.updatedAt,
                            qualityScore = pool.qualityScore
                        )
                    )
                    wordPoolDao.insertMembers(memberIds.map { WordPoolMemberEntity(it, poolId) })
                }
            }
        }
        invalidateGraphCache(dictionaryId)
    }

    override suspend fun getPoolsForWord(wordId: Long): List<WordPool> {
        val poolIds = wordPoolDao.getPoolIdsForWord(wordId)
        if (poolIds.isEmpty()) return emptyList()

        val poolEntities = wordPoolDao.getPoolsByIds(poolIds)
        val allWords = wordPoolDao.getWordWithDetailsByPoolIds(poolIds)

        // Group members by pool
        val memberships = wordPoolDao.getMembershipsByPoolIds(poolIds)
        val poolToWordIds = mutableMapOf<Long, MutableSet<Long>>()
        memberships.forEach { m ->
            poolToWordIds.getOrPut(m.poolId) { mutableSetOf() }.add(m.wordId)
        }

        val wordMap = allWords.associateBy { it.word.id }

        return poolEntities.map { entity ->
            val memberWordIds = poolToWordIds[entity.id] ?: emptySet()
            val memberDetails = memberWordIds
                .filter { it != wordId }
                .mapNotNull { wid -> wordMap[wid]?.toDomain() }
            WordPool(
                poolId = entity.id,
                focusWordId = entity.focusWordId,
                strategy = entity.strategy,
                algorithmVersion = entity.algorithmVersion,
                members = memberDetails
            )
        }.filter { it.members.isNotEmpty() }
    }

    override suspend fun auditQualityFirstPools(dictionaryId: Long): PoolHealthReport {
        val validWordIds = wordDao.getWordLabels(dictionaryId).mapTo(linkedSetOf()) { it.id }
        val edgeSnapshot = loadQualityEdgeSnapshot(dictionaryId, validWordIds)
        val poolEntities = wordPoolDao.getPoolsByDictionary(dictionaryId)
        val strategyStates = wordPoolDao.getStrategyStates(dictionaryId)
            .filter { it.strategy != QUALITY_FIRST_STAGING_STATE }
            .mapTo(mutableSetOf()) { it.strategy }
        val membersByPool = wordPoolDao.getAllMemberships(dictionaryId)
            .groupBy({ it.poolId }, { it.wordId })

        fun auditStrategy(
            strategy: String,
            edges: List<WordEdgeEntity>,
            storedEdgeCount: Int,
            active: Boolean
        ): PoolStrategyAudit {
            val entities = poolEntities.filter { it.strategy == strategy }
            val existingPools = entities.map { membersByPool[it.id].orEmpty().distinct() }
            val activeEdges = if (active) edges else emptyList()
            val planEdges = activeEdges.map { QualityPoolEdge(it.wordIdA, it.wordIdB) }
            val plan = QualityFirstPoolPlanner.plan(planEdges)
            val existingWordIds = existingPools.flatten().toSet()
            val expectedWordIds = plan.coveredWordIds
            val existingCanonical = existingPools.map { it.sorted() }.sortedBy { it.joinToString(",") }
            val plannedCanonical = plan.pools.map { it.distinct().sorted() }.sortedBy { it.joinToString(",") }
            return PoolStrategyAudit(
                plannedPools = plan.pools,
                expectedWordIds = expectedWordIds,
                existingWordIds = existingWordIds,
                validEdgeCount = activeEdges.size,
                storedEdgeCount = if (active) storedEdgeCount else 0,
                connectedComponentCount = plan.connectedComponents.size,
                oversizedComponentCount = plan.oversizedComponentCount,
                invalidSizePoolCount = existingPools.count { it.size !in 2..WordPoolEngine.MAX_POOL_SIZE },
                disconnectedPoolCount = existingPools.count { members ->
                    members.size >= 2 && !QualityFirstPoolPlanner.isConnectedPool(members, planEdges)
                },
                invalidFocusPoolCount = entities.count { entity ->
                    entity.focusWordId != null && entity.focusWordId !in membersByPool[entity.id].orEmpty()
                },
                missingSupportingEdgePoolCount = if (activeEdges.isEmpty() && storedEdgeCount == 0) {
                    existingPools.size
                } else {
                    0
                },
                duplicatePoolCount = existingCanonical.size - existingCanonical.toSet().size,
                layoutMismatch = existingCanonical != plannedCanonical
            )
        }

        val qualityFirstActive = PoolStrategy.QUALITY_FIRST.dbValue in strategyStates ||
            poolEntities.any { it.strategy == PoolStrategy.QUALITY_FIRST.dbValue } ||
            edgeSnapshot.qualityFirstEdges.any { it.evidenceSource != "user_note" }
        val balancedActive = PoolStrategy.BALANCED.dbValue in strategyStates ||
            poolEntities.any { it.strategy == PoolStrategy.BALANCED.dbValue } ||
            edgeSnapshot.balancedEdges.any { it.evidenceSource in BALANCED_EDGE_SOURCES }
        val knownAudits = listOf(
            auditStrategy(
                PoolStrategy.QUALITY_FIRST.dbValue,
                edgeSnapshot.qualityFirstEdges,
                edgeSnapshot.qualityFirstStoredEdgeCount,
                qualityFirstActive
            ),
            auditStrategy(
                PoolStrategy.BALANCED.dbValue,
                edgeSnapshot.balancedEdges,
                edgeSnapshot.balancedStoredEdgeCount,
                balancedActive
            )
        )
        val unknownStrategyPoolCount = poolEntities.count { entity ->
            entity.strategy != PoolStrategy.QUALITY_FIRST.dbValue && entity.strategy != PoolStrategy.BALANCED.dbValue
        }

        return PoolHealthReport(
            dictionaryId = dictionaryId,
            strategy = PoolStrategy.QUALITY_FIRST,
            existingPoolCount = poolEntities.size,
            plannedPoolCount = knownAudits.sumOf { it.plannedPools.size },
            existingCoveredWordCount = knownAudits.flatMap { it.existingWordIds }.toSet().size,
            expectedCoveredWordCount = knownAudits.flatMap { it.expectedWordIds }.toSet().size,
            validEdgeCount = knownAudits.sumOf { it.validEdgeCount },
            connectedComponentCount = knownAudits.sumOf { it.connectedComponentCount },
            oversizedComponentCount = knownAudits.sumOf { it.oversizedComponentCount },
            invalidSizePoolCount = knownAudits.sumOf { it.invalidSizePoolCount } + unknownStrategyPoolCount,
            disconnectedPoolCount = knownAudits.sumOf { it.disconnectedPoolCount },
            uncoveredWordCount = knownAudits.sumOf { (it.expectedWordIds - it.existingWordIds).size },
            extraneousMemberCount = knownAudits.sumOf { (it.existingWordIds - it.expectedWordIds).size },
            orphanEdgeCount = edgeSnapshot.orphanEdgeCount,
            selfLoopEdgeCount = edgeSnapshot.selfLoopEdgeCount,
            unknownTypeEdgeCount = edgeSnapshot.unknownTypeEdgeCount,
            layoutMismatch = knownAudits.any { it.layoutMismatch },
            invalidFocusPoolCount = knownAudits.sumOf { it.invalidFocusPoolCount },
            missingSupportingEdgePoolCount = knownAudits.sumOf { it.missingSupportingEdgePoolCount } +
                unknownStrategyPoolCount,
            storedEdgeCount = knownAudits.sumOf { it.storedEdgeCount },
            duplicatePoolCount = knownAudits.sumOf { it.duplicatePoolCount }
        )
    }

    override suspend fun repairQualityFirstPoolsFromExistingEdges(dictionaryId: Long): PoolRepairResult =
        withEdgeWriteLock(dictionaryId) {
            repairPoolsFromExistingEdgesLocked(dictionaryId)
        }

    private suspend fun repairPoolsFromExistingEdgesLocked(dictionaryId: Long): PoolRepairResult {
        val before = auditQualityFirstPools(dictionaryId)
        check(before.canRepairFromExistingEdges) {
            if (before.missingSupportingEdgePoolCount > 0) {
                "现有词池缺少支持边，无法无损修复；请先从备份恢复边数据或重新构建"
            } else {
                "当前词典没有可修复的有效边"
            }
        }

        val validWordIds = wordDao.getWordLabels(dictionaryId).mapTo(hashSetOf()) { it.id }
        val edgeSnapshot = loadQualityEdgeSnapshot(dictionaryId, validWordIds)
        val poolEntities = wordPoolDao.getPoolsByDictionary(dictionaryId)
        val strategyStates = wordPoolDao.getStrategyStates(dictionaryId)
            .filter { it.strategy != QUALITY_FIRST_STAGING_STATE }
            .mapTo(hashSetOf()) { it.strategy }
        val qualityFirstActive = PoolStrategy.QUALITY_FIRST.dbValue in strategyStates ||
            poolEntities.any { it.strategy == PoolStrategy.QUALITY_FIRST.dbValue } ||
            edgeSnapshot.qualityFirstEdges.any { it.evidenceSource != "user_note" }
        val balancedActive = PoolStrategy.BALANCED.dbValue in strategyStates ||
            poolEntities.any { it.strategy == PoolStrategy.BALANCED.dbValue } ||
            edgeSnapshot.balancedEdges.any { it.evidenceSource in BALANCED_EDGE_SOURCES }
        val qualityFirstPools = if (qualityFirstActive) {
            buildPoolsFromEffectiveEdges(edgeSnapshot.qualityFirstEdges)
        } else {
            emptyList()
        }
        val balancedPools = if (balancedActive) {
            buildPoolsFromEffectiveEdges(edgeSnapshot.balancedEdges)
        } else {
            emptyList()
        }
        db.withTransaction {
            edgeSnapshot.invalidEdgeIds.chunked(900).forEach { ids ->
                if (ids.isNotEmpty()) wordEdgeDao.deleteEdgesByIds(ids)
            }
            val now = System.currentTimeMillis()
            if (qualityFirstActive && edgeSnapshot.qualityFirstStoredEdgeCount > 0) {
                persistPoolsInCurrentTransaction(
                    dictionaryId = dictionaryId,
                    strategy = PoolStrategy.QUALITY_FIRST,
                    pools = qualityFirstPools,
                    generatedEdges = emptyList(),
                    updatedAt = now,
                    replaceGeneratedEdges = false
                )
            }
            if (balancedActive && edgeSnapshot.balancedStoredEdgeCount > 0) {
                persistPoolsInCurrentTransaction(
                    dictionaryId = dictionaryId,
                    strategy = PoolStrategy.BALANCED,
                    pools = balancedPools,
                    generatedEdges = emptyList(),
                    updatedAt = now,
                    replaceGeneratedEdges = false
                )
            }
        }
        invalidateGraphCache(dictionaryId)
        val after = auditQualityFirstPools(dictionaryId)
        check(after.isHealthy) { "词池修复后的健康检查未通过" }
        return PoolRepairResult(
            before = before,
            after = after,
            replacedPoolCount = qualityFirstPools.size + balancedPools.size
        )
    }

    private fun buildPoolsFromEffectiveEdges(edges: List<WordEdgeEntity>): List<BuiltPoolWithWordIds> {
        if (edges.isEmpty()) return emptyList()
        val planEdges = edges.map { QualityPoolEdge(it.wordIdA, it.wordIdB) }
        val plan = QualityFirstPoolPlanner.plan(planEdges)
        check(plan.coveredWordIds == planEdges.flatMapTo(hashSetOf()) { listOf(it.wordIdA, it.wordIdB) }) {
            "修复规划未覆盖全部有效边端点"
        }
        check(plan.pools.all { members ->
            members.size in 2..WordPoolEngine.MAX_POOL_SIZE &&
                QualityFirstPoolPlanner.isConnectedPool(members, planEdges)
        }) { "修复规划生成了无效或不连通的词池" }
        val edgesByWordId = mutableMapOf<Long, MutableList<WordEdgeEntity>>()
        edges.forEach { edge ->
            edgesByWordId.getOrPut(edge.wordIdA) { mutableListOf() }.add(edge)
            edgesByWordId.getOrPut(edge.wordIdB) { mutableListOf() }.add(edge)
        }
        return plan.pools.map { members ->
            BuiltPoolWithWordIds(
                focusWordId = members.maxWithOrNull(
                    compareBy<Long> { edgesByWordId[it].orEmpty().size }.thenByDescending { it }
                ),
                memberWordIds = members,
                qualityScore = PoolQualityScorer.computePoolQualityScore(members, edgesByWordId)
            )
        }
    }

    private suspend fun loadQualityEdgeSnapshot(
        dictionaryId: Long,
        validWordIds: Set<Long>
    ): QualityEdgeSnapshot {
        val qualityFirstEdges = mutableListOf<WordEdgeEntity>()
        val balancedEdges = mutableListOf<WordEdgeEntity>()
        var qualityFirstStoredEdges = 0
        var balancedStoredEdges = 0
        val invalidEdgeIds = mutableListOf<Long>()
        var orphanEdges = 0
        var selfLoops = 0
        var unknownTypes = 0
        var lastEdgeId = 0L

        while (true) {
            val page = wordEdgeDao.getEdgesPageFull(
                dictionaryId = dictionaryId,
                lastId = lastEdgeId,
                limit = REBUILD_EDGE_PAGE_SIZE
            )
            if (page.isEmpty()) break
            page.forEach { edge ->
                when {
                    edge.wordIdA == edge.wordIdB -> {
                        selfLoops++
                        invalidEdgeIds += edge.id
                    }
                    edge.wordIdA !in validWordIds || edge.wordIdB !in validWordIds -> {
                        orphanEdges++
                        invalidEdgeIds += edge.id
                    }
                    EdgeType.fromDbValue(edge.edgeType) == null -> {
                        unknownTypes++
                        invalidEdgeIds += edge.id
                    }
                    else -> {
                        if (edge.evidenceSource == "user_note") {
                            qualityFirstStoredEdges++
                            balancedStoredEdges++
                        } else if (edge.evidenceSource in BALANCED_EDGE_SOURCES) {
                            balancedStoredEdges++
                        } else {
                            qualityFirstStoredEdges++
                        }
                        if (edge.confidence < SIGNIFICANT_EDGE_MIN_CONFIDENCE ||
                            edge.status == EXCLUDED_POOL_EDGE_STATUS
                        ) {
                            return@forEach
                        }
                        if (edge.evidenceSource == "user_note") {
                            qualityFirstEdges += edge
                            balancedEdges += edge
                        } else if (edge.evidenceSource in BALANCED_EDGE_SOURCES) {
                            balancedEdges += edge
                        } else {
                            qualityFirstEdges += edge
                        }
                    }
                }
            }
            lastEdgeId = page.last().id
        }

        return QualityEdgeSnapshot(
            qualityFirstEdges = qualityFirstEdges,
            balancedEdges = balancedEdges,
            qualityFirstStoredEdgeCount = qualityFirstStoredEdges,
            balancedStoredEdgeCount = balancedStoredEdges,
            invalidEdgeIds = invalidEdgeIds,
            orphanEdgeCount = orphanEdges,
            selfLoopEdgeCount = selfLoops,
            unknownTypeEdgeCount = unknownTypes
        )
    }

    override suspend fun invalidateRelationGraph(dictionaryId: Long) {
        invalidateGraphCache(dictionaryId)
    }

    override suspend fun getWordEdgePreviews(
        dictionaryId: Long,
        wordId: Long,
        minConfidence: Double
    ): List<WordEdgeNeighborPreview> {
        val edges = wordEdgeDao.getEdgesForWord(dictionaryId, wordId, minConfidence)
        if (edges.isEmpty()) return emptyList()

        val neighborIds = edges.map { edge ->
            if (edge.wordIdA == wordId) edge.wordIdB else edge.wordIdA
        }.distinct()
        val spellingsById = wordDao.getWordsByIds(neighborIds)
            .associate { it.word.id to it.word.spelling }

        return edges
            .groupBy { edge ->
                if (edge.wordIdA == wordId) edge.wordIdB else edge.wordIdA
            }
            .mapNotNull { (neighborId, groupedEdges) ->
                val spelling = spellingsById[neighborId] ?: return@mapNotNull null
                val edgeTypes = groupedEdges.mapNotNull { EdgeType.fromDbValue(it.edgeType) }.toSet()
                if (edgeTypes.isEmpty()) return@mapNotNull null
                WordEdgeNeighborPreview(
                    neighborId = neighborId,
                    spelling = spelling,
                    edgeTypes = edgeTypes
                )
            }
    }

    override suspend fun confirmWordRelation(
        dictionaryId: Long,
        wordId: Long,
        relatedWordId: Long,
        edgeType: EdgeType
    ): Boolean = withEdgeWriteLock(dictionaryId) {
        val currentWord = wordDao.getWordById(wordId)?.toDomain()
            ?: throw IllegalStateException("当前背诵词不存在")
        val relatedWord = wordDao.getWordById(relatedWordId)?.toDomain()
            ?: throw IllegalStateException("便签单词不存在")
        if (currentWord.dictionaryId != dictionaryId || relatedWord.dictionaryId != dictionaryId) {
            throw IllegalStateException("便签关联的两个单词必须属于同一个词典")
        }
        val edges = wordEdgeDao.getEdgesBetweenWords(dictionaryId, wordId, relatedWordId)
        if (edges.isEmpty()) return@withEdgeWriteLock false
        val selectedEdge = edges.firstOrNull { it.edgeType == edgeType.dbValue }
        if (selectedEdge != null) {
            wordEdgeDao.promoteUserEdge(
                id = selectedEdge.id,
                status = "support",
                confidence = 1.0,
                evidenceSource = "user_note"
            )
        } else {
            wordEdgeDao.insertEdgeIfAbsent(
                toEdgeEntity(
                    currentWord = currentWord,
                    otherWord = relatedWord,
                    edge = buildWordNoteEdge(edgeType),
                    dictionaryId = dictionaryId
                )
            )
            wordEdgeDao.getEdgesBetweenWords(dictionaryId, wordId, relatedWordId)
                .firstOrNull { it.edgeType == edgeType.dbValue }
                ?.let { inserted ->
                    wordEdgeDao.promoteUserEdge(
                        id = inserted.id,
                        status = "support",
                        confidence = 1.0,
                        evidenceSource = "user_note"
                    )
                }
        }
        invalidateGraphCache(dictionaryId)
        true
    }

    override suspend fun organizeWordNoteRelation(
        dictionaryId: Long,
        wordId: Long,
        relatedWordId: Long,
        edgeType: EdgeType
    ) = withEdgeWriteLock(dictionaryId) {
        val currentWord = wordDao.getWordById(wordId)?.toDomain()
            ?: throw IllegalStateException("当前背诵词不存在")
        val relatedWord = wordDao.getWordById(relatedWordId)?.toDomain()
            ?: throw IllegalStateException("便签单词不存在")
        if (currentWord.dictionaryId != dictionaryId || relatedWord.dictionaryId != dictionaryId) {
            throw IllegalStateException("便签关联的两个单词必须属于同一个词典")
        }

        val existingEdges = wordEdgeDao.getEdgesBetweenWords(dictionaryId, wordId, relatedWordId)
        val selectedEdge = existingEdges.firstOrNull { it.edgeType == edgeType.dbValue }
        if (selectedEdge != null) {
            wordEdgeDao.promoteUserEdge(
                id = selectedEdge.id,
                status = "support",
                confidence = 1.0,
                evidenceSource = "user_note"
            )
        } else {
            val fallbackEdge = buildWordNoteEdge(edgeType)
            wordEdgeDao.insertEdgeIfAbsent(
                toEdgeEntity(currentWord, relatedWord, fallbackEdge, dictionaryId)
            )
            wordEdgeDao.getEdgesBetweenWords(dictionaryId, wordId, relatedWordId)
                .firstOrNull { it.edgeType == edgeType.dbValue }
                ?.let { edge ->
                    wordEdgeDao.promoteUserEdge(
                        id = edge.id,
                        status = "support",
                        confidence = 1.0,
                        evidenceSource = "user_note"
                    )
                }
        }
        invalidateGraphCache(dictionaryId)
    }

    override suspend fun getWordDetail(wordId: Long): WordDetails? =
        wordDao.getWordById(wordId)?.toDomain()

    override suspend fun getWordGraphEdgeDetail(edgeId: Long): WordGraphEdgeDetail? {
        val edge = wordEdgeDao.getEdgeDetail(edgeId) ?: return null
        val type = EdgeType.fromDbValue(edge.edgeType) ?: return null
        return WordGraphEdgeDetail(
            edgeId = edge.id,
            type = type,
            relationStrength = edge.relationStrength,
            confidence = edge.confidence,
            status = edge.status,
            reason = edge.reason,
            exampleSentence = edge.exampleSentence,
            register = edge.register,
            difficultyCefr = edge.difficultyCefr,
            warningNote = edge.warningNote
        )
    }

    override suspend fun getWordRelationGraph(dictionaryId: Long): WordGraph {
        val cachedGraph = graphCacheMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = graphCache
            if (cached != null &&
                cached.dictionaryId == dictionaryId &&
                now - cached.loadedAtMs <= GRAPH_CACHE_TTL_MS
            ) {
                cached.graph
            } else {
                null
            }
        }
        if (cachedGraph != null) return cachedGraph

        val graph = AppResourceCoordinator.withMemoryHeavyOperation("word_graph:$dictionaryId") {
            buildWordRelationGraph(dictionaryId)
        }
        return graphCacheMutex.withLock {
            val current = graphCache
            if (current != null &&
                current.dictionaryId == dictionaryId &&
                System.currentTimeMillis() - current.loadedAtMs <= GRAPH_CACHE_TTL_MS
            ) {
                current.graph
            } else {
                graphCache = CachedWordGraph(
                    dictionaryId = dictionaryId,
                    loadedAtMs = System.currentTimeMillis(),
                    graph = graph
                )
                graph
            }
        }
    }

    private suspend fun buildWordRelationGraph(dictionaryId: Long): WordGraph {
        val labels = wordDao.getWordLabels(dictionaryId)
        val n = labels.size
        if (n == 0) {
            return WordGraph(emptyList(), emptyList(), emptyList(), emptyList(), 0, 0, emptyMap())
        }

        // wordId -> node index (node list preserves label order; edges/clusters reference these indices)
        val indexById = HashMap<Long, Int>(n * 2)
        labels.forEachIndexed { i, l -> indexById[l.id] = i }

        val uf = UnionFind(n)
        val degree = IntArray(n)
        val clusterDistribution = HashMap<EdgeCluster, Int>()
        val effectiveEdgeCount = wordEdgeDao.countEffectiveGraphEdges(
            dictionaryId = dictionaryId,
            minConfidence = SIGNIFICANT_EDGE_MIN_CONFIDENCE,
            excludedStatus = EXCLUDED_POOL_EDGE_STATUS
        )
        val graphEdges = ArrayList<WordGraphEdge>(
            effectiveEdgeCount.coerceAtMost(GRAPH_EDGE_INITIAL_CAPACITY_MAX)
        )

        forEachGraphEdge(dictionaryId) { e ->
            val ai = indexById[e.wordIdA] ?: return@forEachGraphEdge
            val bi = indexById[e.wordIdB] ?: return@forEachGraphEdge
            if (ai == bi) return@forEachGraphEdge
            val type = EdgeType.fromDbValue(e.edgeType) ?: return@forEachGraphEdge
            uf.union(ai, bi)
            degree[ai]++
            degree[bi]++
            clusterDistribution[type.cluster] = (clusterDistribution[type.cluster] ?: 0) + 1
            graphEdges.add(
                WordGraphEdge(
                    edgeId = e.id,
                    aIndex = ai,
                    bIndex = bi,
                    type = type,
                    relationStrength = e.relationStrength,
                    confidence = e.confidence
                )
            )
        }

        // 把有边的节点按连通分量分组；孤立词（度数 0）另存。
        val rootToNodes = LinkedHashMap<Int, MutableList<Int>>()
        val isolated = ArrayList<Int>()
        for (i in 0 until n) {
            if (degree[i] == 0) {
                isolated.add(i)
            } else {
                rootToNodes.getOrPut(uf.find(i)) { ArrayList() }.add(i)
            }
        }

        // 每个分量内各关系类别的边数（卡片色点 / 聚合点主色）。
        val rootRelationCounts = HashMap<Int, HashMap<EdgeCluster, Int>>()
        graphEdges.forEach { e ->
            val root = uf.find(e.aIndex)
            val m = rootRelationCounts.getOrPut(root) { HashMap() }
            m[e.type.cluster] = (m[e.type.cluster] ?: 0) + 1
        }

        // 簇按词数降序编号，得到稳定且自然的 cluster id。
        val sortedRoots = rootToNodes.entries.sortedByDescending { it.value.size }
        val clusterIdByRoot = HashMap<Int, Int>(sortedRoots.size * 2)
        sortedRoots.forEachIndexed { cid, entry -> clusterIdByRoot[entry.key] = cid }

        val clusterIdByNode = IntArray(n) { -1 }
        rootToNodes.forEach { (root, members) ->
            val cid = clusterIdByRoot.getValue(root)
            members.forEach { clusterIdByNode[it] = cid }
        }

        val nodes = labels.mapIndexed { i, l ->
            WordGraphNode(wordId = l.id, spelling = l.spelling, clusterId = clusterIdByNode[i], degree = degree[i])
        }

        val clusters = sortedRoots.map { (root, members) ->
            // 核心 = 度数最高者；并列取下标最小者（稳定）。
            val core = members.minWith(compareByDescending<Int> { degree[it] }.thenBy { it })
            WordGraphCluster(
                id = clusterIdByRoot.getValue(root),
                coreNodeIndex = core,
                nodeIndices = members,
                relationCounts = rootRelationCounts[root] ?: emptyMap()
            )
        }

        return WordGraph(
            nodes = nodes,
            edges = graphEdges,
            clusters = clusters,
            isolatedNodeIndices = isolated,
            totalWords = n,
            totalEdges = graphEdges.size,
            clusterDistribution = clusterDistribution
        )
    }

    private suspend fun forEachGraphEdge(
        dictionaryId: Long,
        action: (GraphEdgeProjection) -> Unit
    ) {
        var lastEdgeId = 0L
        while (true) {
            val edgePage = wordEdgeDao.getEffectiveGraphEdgesPage(
                dictionaryId = dictionaryId,
                lastId = lastEdgeId,
                minConfidence = SIGNIFICANT_EDGE_MIN_CONFIDENCE,
                excludedStatus = EXCLUDED_POOL_EDGE_STATUS,
                limit = GRAPH_EDGE_PAGE_SIZE
            )
            if (edgePage.isEmpty()) break
            edgePage.forEach(action)
            lastEdgeId = edgePage.last().id
            coroutineContext.ensureActive()
        }
    }

    override suspend fun rebuildPools(
        dictionaryId: Long,
        strategy: PoolStrategy,
        startIndex: Int,
        rebuildMode: RebuildMode,
        resumeProgressMessage: String?,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onProgress: (Int, Int, String?) -> Unit
    ) = withEdgeWriteLock(dictionaryId) {
        val output: RebuildPoolOutput = when (strategy) {
            PoolStrategy.BALANCED, PoolStrategy.BALANCED_WITH_AI -> {
                // BALANCED needs all candidates in memory for the engine
                val words = wordDao.getWordsByDictionaryOnce(dictionaryId)
                val total = words.size
                onProgress(0, total, null)
                buildBalanced(words, dictionaryId, strategy, isCancelled, isPaused, onProgress, total)
            }
            PoolStrategy.QUALITY_FIRST ->
                RebuildPoolOutput(
                    pools = buildQualityFirstStreaming(
                        dictionaryId,
                        startIndex,
                        rebuildMode,
                        resumeProgressMessage,
                        isCancelled,
                        isPaused,
                        onProgress
                    )
                )
        }

        // Ensure not cancelled before writing
        coroutineContext.ensureActive()

        if (isCancelled()) throw CancellationException("词池构建已停止")
        if (strategy == PoolStrategy.QUALITY_FIRST) {
            persistQualityFirstStagedResult(dictionaryId, output.pools)
        } else {
            persistPools(dictionaryId, strategy, output.pools, output.generatedEdges)
        }
        invalidateGraphCache(dictionaryId)
    }

    /** 把构建好的词池在单事务内落库（先删该策略旧池再插入新池+成员）。 */
    private suspend fun persistPools(
        dictionaryId: Long,
        strategy: PoolStrategy,
        pools: List<BuiltPoolWithWordIds>,
        generatedEdges: List<WordEdgeEntity> = emptyList()
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            persistPoolsInCurrentTransaction(dictionaryId, strategy, pools, generatedEdges, now)
        }
    }

    private suspend fun persistPoolsInCurrentTransaction(
        dictionaryId: Long,
        strategy: PoolStrategy,
        pools: List<BuiltPoolWithWordIds>,
        generatedEdges: List<WordEdgeEntity>,
        updatedAt: Long,
        replaceGeneratedEdges: Boolean = true
    ) {
        if (replaceGeneratedEdges &&
            (strategy == PoolStrategy.BALANCED || strategy == PoolStrategy.BALANCED_WITH_AI)
        ) {
            val replacedSources = if (strategy == PoolStrategy.BALANCED_WITH_AI) {
                listOf("balanced_local", "balanced_ai")
            } else {
                listOf("balanced_local")
            }
            wordEdgeDao.deleteGeneratedEdgesBySources(
                dictionaryId,
                replacedSources
            )
            if (generatedEdges.isNotEmpty()) {
                wordEdgeDao.insertEdges(generatedEdges)
            }
        }
        wordPoolDao.deleteByDictionaryAndStrategy(dictionaryId, strategy.dbValue)
        pools.forEach { builtPool ->
            val poolId = wordPoolDao.insertPool(
                WordPoolEntity(
                    dictionaryId = dictionaryId,
                    focusWordId = builtPool.focusWordId,
                    strategy = strategy.dbValue,
                    algorithmVersion = strategy.algorithmVersion,
                    updatedAt = updatedAt,
                    qualityScore = builtPool.qualityScore
                )
            )
            wordPoolDao.insertMembers(
                builtPool.memberWordIds.map {
                    WordPoolMemberEntity(wordId = it, poolId = poolId)
                }
            )
        }
        wordPoolDao.upsertStrategyState(
            WordPoolStrategyStateEntity(
                dictionaryId = dictionaryId,
                strategy = strategy.dbValue,
                updatedAt = updatedAt
            )
        )
    }

    private suspend fun persistQualityFirstStagedResult(
        dictionaryId: Long,
        pools: List<BuiltPoolWithWordIds>
    ) {
        val stagedEdges = wordEdgeDao.getStagedEdges(dictionaryId).map(WordEdgeStagingEntity::toLiveEntity)
        val now = System.currentTimeMillis()
        db.withTransaction {
            wordEdgeDao.deleteUnprotectedEdgesByDictionary(dictionaryId, PROTECTED_EDGE_SOURCES)
            stagedEdges.chunked(500).forEach { batch ->
                if (batch.isNotEmpty()) wordEdgeDao.insertEdges(batch)
            }
            persistPoolsInCurrentTransaction(
                dictionaryId = dictionaryId,
                strategy = PoolStrategy.QUALITY_FIRST,
                pools = pools,
                generatedEdges = emptyList(),
                updatedAt = now
            )
            wordEdgeDao.deleteStagedEdges(dictionaryId)
            wordPoolDao.deleteStrategyState(dictionaryId, QUALITY_FIRST_STAGING_STATE)
        }
    }

    override suspend fun getEdgeCount(dictionaryId: Long): Int =
        wordEdgeDao.countEffectiveGraphEdges(
            dictionaryId = dictionaryId,
            minConfidence = SIGNIFICANT_EDGE_MIN_CONFIDENCE,
            excludedStatus = EXCLUDED_POOL_EDGE_STATUS
        )

    override suspend fun reviewPools(
        dictionaryId: Long,
        strategy: PoolStrategy,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onProgress: (Int, Int, String?) -> Unit
    ) = withEdgeWriteLock(dictionaryId) {
        coroutineContext.ensureActive()
        val edgeCount = wordEdgeDao.countEdgesExcludingSources(dictionaryId, REVIEW_EXCLUDED_SOURCES)
        if (edgeCount <= 0) {
            onProgress(0, 0, null)
            return@withEdgeWriteLock
        }
        val wordSpellings = wordDao.getWordLabels(dictionaryId).associate { it.id to it.spelling }

        // 提纯全部已存在边。批大小 / 并发 / RPM / 重试模式均复用词池构建配置；
        // review verdict=remove 现改为“保留边记录，仅把 confidence 降到 0”。
        edgeReviewer.reviewDictionaryEdgesWithAi(
            dictionaryId = dictionaryId,
            wordSpellings = wordSpellings,
            isCancelled = isCancelled,
            isPaused = isPaused,
            onReviewStart = { _, totalBatches ->
                if (totalBatches > 0) {
                    liveMonitor.startWord(
                        dictionaryId = dictionaryId,
                        taskType = BackgroundTaskType.WORD_POOL_REVIEW,
                        word = "AI提纯批次",
                        chunkCount = totalBatches,
                        alreadyCommitted = 0
                    )
                } else {
                    liveMonitor.clear()
                }
            },
            onBatchAttempt = { batchIndex, attempt, response, error, success ->
                liveMonitor.recordAttempt(
                    dictionaryId = dictionaryId,
                    taskType = BackgroundTaskType.WORD_POOL_REVIEW,
                    word = "AI提纯批次",
                    chunkIndex = batchIndex,
                    attempt = attempt,
                    response = response,
                    error = error,
                    success = success
                )
            },
            onProgress = onProgress
        )

        coroutineContext.ensureActive()
        invalidateGraphCache(dictionaryId)
        liveMonitor.clear()
    }

    private suspend fun invalidateGraphCache(dictionaryId: Long) {
        graphCacheMutex.withLock {
            if (graphCache?.dictionaryId == dictionaryId) {
                graphCache = null
            }
        }
    }

    private suspend fun <T> withEdgeWriteLock(dictionaryId: Long, block: suspend () -> T): T {
        return poolEdgeWriteCoordinator.withLock(dictionaryId, block)
    }

    override suspend fun getPoolCount(dictionaryId: Long): Int =
        wordPoolDao.countPools(dictionaryId)

    override suspend fun getWordToPoolsMap(dictionaryId: Long): Map<Long, Set<Long>> {
        val memberships = wordPoolDao.getAllMemberships(dictionaryId)
        val result = mutableMapOf<Long, MutableSet<Long>>()
        memberships.forEach { m ->
            result.getOrPut(m.wordId) { mutableSetOf() }.add(m.poolId)
        }
        return result
    }

    override suspend fun getPoolToMembersMap(dictionaryId: Long): Map<Long, Set<Long>> {
        val memberships = wordPoolDao.getAllMemberships(dictionaryId)
        val result = mutableMapOf<Long, MutableSet<Long>>()
        memberships.forEach { m ->
            result.getOrPut(m.poolId) { mutableSetOf() }.add(m.wordId)
        }
        return result
    }

    override suspend fun getPoolVersionInfo(dictionaryId: Long): List<Pair<String, String>> {
        return wordPoolDao.getPoolVersionInfo(dictionaryId).map { it.strategy to it.algorithmVersion }
    }

    override suspend fun getWordEdgeAdjacency(dictionaryId: Long): Map<Long, Map<Long, Set<com.xty.englishhelper.domain.model.EdgeType>>> {
        val adj = mutableMapOf<Long, MutableMap<Long, MutableSet<com.xty.englishhelper.domain.model.EdgeType>>>()
        forEachGraphEdge(dictionaryId) { e ->
            val type = com.xty.englishhelper.domain.model.EdgeType.fromDbValue(e.edgeType) ?: return@forEachGraphEdge
            adj.getOrPut(e.wordIdA) { mutableMapOf() }
                .getOrPut(e.wordIdB) { mutableSetOf() }
                .add(type)
            adj.getOrPut(e.wordIdB) { mutableMapOf() }
                .getOrPut(e.wordIdA) { mutableSetOf() }
                .add(type)
        }
        return adj
    }

    override suspend fun getWordEdgeAdjacencyDetailed(dictionaryId: Long): Map<Long, List<EdgeNeighbor>> {
        val edges = wordEdgeDao.getAllEdges(dictionaryId)
        val adj = mutableMapOf<Long, MutableList<EdgeNeighbor>>()
        edges.forEach { e ->
            if (e.wordIdA == e.wordIdB) return@forEach
            val type = EdgeType.fromDbValue(e.edgeType) ?: return@forEach
            // 同一条边在两端各登记一次，邻居取对端 wordId。
            adj.getOrPut(e.wordIdA) { mutableListOf() }.add(
                EdgeNeighbor(
                    neighborId = e.wordIdB,
                    type = type,
                    relationStrength = e.relationStrength,
                    confidence = e.confidence,
                    learningValue = e.learningValue,
                    status = e.status,
                    reason = e.reason,
                    exampleSentence = e.exampleSentence,
                    register = e.register,
                    difficultyCefr = e.difficultyCefr,
                    warningNote = e.warningNote
                )
            )
            adj.getOrPut(e.wordIdB) { mutableListOf() }.add(
                EdgeNeighbor(
                    neighborId = e.wordIdA,
                    type = type,
                    relationStrength = e.relationStrength,
                    confidence = e.confidence,
                    learningValue = e.learningValue,
                    status = e.status,
                    reason = e.reason,
                    exampleSentence = e.exampleSentence,
                    register = e.register,
                    difficultyCefr = e.difficultyCefr,
                    warningNote = e.warningNote
                )
            )
        }
        return adj
    }

    private suspend fun buildBalanced(
        words: List<WordWithDetails>,
        dictionaryId: Long,
        strategy: PoolStrategy,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onProgress: (Int, Int, String?) -> Unit,
        total: Int
    ): RebuildPoolOutput {
        val associations = wordPoolDao.getAssociationsInDictionary(dictionaryId)
        val assocMap = mutableMapOf<Long, MutableList<Long>>()
        associations.forEach { pair ->
            assocMap.getOrPut(pair.wordId) { mutableListOf() }.add(pair.associatedWordId)
            assocMap.getOrPut(pair.associatedWordId) { mutableListOf() }.add(pair.wordId)
        }

        val candidates = words.mapIndexed { index, wwd ->
            if (isCancelled()) throw CancellationException("词池构建已停止")
            while (isPaused()) {
                kotlinx.coroutines.delay(500)
                if (isCancelled()) throw CancellationException("词池构建已停止")
            }
            coroutineContext.ensureActive()
            onProgress(index, total, wwd.word.spelling)
            val domain = wwd.toDomain()
            PoolCandidate(
                index = index,
                wordId = domain.id,
                spelling = domain.spelling,
                meanings = domain.meanings.map { it.definition },
                synonymSpellings = domain.synonyms.map { it.word },
                similarSpellings = domain.similarWords.map { it.word },
                cognateSpellings = domain.cognates.map { it.word },
                associatedWordIds = assocMap[domain.id] ?: emptyList()
            )
        }

        var build = engine.buildPoolsWithRelations(candidates)
        onProgress(total, total, null)

        if (strategy == PoolStrategy.BALANCED_WITH_AI) {
            coroutineContext.ensureActive()
            build = tryAiBatchCompletion(candidates, build)
        }

        val pools = build.pools.map { pool ->
            BuiltPoolWithWordIds(
                focusWordId = pool.coreIndex?.let { candidates[it].wordId },
                memberWordIds = pool.memberIndices.map { candidates[it].wordId }
            )
        }
        val generatedEdges = build.relations.map { relation ->
            val wordA = candidates[relation.indexA]
            val wordB = candidates[relation.indexB]
            WordEdgeEntity(
                wordIdA = minOf(wordA.wordId, wordB.wordId),
                wordIdB = maxOf(wordA.wordId, wordB.wordId),
                edgeType = relation.edgeType.dbValue,
                dictionaryId = dictionaryId,
                status = "support",
                learningValue = 3,
                relationStrength = 3,
                confidence = if (relation.evidenceSource == "balanced_ai") 0.7 else 0.65,
                reason = if (relation.evidenceSource == "balanced_ai") {
                    "来自均衡 AI 分组"
                } else {
                    "来自本地均衡词池关系"
                },
                evidenceSource = relation.evidenceSource,
                register = "neutral"
            )
        }
        return RebuildPoolOutput(pools = pools, generatedEdges = generatedEdges)
    }

    // ── QUALITY_FIRST 构建 ──
    //
    // 外层：从最后一个词向前遍历（倒序）。第 i 个词只与它前面的词 [0, i) 比较，
    //       因此每对词恰好比较一次。末尾的词前驱最多 → 分块最多 → 一上来就并发多次请求。
    // 内层：前驱词按 chunkSize 切块，每块一次 AI 请求；同一词的多块并发执行，
    //       并发度受 maxConcurrent 限制、整体频率受 requestsPerMinute 限制。
    //       一个词的所有块完成后才进入下一个词。
    //
    // 例：301 个词、chunkSize=50。先处理末词 → 前驱 300 个 → 6 块 → 6 次请求；
    //     再处理倒数第二个 → 5~6 块……直到第 1 个词（无前驱，跳过）。
    private suspend fun buildQualityFirstStreaming(
        dictionaryId: Long,
        startIndex: Int,
        rebuildMode: RebuildMode,
        resumeProgressMessage: String?,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onProgress: (Int, Int, String?) -> Unit
    ): List<BuiltPoolWithWordIds> {
        val chunkSize = settingsDataStore.getPoolWindowSize()          // 每块候选词数
        // MAX_CONCURRENT / REQUESTS_PER_MINUTE 仅是「初始值」：下方外层词循环每词都会重读一次，
        // 使构建途中修改这两项能从「后面的词」起立即生效（chunkSize 绑定续传坐标系，故不在此热更新）。
        val maxConcurrent = settingsDataStore.getPoolMaxConcurrent()   // 同一词内各块的最大并发（初始值，每词热重读）
        val requestsPerMinute = settingsDataStore.getPoolRequestsPerMinute() // 每分钟请求数（初始值，每词热重读）
        val initialRetryMode = settingsDataStore.getPoolRetryMode()    // 重试模式（初始值，每词热重读）
        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        Log.i("WordPoolRepo", "QUALITY_FIRST 配置: chunkSize=$chunkSize, maxConcurrent=$maxConcurrent, rpm=$requestsPerMinute, retry=$initialRetryMode, mode=$rebuildMode, startIndex=$startIndex, resumeMsg=${resumeProgressMessage ?: "<none>"}")

        // 正式边始终保持不动。全新任务只清 staging；失败任务在 staging 会话存在时继续断点。
        // 升级前遗留任务没有 staging 会话，必须从头计算，但旧正式数据仍保留到最终原子交换。

        // 限流器全程**单实例**（保留全局节奏）：速率改变时用 updateRate 就地更新，绝不重建。
        // Semaphore 改为「每词新建」——permit 数构造后不可变，但每词的块都在下方独立 coroutineScope（栅栏）内跑完，
        // 词末已无块持票，故为每个词新建一个安全无泄漏。因此这里不再预建 Semaphore。
        val rateLimiter = EdgeRateLimiter(requestsPerMinute)
        var lastMaxConcurrent = maxConcurrent   // 仅用于「并发配置热更新」变更日志
        var lastRpm = requestsPerMinute          // 仅用于「并发配置热更新」变更日志
        var lastRetryMode = initialRetryMode     // 仅用于「并发配置热更新」变更日志

        // 一次性载入全部词（含完整释义），按 spelling 升序。各块直接复用这些对象，不再回查数据库。
        val allWords: List<WordDetails> = wordDao.getWordsByDictionaryOnce(dictionaryId).map { it.toDomain() }
        val totalWords = allWords.size
        Log.i("WordPoolRepo", "词典共 $totalWords 个词")
        if (totalWords <= 1) {
            wordEdgeDao.deleteStagedEdges(dictionaryId)
            wordPoolDao.upsertStrategyState(
                WordPoolStrategyStateEntity(
                    dictionaryId = dictionaryId,
                    strategy = QUALITY_FIRST_STAGING_STATE,
                    updatedAt = System.currentTimeMillis()
                )
            )
            onProgress(totalWords, totalWords, null)
            return emptyList()
        }

        // AI 调用成功计数（仅用于日志）。任何分块彻底失败都会立即抛 PoolBuildDataException 中止构建，
        // 因此走到构建结束就意味着所有调用都成功了。并发块中写入，故用原子类型。
        val successfulAiCalls = AtomicInteger(0)

        // 已完整处理的词数：用于进度展示与断点续传。
        // 倒序处理时，已处理的是“末尾若干个词”，startIndex 即上次已处理的数量。
        val hasStagingSession = wordPoolDao.getStrategyStates(dictionaryId)
            .any { it.strategy == QUALITY_FIRST_STAGING_STATE }
        val requestedAlreadyProcessed = if (startIndex > 0) startIndex.coerceIn(0, totalWords) else 0
        val alreadyProcessed = if (hasStagingSession) requestedAlreadyProcessed else 0
        val processed = AtomicInteger(alreadyProcessed)

        // ============================================================
        // 外层循环：从后向前（倒序）。
        //   末词前驱最多 → 分块最多 → 立刻并发多次请求，可即时观察。
        //   index 0 无前驱，循环结束后统一计入进度。
        //   断点续传：跳过末尾已处理的 alreadyProcessed 个词，从 (totalWords-1-alreadyProcessed) 开始。
        // ============================================================
        val firstIndexToProcess = (totalWords - 1 - alreadyProcessed).coerceAtLeast(0)

        // ============================================================
        // 续传点解算（**与 rebuildMode 无关**）：
        //   词级：alreadyProcessed = 已完整处理的末尾词数（来自 startIndex / progressCurrent）。
        //   块级：startChunk = 断点词已提交的连续前缀块数（来自持久化的 progressMessage，严格校验）。
        // 两者都与模式无关——FULL 被中断后同样要能续传，绝不能丢掉已建的边。
        // ============================================================
        val startChunk = if (hasStagingSession) {
            resolveStartChunk(resumeProgressMessage, allWords, firstIndexToProcess, chunkSize)
        } else {
            0
        }
        val hasResumableWork = hasStagingSession && (alreadyProcessed > 0 || startChunk > 0)
        Log.i(
            "WordPoolRepo",
            "续传点解算: alreadyProcessed=$alreadyProcessed, firstIndexToProcess=$firstIndexToProcess, " +
                "startChunk=$startChunk, hasResumableWork=$hasResumableWork" +
                (if (firstIndexToProcess in allWords.indices) ", 断点词='${allWords[firstIndexToProcess].spelling}'" else "")
        )

        // 新边先写入 staging。失败、取消或进程退出时，当前正式边和词池完全不变；
        // 只有全部分块成功后才在单事务中交换 staging 边与正式池。
        if (!hasResumableWork) {
            Log.i("WordPoolRepo", "$rebuildMode 全新构建：清空词典 $dictionaryId 的暂存边")
            wordEdgeDao.deleteStagedEdges(dictionaryId)
            wordPoolDao.upsertStrategyState(
                WordPoolStrategyStateEntity(
                    dictionaryId = dictionaryId,
                    strategy = QUALITY_FIRST_STAGING_STATE,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        if (startChunk > 0) {
            Log.i("WordPoolRepo", "块级续传：断点词 '${allWords[firstIndexToProcess].spelling}' 从第 ${startChunk + 1} 块继续（跳过前 $startChunk 个已提交块）")
        }

        for (wordIndex in firstIndexToProcess downTo 1) {
            if (isCancelled()) {
                Log.d("WordPoolRepo", "构建被取消 @wordIndex=$wordIndex")
                throw CancellationException("词池构建已停止")
            }
            awaitWhilePaused(isPaused, isCancelled)
            if (isCancelled()) throw CancellationException("词池构建已停止")

            val currentWord = allWords[wordIndex]

            // 续传清理：仅断点词（本次循环首个词）需要。
            //   startChunk>0：块级续传——只清掉待重做块覆盖的前驱区 [startChunk*chunkSize, wordIndex) 残边，
            //                 保留 [0, startChunk) 已提交块的边；前驱可能很多，分批以避开 SQLite 参数上限。
            //   startChunk==0：整词重做——沿用旧逻辑，先清掉该词所有残边。
            if (wordIndex == firstIndexToProcess && (alreadyProcessed > 0 || startChunk > 0)) {
                if (startChunk > 0) {
                    val from = (startChunk * chunkSize).coerceAtMost(wordIndex)
                    allWords.subList(from, wordIndex).map { it.id }.chunked(900).forEach { batch ->
                        wordEdgeDao.deleteStagedEdgesForWordAgainst(
                            dictionaryId,
                            currentWord.id,
                            batch
                        )
                    }
                } else {
                    wordEdgeDao.deleteStagedEdgesForWord(dictionaryId, currentWord.id)
                }
            }

            // ── 并发配置热生效（每词重读一次）──
            // 在设置里改了 MAX_CONCURRENT / REQUESTS_PER_MINUTE，从这里（即「后面的词」）起立即按新值执行。
            //   · Semaphore 的 permit 数构造后不可变，但本词的块都在下方独立 coroutineScope（栅栏）内跑完，
            //     上一个词此刻已无块持票 → 为本词新建一个 Semaphore，安全无泄漏。
            //   · 限流器是**全局节奏**，绝不重建（重建会清零排程→每词边界突发击穿限流），用 updateRate 就地改间隔。
            //   · chunkSize 不热更新：它绑定续传坐标系（progressMessage 总块数校验），中途改会使块级续传退化为整词重做。
            val currentMaxConcurrent = settingsDataStore.getPoolMaxConcurrent()
            val currentRpm = settingsDataStore.getPoolRequestsPerMinute()
            val currentRetryMode = settingsDataStore.getPoolRetryMode()
            if (currentMaxConcurrent != lastMaxConcurrent || currentRpm != lastRpm || currentRetryMode != lastRetryMode) {
                Log.i(
                    "WordPoolRepo",
                    "并发配置热更新 @'${currentWord.spelling}': " +
                        "maxConcurrent $lastMaxConcurrent→$currentMaxConcurrent, rpm $lastRpm→$currentRpm, " +
                        "retry $lastRetryMode→$currentRetryMode"
                )
                lastMaxConcurrent = currentMaxConcurrent
                lastRpm = currentRpm
                lastRetryMode = currentRetryMode
            }
            rateLimiter.updateRate(currentRpm)
            val semaphore = Semaphore(currentMaxConcurrent)
            // 本词累计失败次数：宽松重试间隔 = 累计失败 × 10s（上限 2 分钟）。逐词重置，所有块并发共享。
            val wordFailureTally = AtomicInteger(0)

            // 内层：前驱词 [0, wordIndex) 按 chunkSize 分块。
            // 例：301 个词处理末词时，前驱 300 个、chunkSize=50 → 6 块：[0,50) [50,100) … [250,300)
            val chunks = allWords.subList(0, wordIndex).chunked(chunkSize)
            val wordsDone = processed.get()
            // 块级续传：仅本次循环的首词（断点词）跳过已提交的前缀块；其余词都从块 0 开始。
            val startChunkForWord = if (wordIndex == firstIndexToProcess) startChunk.coerceAtMost(chunks.size) else 0
            Log.d(
                "WordPoolRepo",
                "词 '${currentWord.spelling}' (剩余#$wordIndex): 对比前 $wordIndex 个词，分 ${chunks.size} 块" +
                    if (startChunkForWord > 0) "（从第 ${startChunkForWord + 1} 块续传）" else ""
            )

            // 续传计数起点：断点词 [0, startChunkForWord) 块的边在上次构建已落库（cleanup 只删了待重做区 [from, wordIndex)，
            // 保留了 [0, from)），但 committedEdges 是 per-word 局部变量、默认从 0 起。若不补回这些已落库的边数，
            // 详情页"已找到 N 条"会在续传时清零，让用户误以为前面已整理的块白做了（块数却仍跳过它们）。
            // 故先从 DB 数出 currentWord 与前驱 [0, from) 之间已存在的边数作为起点；后续重做块再在其上累加。
            var committedEdges = 0
            if (startChunkForWord > 0) {
                val committedUntil = (startChunkForWord * chunkSize).coerceAtMost(wordIndex)
                allWords.subList(0, committedUntil).map { it.id }.chunked(900).forEach { batch ->
                    committedEdges += wordEdgeDao.countStagedEdgesForWordAgainst(dictionaryId, currentWord.id, batch)
                }
            }

            // 该词开始：先报告一次（已提交块数 = startChunkForWord，已找到边数 = 续传起点），让详情页立即显示总块数与续传进度。
            onProgress(wordsDone, totalWords, encodeProgress(currentWord.spelling, startChunkForWord, chunks.size, committedEdges))

            // 实时分块网格：用本词块数初始化方块（续传的前缀块直接置绿），下方每块每次尝试都会刷新对应方块颜色。
            liveMonitor.startWord(
                dictionaryId = dictionaryId,
                taskType = BackgroundTaskType.WORD_POOL_REBUILD,
                word = currentWord.spelling,
                chunkCount = chunks.size,
                alreadyCommitted = startChunkForWord
            )

            // ── 分波处理（修复”失败不阻塞 + 计算后丢弃后续所有块”——用户反馈 ①②③ 的同一根因）──
            // 把待整理的块按 currentMaxConcurrent 切成若干「波」，一次只启动**一波**：
            //   · 一波内所有块并发整理（受 Semaphore 并发数 + EdgeRateLimiter 每分钟请求数约束），
            //     各块内部最多重试 MAX_EDGE_RETRIES 次；按块序逐块入库（连续前缀 = 干净续传点）。
            //   · 本波**全部成功并提交**后才启动下一波；某块重试耗尽 → 回传 Failed → 提交循环按序 await 到它时
            //     抛 PoolBuildDataException 中止，**绝不预跑后续波**。
            // 由此：① 失败会”阻塞”，不会继续整理后面波的块（用户要求②：四成功一失败只重试那一个、三成功两失败两个并发重试——
            //          成功块的协程已结束、其槽空闲，提交循环阻塞在最低位失败块上，自然只剩失败块在跑）；
            //       ② 失败时至多丢弃「本波内、失败块之后已算完的块」(< 并发数)，而非”从失败点到末尾的所有块”
            //          → 详情页不再出现”失败点之后整片变绿又被清空”（用户反馈①）；
            //       ③ 已提交连续前缀干净，手动填块推进 1 块后续传从该块之后继续、绝不重算覆盖（用户反馈③）。
            // 失败块回传 ChunkOutcome.Failed（不在 async 内抛）→ 见 [ChunkOutcome] 注释：失败只在提交循环按序 await 到它时
            //   才浮现，此前成功前缀必已提交并上报，续传点不会退化。
            // 注意：committedEdges 已在上方以”续传起点边数”初始化；committedChunks 为已提交的连续前缀块数（即续传点）。
            var committedChunks = startChunkForWord
            waveLoop@ while (committedChunks < chunks.size) {
                if (isCancelled()) throw CancellationException("词池构建已停止")
                val waveStart = committedChunks
                val waveEnd = (waveStart + currentMaxConcurrent).coerceAtMost(chunks.size)
                coroutineScope {
                    val deferreds = (waveStart until waveEnd).associateWith { chunkIndex ->
                        async {
                            semaphore.withPermit {
                                awaitWhilePaused(isPaused, isCancelled)
                                if (isCancelled()) throw CancellationException("词池构建已停止")
                                rateLimiter.acquire()
                                if (isCancelled()) throw CancellationException("词池构建已停止")

                                // chunk 本身就是完整的 WordDetails，直接构建 prompt，无需回查数据库。
                                val chunk = chunks[chunkIndex]
                                val prompt = EdgePromptBuilder.buildEdgePrompt(currentWord, chunk)
                                when (val result =
                                    callAiForEdges(prompt, currentWord, chunk, unwrapEnabled, repairEnabled, currentRetryMode, wordFailureTally) { attempt, raw, error, success ->
                                        // 每一次尝试（含失败重试）都实时反映到详情页对应方块。
                                        liveMonitor.recordAttempt(
                                            dictionaryId = dictionaryId,
                                            taskType = BackgroundTaskType.WORD_POOL_REBUILD,
                                            word = currentWord.spelling,
                                            chunkIndex = chunkIndex,
                                            attempt = attempt,
                                            response = raw,
                                            error = error,
                                            success = success
                                        )
                                    }) {
                                    is EdgeCallResult.Success -> {
                                        // 合规响应（含合法空数组 / 全被阈值过滤）均算成功。
                                        successfulAiCalls.incrementAndGet()
                                        ChunkOutcome.Ok(
                                            result.edges.mapNotNull { edge ->
                                                val other = chunk.getOrNull(edge.index) ?: return@mapNotNull null
                                                toEdgeEntity(currentWord, other, edge, dictionaryId)
                                            }
                                        )
                                    }
                                    // 该块重试耗尽仍返回不合规数据 → 回传失败（不抛），交提交循环按序处理。
                                    is EdgeCallResult.Failure -> ChunkOutcome.Failed(chunkIndex, result.error, result.attempts)
                                }
                            }
                        }
                    }

                    // 按块序提交本波：等待第 chunkIndex 块算完即入库，再上报「已提交块数」（连续前缀，安全续传点）。
                    for (chunkIndex in waveStart until waveEnd) {
                        val outcome = deferreds[chunkIndex]!!.await()
                        // 取消：停止提交，避免把”取消产生的空结果块”误记为已完成（否则续传会跳过未整理的块）。
                        if (isCancelled()) throw CancellationException("词池构建已停止")
                        when (outcome) {
                            is ChunkOutcome.Ok -> {
                                if (outcome.edges.isNotEmpty()) {
                                    persistStagedGeneratedEdges(outcome.edges)
                                }
                                committedEdges += outcome.edges.size
                                committedChunks = chunkIndex + 1
                                onProgress(wordsDone, totalWords, encodeProgress(currentWord.spelling, committedChunks, chunks.size, committedEdges))
                                Log.d("WordPoolRepo", "  块[$committedChunks/${chunks.size}] '${currentWord.spelling}' vs ${chunks[chunkIndex].size}词 → ${outcome.edges.size}条边已提交")
                            }
                            is ChunkOutcome.Failed -> {
                                // 此刻连续前缀 [startChunkForWord, chunkIndex) 已全部入库并上报，续传点 = chunkIndex。
                                // 抛出中止整个构建：coroutineScope 取消本波其余在算的块；已落库进度供修复后从当前词当前块续传。
                                throw PoolBuildDataException(
                                    "Pool build stopped: word '${currentWord.spelling}' chunk ${outcome.chunkIndex + 1}/${chunks.size} " +
                                        "returned non-compliant data after ${outcome.attempts} attempts.\n" +
                                        "Reason: ${outcome.error}\n" +
                                        "Completed progress has been preserved. Please check your AI service/model configuration and resume."
                                )
                            }
                        }
                    }
                }
                // 取消时连续前缀未推进到本波末尾 → 跳出，不进入下一波。
                if (isCancelled()) throw CancellationException("词池构建已停止")
            }

            // 该词处理完毕：完成数 +1，发出一次进度（块数清零，待下一个词重新填充）。
            // 取消时不把”未整理完的当前词”计为已完成（否则续传会跳过该词，缺块）。
            if (isCancelled()) throw CancellationException("词池构建已停止")
            val done = processed.incrementAndGet()
            onProgress(done, totalWords, encodeProgress(currentWord.spelling, 0, 0, committedEdges))
        }

        // index 0 的词无前驱，处理结束后计入完成数，使进度抵达 100%。
        if (isCancelled()) throw CancellationException("词池构建已停止")
        processed.incrementAndGet()
        onProgress(processed.get().coerceAtMost(totalWords), totalWords, null)
        liveMonitor.clear()

        // ============================================================
        // 构建完成。任一分块在重试耗尽后失败都会提前抛 PoolBuildDataException 中止，
        // 因此能走到这里就意味着所有 AI 调用都拿到了合规响应。
        // ============================================================
        if (successfulAiCalls.get() > 0) {
            Log.i("WordPoolRepo", "AI 调用全部成功，共 ${successfulAiCalls.get()} 次")
        }

        // ============================================================
        // 整理完成：直接用当前边构建词池。
        // 提纯（AI 评估并降权劣质边）已抽离为独立的手动触发任务（见 reviewPools），
        // 不再附加在整理之后。
        // ============================================================
        coroutineContext.ensureActive()
        return rebuildPoolsFromStagedDictionaryEdges(dictionaryId)
    }

    // ── QUALITY_FIRST 小工具 ──

    /**
     * 进度消息编码：单词|已提交块数|总块数|本词已提交的边数。详情页据此解析展示，块级续传也据此恢复续传点。
     * 「已提交块数」是连续前缀（按块序逐块入库后才 +1），因此可安全作为续传起点。
     */
    private fun encodeProgress(word: String, chunksDone: Int, chunkTotal: Int, edges: Int): String =
        "$word|$chunksDone|$chunkTotal|$edges"

    /**
     * 解析持久化的进度消息，算出断点词应从第几块续传；返回 0 表示整词重做（无有效续传点）。
     * **与 rebuildMode 无关**：FULL 被中断后续传也要按块恢复（是否清库由调用方的 hasResumableWork 决定，与此处解耦）。
     * 校验（任一不满足都回退 0，安全退化为整词重做）：
     *   1) firstIndexToProcess 在范围内，且消息里的词 == 断点词（防词集变动 / 消息来自已完成的其它词）；
     *   2) 消息里的总块数 == 断点词当下应有块数 ceil(firstIndexToProcess/chunkSize)（防 chunkSize 设置变更）；
     *   3) chunksDone / chunkTotal 必须 > 0（词刚完成时编码为 0|0，解析失败时缺字段，均回退 0）。
     */
    private fun resolveStartChunk(
        message: String?,
        allWords: List<WordDetails>,
        firstIndexToProcess: Int,
        chunkSize: Int
    ): Int {
        if (message.isNullOrBlank()) return 0
        if (chunkSize <= 0) return 0
        if (firstIndexToProcess !in allWords.indices) return 0
        val parts = message.split("|")
        if (parts.size < 3) return 0
        val word = parts[0]
        val chunksDone = parts[1].toIntOrNull() ?: return 0
        val chunkTotal = parts[2].toIntOrNull() ?: return 0
        if (chunksDone <= 0 || chunkTotal <= 0) return 0
        if (allWords[firstIndexToProcess].spelling != word) return 0
        // 断点词的前驱数 = firstIndexToProcess，应有块数 = ceil(firstIndexToProcess / chunkSize)。
        val expectedTotal = (firstIndexToProcess + chunkSize - 1) / chunkSize
        if (chunkTotal != expectedTotal) return 0
        return chunksDone.coerceIn(0, chunkTotal)
    }

    /** 暂停期间挂起等待（标志位由 BackgroundTaskManager 控制），取消时立即返回。 */
    private suspend fun awaitWhilePaused(isPaused: () -> Boolean, isCancelled: () -> Boolean) {
        while (isPaused() && !isCancelled()) {
            delay(300)
        }
    }

    private fun buildWordNoteEdge(
        selectedEdgeType: EdgeType
    ): com.xty.englishhelper.data.repository.pool.ParsedEdge {
        return com.xty.englishhelper.data.repository.pool.ParsedEdge(
            index = 0,
            edgeType = selectedEdgeType,
            status = "support",
            learningValue = 4,
            relationStrength = 3,
            confidence = 1.0,
            reason = "来自用户在背词页补充的单词便签关联",
            warningNote = null,
            evidenceSource = "user_note",
            register = "neutral",
            exampleSentence = null,
            difficultyCefr = null
        )
    }

    private suspend fun persistStagedGeneratedEdges(edges: List<WordEdgeEntity>) {
        wordEdgeDao.upsertStagedEdges(edges.map(WordEdgeEntity::toStagingEntity))
    }

    /** 把一条 AI 解析出的边转换为可入库实体（按 wordIdA < wordIdB 规范化）。 */
    private fun toEdgeEntity(
        currentWord: WordDetails,
        otherWord: WordDetails,
        edge: com.xty.englishhelper.data.repository.pool.ParsedEdge,
        dictionaryId: Long
    ): WordEdgeEntity {
        val (idA, idB) = if (currentWord.id < otherWord.id) {
            currentWord.id to otherWord.id
        } else {
            otherWord.id to currentWord.id
        }
        return WordEdgeEntity(
            wordIdA = idA,
            wordIdB = idB,
            edgeType = edge.edgeType.dbValue,
            dictionaryId = dictionaryId,
            status = edge.status,
            learningValue = edge.learningValue,
            relationStrength = edge.relationStrength,
            confidence = edge.confidence,
            reason = edge.reason,
            warningNote = edge.warningNote,
            evidenceSource = edge.evidenceSource,
            register = edge.register,
            exampleSentence = edge.exampleSentence,
            difficultyCefr = edge.difficultyCefr
        )
    }

    /**
     * 调用边生成 AI 并校验响应，最多重试 [MAX_EDGE_RETRIES] 次。
     * 返回 [EdgeCallResult.Success]（含 0..N 条合规边）或 [EdgeCallResult.Failure]（重试耗尽 / 不可重试），
     * Failure 携带**实际尝试次数**用于准确的中止报告（不再硬编码 MAX_EDGE_RETRIES）。
     * 注意：空数组 `[]`、或边全部被硬阈值过滤后为空，都属于 **成功**（合规且有意义），不视为失败。
     *
     * [retryMode] 控制失败后的行为（详见 [PoolRetryMode]）：
     *   · AGGRESSIVE：不可重试错误立即返回 Failure；可重试错误短延迟 / 退避后重试。
     *   · LENIENT：任何失败都继续重试（含原本不可重试的错误），仅在重试耗尽后返回 Failure；间隔按 [failureTally] 计算。
     * [failureTally] 为**当前词**累计失败次数（该词所有块共享）；每次失败 +1，宽松模式据此算等待间隔。
     *
     * [onAttempt] 在**每一次**尝试结束时回调一次（attempt 从 0 起），把服务器原文 [raw]、失败原因与成败实时反映到详情页方块。
     * raw 提到循环作用域，故解析失败（RetryableEdgeException）时也能附上导致失败的服务器原文；网络等异常拿不到响应时为 null。
     */
    private suspend fun callAiForEdges(
        prompt: String,
        target: WordDetails,
        window: List<WordDetails>,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean,
        retryMode: PoolRetryMode,
        failureTally: AtomicInteger,
        onAttempt: (attempt: Int, raw: String?, error: String?, success: Boolean) -> Unit
    ): EdgeCallResult {
        var lastError: String? = null
        for (attempt in 0 until MAX_EDGE_RETRIES) {
            var raw: String? = null
            try {
                raw = callAi(prompt)
                if (raw.isBlank()) {
                    Log.w("WordPoolRepo", "AI 返回空响应 (第 ${attempt + 1}/$MAX_EDGE_RETRIES 次)")
                    lastError = "AI returned empty response"
                    onAttempt(attempt, raw, lastError, false)
                    waitAfterFailure(attempt, retryMode, failureTally, null)
                    continue
                }
                val normalized = EdgeParser.normalizeResponse(raw, unwrapEnabled, repairEnabled)
                if (normalized.isBlank()) {
                    Log.w("WordPoolRepo", "响应归一化后为空 (第 ${attempt + 1}/$MAX_EDGE_RETRIES 次), raw=${raw.take(200)}")
                    lastError = "Normalized response is empty"
                    onAttempt(attempt, raw, lastError, false)
                    waitAfterFailure(attempt, retryMode, failureTally, null)
                    continue
                }
                val parsed = EdgeParser.parseAndValidateEdgeResponse(normalized, window.size)
                val filtered = EdgeParser.applyHardThresholds(parsed, target, window)
                onAttempt(attempt, raw, null, true)
                return EdgeCallResult.Success(filtered)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NonRetryableEdgeException) {
                lastError = e.message?.take(200) ?: e.javaClass.simpleName
                Log.w("WordPoolRepo", "不可重试错误 (第 ${attempt + 1} 次, mode=$retryMode)", e)
                onAttempt(attempt, raw, lastError, false)
                // 积极模式：立即失败。宽松模式：照样重试（加间隔），仅在重试耗尽后失败。
                if (retryMode == PoolRetryMode.AGGRESSIVE) {
                    return EdgeCallResult.Failure(lastError ?: "Non-retryable error", attempt + 1)
                }
                waitAfterFailure(attempt, retryMode, failureTally, null)
            } catch (e: RetryableEdgeException) {
                // 解析器判定响应格式不合规 —— 按定义可重试。
                lastError = e.message?.take(200) ?: "Non-compliant response format"
                Log.w("WordPoolRepo", "AI 响应格式不合规 (第 ${attempt + 1}/$MAX_EDGE_RETRIES 次)，重试中", e)
                onAttempt(attempt, raw, lastError, false)
                waitAfterFailure(attempt, retryMode, failureTally, null)
            } catch (e: Exception) {
                val retryable = AiErrorUtils.isRetryableError(e)
                lastError = e.message?.take(200) ?: e.javaClass.simpleName
                Log.w("WordPoolRepo", "AI 调用失败 (第 ${attempt + 1}/$MAX_EDGE_RETRIES 次, retryable=$retryable, mode=$retryMode)", e)
                onAttempt(attempt, raw, lastError, false)
                // 积极模式遇不可重试错误立即失败；宽松模式一律重试。
                if (retryMode == PoolRetryMode.AGGRESSIVE && !retryable) {
                    return EdgeCallResult.Failure(lastError ?: "Non-retryable error", attempt + 1)
                }
                waitAfterFailure(attempt, retryMode, failureTally, if (retryable) e else null)
            }
        }
        Log.w("WordPoolRepo", "AI 调用在 $MAX_EDGE_RETRIES 次尝试后仍失败 (mode=$retryMode): $lastError")
        return EdgeCallResult.Failure(lastError ?: "AI call failed (retried $MAX_EDGE_RETRIES times)", MAX_EDGE_RETRIES)
    }

    /**
     * 一次失败后的处理：把失败计入 [failureTally]（当前词累计），并在「还会重试」时按模式等待。
     *   · LENIENT：等待 = min(累计失败次数 × [LENIENT_RETRY_UNIT_MS], [LENIENT_RETRY_MAX_MS])。
     *     即「当前词所有块各自失败次数之和 × 10 秒」，上限 2 分钟——失败越多等得越久，给限流/过载更多恢复时间。
     *   · AGGRESSIVE：[e] 非空（可重试异常）按 [AiErrorUtils.retryDelay] 退避，否则线性 1s/2s/3s（原行为）。
     * 计数在「是否等待」判断之前自增：即便本次是最后一次尝试（自身不再等待），其它并发块后续的等待也应把它算进去。
     */
    private suspend fun waitAfterFailure(
        attempt: Int,
        retryMode: PoolRetryMode,
        failureTally: AtomicInteger,
        e: Exception?
    ) {
        val totalFailures = failureTally.incrementAndGet()
        if (attempt >= MAX_EDGE_RETRIES - 1) return // 最后一次失败后不再等待
        val delayMs = when (retryMode) {
            PoolRetryMode.LENIENT ->
                (totalFailures.toLong() * LENIENT_RETRY_UNIT_MS).coerceAtMost(LENIENT_RETRY_MAX_MS)
            PoolRetryMode.AGGRESSIVE ->
                if (e != null) AiErrorUtils.retryDelay(e, attempt) else 1000L * (attempt + 1)
        }
        if (delayMs > 0) delay(delayMs)
    }

    // ── Reviewer ──

    /** Builds the final pool plan from isolated staging edges plus authoritative user edges. */
    private suspend fun rebuildPoolsFromStagedDictionaryEdges(dictionaryId: Long): List<BuiltPoolWithWordIds> {
        val validWordIds = wordDao.getWordLabels(dictionaryId).mapTo(hashSetOf()) { it.id }
        if (validWordIds.isEmpty()) return emptyList()
        val edgeByKey = linkedMapOf<String, WordEdgeEntity>()
        wordEdgeDao.getStagedEdges(dictionaryId)
            .map(WordEdgeStagingEntity::toLiveEntity)
            .forEach { edge -> edgeByKey[edge.normalizedKey()] = edge }
        wordEdgeDao.getEdgesBySource(dictionaryId, "user_note")
            .forEach { edge -> edgeByKey[edge.normalizedKey()] = edge }
        val effectiveEdges = edgeByKey.values.filter { edge ->
            edge.wordIdA != edge.wordIdB &&
                edge.wordIdA in validWordIds &&
                edge.wordIdB in validWordIds &&
                EdgeType.fromDbValue(edge.edgeType) != null &&
                edge.confidence >= SIGNIFICANT_EDGE_MIN_CONFIDENCE &&
                edge.status != EXCLUDED_POOL_EDGE_STATUS
        }
        val adjacency = mutableMapOf<Long, MutableSet<Long>>()
        val degreeByWordId = mutableMapOf<Long, Int>()
        effectiveEdges.forEach { edge ->
            adjacency.getOrPut(edge.wordIdA) { linkedSetOf() }.add(edge.wordIdB)
            adjacency.getOrPut(edge.wordIdB) { linkedSetOf() }.add(edge.wordIdA)
            degreeByWordId[edge.wordIdA] = (degreeByWordId[edge.wordIdA] ?: 0) + 1
            degreeByWordId[edge.wordIdB] = (degreeByWordId[edge.wordIdB] ?: 0) + 1
        }

        if (degreeByWordId.isEmpty()) return emptyList()
        val membersByPoolIndex = QualityFirstPoolPlanner.planAdjacency(adjacency).pools
        if (membersByPoolIndex.isEmpty()) return emptyList()

        val scorerByPoolIndex = mutableMapOf<Int, PoolQualityScorer.Accumulator>()
        val poolIndicesByWordId = mutableMapOf<Long, MutableSet<Int>>()
        membersByPoolIndex.forEachIndexed { poolIndex, members ->
            members.forEach { wordId ->
                poolIndicesByWordId.getOrPut(wordId) { linkedSetOf() }.add(poolIndex)
            }
        }
        effectiveEdges.forEach { edge ->
            val commonPools = poolIndicesByWordId[edge.wordIdA].orEmpty()
                .intersect(poolIndicesByWordId[edge.wordIdB].orEmpty())
            commonPools.forEach { poolIndex ->
                scorerByPoolIndex
                    .getOrPut(poolIndex) { PoolQualityScorer.Accumulator() }
                    .accept(edge)
            }
        }

        return membersByPoolIndex.mapIndexed { poolIndex, members ->
            BuiltPoolWithWordIds(
                focusWordId = members.maxWithOrNull(
                    compareBy<Long> { degreeByWordId[it] ?: 0 }.thenByDescending { it }
                ),
                memberWordIds = members,
                qualityScore = scorerByPoolIndex[poolIndex]?.score() ?: 0
            )
        }
    }

    // ── AI helpers ──

    private suspend fun tryAiBatchCompletion(
        candidates: List<PoolCandidate>,
        existingBuild: BalancedPoolBuild
    ): BalancedPoolBuild {
        val assignedIndices = existingBuild.pools.flatMap { it.memberIndices }.toSet()
        val orphans = candidates.filter { it.index !in assignedIndices }

        if (orphans.isEmpty()) return existingBuild

        val allAiGroups = mutableListOf<List<Int>>()
        orphans.chunked(40).forEach { batch ->
            coroutineContext.ensureActive()
            val prompt = buildString {
                appendLine("以下单词来自同一词库。找出其中形近（拼写相似、易混淆）或意近（语义相关）的分组，每组至少2词。")
                appendLine("返回严格JSON: [[0,3],[1,5,12]]（数字为列表序号，不含无关词）。")
                appendLine(com.xty.englishhelper.util.Constants.JSON_STRICT_RULES)
                appendLine()
                batch.forEachIndexed { i, c ->
                    val firstMeaning = c.meanings.firstOrNull() ?: ""
                    appendLine("$i. ${c.spelling}: $firstMeaning")
                }
            }

            val groups = callAiForGroups(prompt, batch.size)
            groups.forEach { group ->
                val globalGroup = group.mapNotNull { localIdx ->
                    if (localIdx in batch.indices) batch[localIdx].index else null
                }
                if (globalGroup.size >= 2) {
                    allAiGroups.add(globalGroup)
                }
            }
        }

        return if (allAiGroups.isEmpty()) existingBuild
        else engine.mergeAiGroupsWithRelations(candidates, existingBuild, allAiGroups)
    }

    private suspend fun callAiForGroups(prompt: String, listSize: Int): List<List<Int>> {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_EDGE_RETRIES) {
            try {
                val raw = callAi(prompt)
                if (raw.isBlank()) {
                    Log.w("WordPoolRepo", "AI batch returned empty response on attempt ${attempt + 1}")
                    lastException = RetryableEdgeException("Empty AI response")
                    if (attempt < MAX_EDGE_RETRIES - 1) delay(1000L * (attempt + 1))
                    continue
                }
                val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
                val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
                val normalized = EdgeParser.normalizeResponse(raw, unwrapEnabled, repairEnabled)
                if (normalized.isBlank()) {
                    Log.w("WordPoolRepo", "AI batch normalized blank on attempt ${attempt + 1}, raw=${raw.take(200)}")
                    lastException = RetryableEdgeException("Normalized response is blank")
                    if (attempt < MAX_EDGE_RETRIES - 1) delay(1000L * (attempt + 1))
                    continue
                }
                return EdgeParser.parseJsonIntArrayOfArrays(normalized, listSize)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableEdgeException) {
                lastException = e
                Log.w("WordPoolRepo", "AI batch malformed on attempt ${attempt + 1}/$MAX_EDGE_RETRIES, retrying", e)
                if (attempt < MAX_EDGE_RETRIES - 1) {
                    delay(1000L * (attempt + 1))
                }
            } catch (e: Exception) {
                lastException = e
                val retryable = AiErrorUtils.isRetryableError(e)
                Log.w("WordPoolRepo", "AI batch call attempt ${attempt + 1}/$MAX_EDGE_RETRIES failed (retryable=$retryable)", e)
                if (!retryable) {
                    throw PoolBuildDataException("均衡 AI 补充分组失败：${e.message ?: e.javaClass.simpleName}")
                }
                if (attempt < MAX_EDGE_RETRIES - 1) {
                    delay(AiErrorUtils.retryDelay(e, attempt))
                }
            }
        }
        Log.w("WordPoolRepo", "AI batch call failed after $MAX_EDGE_RETRIES attempts", lastException)
        throw PoolBuildDataException(
            "均衡 AI 补充分组在 $MAX_EDGE_RETRIES 次尝试后仍失败：" +
                (lastException?.message ?: "未收到可用响应")
        )
    }

    private suspend fun callAi(prompt: String): String {
        val config = settingsDataStore.getAiConfig(AiSettingsScope.POOL)
        val client = aiApiClientProvider.getClient(config.provider)

        return client.sendMessage(
            url = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = 4096
        )
    }

    // ── TEMPORARY: Entry Type Classification ──

    override suspend fun classifyEntryTypes(
        dictionaryId: Long,
        isCancelled: () -> Boolean,
        onProgress: (classified: Int, total: Int) -> Unit
    ): Int {
        return entryTypeClassifier.classify(dictionaryId, isCancelled, onProgress)
    }

    // ── 手动填块（敏感词等模型拒答时人工补数据；坐标系与构建侧严格一致） ──

    override suspend fun getManualChunkContext(
        dictionaryId: Long,
        wordSpelling: String,
        chunkIndex: Int,
        totalChunks: Int
    ): ManualChunkContext? {
        val chunkSize = settingsDataStore.getPoolWindowSize()
        if (chunkSize <= 0) return null
        val allWords = wordDao.getWordsByDictionaryOnce(dictionaryId).map { it.toDomain() }
        val wordIndex = allWords.indexOfFirst { it.spelling == wordSpelling }
        if (wordIndex < 0) return null
        // 与 resolveStartChunk 同样的坐标系校验：当前 chunkSize 推算的总块数须与持久化的一致。
        val expectedTotal = (wordIndex + chunkSize - 1) / chunkSize
        if (expectedTotal != totalChunks) return null
        if (chunkIndex !in 0 until totalChunks) return null
        val target = allWords[wordIndex]
        val from = chunkIndex * chunkSize
        val to = ((chunkIndex + 1) * chunkSize).coerceAtMost(wordIndex)
        if (from >= to) return null
        val window = allWords.subList(from, to)
        val candidates = window.mapIndexed { i, w ->
            val m = w.meanings.firstOrNull()
            ManualChunkCandidate(
                index = i,
                spelling = w.spelling,
                pos = m?.pos ?: "",
                definition = m?.definition ?: ""
            )
        }
        return ManualChunkContext(
            targetSpelling = target.spelling,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            candidates = candidates,
            promptText = EdgePromptBuilder.buildEdgePrompt(target, window)
        )
    }

    override suspend fun manualFillChunk(
        dictionaryId: Long,
        wordSpelling: String,
        chunkIndex: Int,
        totalChunks: Int,
        json: String
    ): ManualFillResult = withEdgeWriteLock(dictionaryId) {
        manualFillChunkLocked(dictionaryId, wordSpelling, chunkIndex, totalChunks, json)
    }

    private suspend fun manualFillChunkLocked(
        dictionaryId: Long,
        wordSpelling: String,
        chunkIndex: Int,
        totalChunks: Int,
        json: String
    ): ManualFillResult {
        val hasStagingSession = wordPoolDao.getStrategyStates(dictionaryId)
            .any { it.strategy == QUALITY_FIRST_STAGING_STATE }
        if (!hasStagingSession) {
            return ManualFillResult(
                false,
                error = "This task predates atomic staging. Restart the pool build before filling chunks manually."
            )
        }
        val chunkSize = settingsDataStore.getPoolWindowSize()
        if (chunkSize <= 0) return ManualFillResult(false, error = "Invalid window size")
        val allWords = wordDao.getWordsByDictionaryOnce(dictionaryId).map { it.toDomain() }
        val wordIndex = allWords.indexOfFirst { it.spelling == wordSpelling }
        if (wordIndex < 0) return ManualFillResult(false, error = "Resume word '$wordSpelling' not found")
        val expectedTotal = (wordIndex + chunkSize - 1) / chunkSize
        if (expectedTotal != totalChunks) {
            return ManualFillResult(false, error = "Window size changed, coordinate mismatch. Cannot fill manually (resume with matching settings, or do a full rebuild).")
        }
        if (chunkIndex !in 0 until totalChunks) return ManualFillResult(false, error = "Chunk index out of bounds")
        val target = allWords[wordIndex]
        val from = chunkIndex * chunkSize
        val to = ((chunkIndex + 1) * chunkSize).coerceAtMost(wordIndex)
        if (from >= to) return ManualFillResult(false, error = "No candidates in this chunk")
        val window = allWords.subList(from, to)

        // 复用与 AI 完全相同的解析 / 校验链；空数组 [] 合法（该块判定无边，可直接跳过敏感块）。
        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        val edges: List<WordEdgeEntity> = try {
            val normalized = EdgeParser.normalizeResponse(json, unwrapEnabled, repairEnabled)
            if (normalized.isBlank()) return ManualFillResult(false, error = "Content is empty")
            val parsed = EdgeParser.parseAndValidateEdgeResponse(normalized, window.size)
            val filtered = EdgeParser.applyHardThresholds(parsed, target, window)
            filtered.mapNotNull { edge ->
                val other = window.getOrNull(edge.index) ?: return@mapNotNull null
                toEdgeEntity(target, other, edge, dictionaryId)
            }
        } catch (e: Exception) {
            return ManualFillResult(false, error = "JSON parse error: ${e.message?.take(160) ?: e.javaClass.simpleName}")
        }

        // 幂等写入 staging；正式边只会在整个构建完成时原子交换。
        window.map { it.id }.chunked(900).forEach { batch ->
            wordEdgeDao.deleteStagedEdgesForWordAgainst(dictionaryId, target.id, batch)
        }
        if (edges.isNotEmpty()) persistStagedGeneratedEdges(edges)

        // 刷新实时网格（仅当现场正是该词时生效；进程重启后网格为空则 no-op，续传会重填）。
        liveMonitor.recordAttempt(
            dictionaryId = dictionaryId,
            taskType = BackgroundTaskType.WORD_POOL_REBUILD,
            word = wordSpelling,
            chunkIndex = chunkIndex,
            attempt = 0,
            response = "(Manual fill)\n$json",
            error = null,
            success = true
        )

        return ManualFillResult(
            success = true,
            insertedEdges = edges.size,
            wordComplete = chunkIndex + 1 >= totalChunks
        )
    }
}
