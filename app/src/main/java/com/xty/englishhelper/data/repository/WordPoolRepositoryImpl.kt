package com.xty.englishhelper.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.xty.englishhelper.data.local.AppDatabase
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.local.entity.WordPoolEntity
import com.xty.englishhelper.data.local.entity.WordPoolMemberEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.data.repository.pool.EdgeParser
import com.xty.englishhelper.data.repository.pool.EdgePromptBuilder
import com.xty.englishhelper.data.repository.pool.EdgeRateLimiter
import com.xty.englishhelper.data.repository.pool.EdgeReviewer
import com.xty.englishhelper.data.repository.pool.EntryTypeClassifier
import com.xty.englishhelper.data.repository.pool.NonRetryableEdgeException
import com.xty.englishhelper.data.repository.pool.PoolQualityScorer
import com.xty.englishhelper.data.repository.pool.RetryableEdgeException
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.pool.BuiltPool
import com.xty.englishhelper.domain.pool.MutableUnionFind
import com.xty.englishhelper.domain.pool.PoolCandidate
import com.xty.englishhelper.domain.pool.UnionFind
import com.xty.englishhelper.domain.pool.WordPoolEngine
import com.xty.englishhelper.domain.repository.WordPoolRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private data class BuiltPoolWithWordIds(
    val focusWordId: Long?,
    val memberWordIds: List<Long>,
    val qualityScore: Int? = null
)

@Singleton
class WordPoolRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val wordPoolDao: WordPoolDao,
    private val wordEdgeDao: WordEdgeDao,
    private val wordDao: WordDao,
    private val aiApiClientProvider: AiApiClientProvider,
    private val settingsDataStore: SettingsDataStore,
    private val edgeReviewer: EdgeReviewer,
    private val entryTypeClassifier: EntryTypeClassifier
) : WordPoolRepository {

    private val engine = WordPoolEngine()

    companion object {
        private const val MAX_EDGE_RETRIES = 4
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

    override suspend fun rebuildPools(
        dictionaryId: Long,
        strategy: PoolStrategy,
        startIndex: Int,
        rebuildMode: RebuildMode,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onProgress: (Int, Int) -> Unit
    ) {
        val words = wordDao.getWordsByDictionaryOnce(dictionaryId)
        val total = words.size
        onProgress(0, total)

        val pools: List<BuiltPoolWithWordIds> = when (strategy) {
            PoolStrategy.BALANCED, PoolStrategy.BALANCED_WITH_AI ->
                buildBalanced(words, dictionaryId, strategy, onProgress, total)
            PoolStrategy.QUALITY_FIRST ->
                buildQualityFirst(words, dictionaryId, startIndex, rebuildMode, isCancelled, isPaused, onProgress, total)
        }

        // Ensure not cancelled before writing
        coroutineContext.ensureActive()

        // Single transaction write
        val now = System.currentTimeMillis()
        db.withTransaction {
            wordPoolDao.deleteByDictionaryAndStrategy(dictionaryId, strategy.dbValue)
            pools.forEach { builtPool ->
                val poolId = wordPoolDao.insertPool(
                    WordPoolEntity(
                        dictionaryId = dictionaryId,
                        focusWordId = builtPool.focusWordId,
                        strategy = strategy.dbValue,
                        algorithmVersion = strategy.algorithmVersion,
                        updatedAt = now,
                        qualityScore = builtPool.qualityScore
                    )
                )
                wordPoolDao.insertMembers(
                    builtPool.memberWordIds.map {
                        WordPoolMemberEntity(wordId = it, poolId = poolId)
                    }
                )
            }
        }
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
        val edges = wordEdgeDao.getAllEdges(dictionaryId)
        val adj = mutableMapOf<Long, MutableMap<Long, MutableSet<com.xty.englishhelper.domain.model.EdgeType>>>()
        edges.forEach { e ->
            val type = com.xty.englishhelper.domain.model.EdgeType.fromDbValue(e.edgeType) ?: return@forEach
            adj.getOrPut(e.wordIdA) { mutableMapOf() }
                .getOrPut(e.wordIdB) { mutableSetOf() }
                .add(type)
            adj.getOrPut(e.wordIdB) { mutableMapOf() }
                .getOrPut(e.wordIdA) { mutableSetOf() }
                .add(type)
        }
        return adj
    }

    // ── BALANCED build ──

    private suspend fun buildBalanced(
        words: List<WordWithDetails>,
        dictionaryId: Long,
        strategy: PoolStrategy,
        onProgress: (Int, Int) -> Unit,
        total: Int
    ): List<BuiltPoolWithWordIds> {
        val associations = wordPoolDao.getAssociationsInDictionary(dictionaryId)
        val assocMap = mutableMapOf<Long, MutableList<Long>>()
        associations.forEach { pair ->
            assocMap.getOrPut(pair.wordId) { mutableListOf() }.add(pair.associatedWordId)
            assocMap.getOrPut(pair.associatedWordId) { mutableListOf() }.add(pair.wordId)
        }

        val candidates = words.mapIndexed { index, wwd ->
            coroutineContext.ensureActive()
            onProgress(index, total)
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

        var pools = engine.buildPools(candidates)
        onProgress(total, total)

        if (strategy == PoolStrategy.BALANCED_WITH_AI) {
            coroutineContext.ensureActive()
            pools = tryAiBatchCompletion(candidates, pools)
        }

        return pools.map { pool ->
            BuiltPoolWithWordIds(
                focusWordId = null,
                memberWordIds = pool.memberIndices.map { candidates[it].wordId }
            )
        }
    }

    // ── QUALITY_FIRST build (sliding window + edge graph, incremental) ──

    private suspend fun buildQualityFirst(
        words: List<WordWithDetails>,
        dictionaryId: Long,
        startIndex: Int,
        rebuildMode: RebuildMode,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onProgress: (Int, Int) -> Unit,
        total: Int
    ): List<BuiltPoolWithWordIds> {
        val domains = words.map { it.toDomain() }
        val windowSize = settingsDataStore.getPoolWindowSize()
        val maxConcurrent = settingsDataStore.getPoolMaxConcurrent()
        val requestsPerMinute = settingsDataStore.getPoolRequestsPerMinute()

        if (rebuildMode == RebuildMode.FULL) {
            wordEdgeDao.deleteByDictionary(dictionaryId)
        }

        val resumeIndex = if (startIndex >= 0) startIndex else domains.size - 1

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()

        val semaphore = Semaphore(maxConcurrent)
        val rateLimiter = EdgeRateLimiter(requestsPerMinute)

        val totalAiCalls = (1..resumeIndex).sumOf { i ->
            (i + windowSize - 1) / windowSize
        }
        val completedCalls = java.util.concurrent.atomic.AtomicInteger(0)

        for (i in resumeIndex.downTo(0)) {
            if (isCancelled()) {
                Log.d("WordPoolRepo", "QualityFirst build cancelled at index $i")
                break
            }

            val currentWord = domains[i]
            if (i == 0) continue

            data class WindowTask(val windowStart: Int, val windowEnd: Int)
            val windowTasks = mutableListOf<WindowTask>()
            var windowEnd = i
            while (windowEnd > 0) {
                val windowStart = maxOf(0, windowEnd - windowSize)
                windowTasks.add(WindowTask(windowStart, windowEnd))
                windowEnd = windowStart
            }

            // BUG 1 fix: collect all edges from concurrent async calls,
            // then do a single batch insert after all window tasks complete.
            // N1 fix: use single coroutineScope wrapping all async for true concurrency.
            val windowResults = coroutineScope {
                windowTasks.map { task ->
                    async {
                        semaphore.withPermit {
                            rateLimiter.acquire()
                            if (isCancelled()) return@withPermit emptyList()

                            val window = domains.subList(task.windowStart, task.windowEnd)
                            val prompt = EdgePromptBuilder.buildEdgePrompt(currentWord, window)
                            val aiResult = callAiForEdges(prompt, currentWord, window, unwrapEnabled, repairEnabled)

                            val count = completedCalls.incrementAndGet()
                            onProgress(count, totalAiCalls)

                            aiResult.mapNotNull { edge ->
                                if (edge.index !in window.indices) return@mapNotNull null
                                val otherWord = window[edge.index]
                                val (idA, idB) = if (currentWord.id < otherWord.id) {
                                    currentWord.id to otherWord.id
                                } else {
                                    otherWord.id to currentWord.id
                                }
                                WordEdgeEntity(
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
                        }
                    }
                }.awaitAll()
            }

            // BUG 1 fix: batch insert after all concurrent work is done
            val newEdges = windowResults.flatten()
            if (newEdges.isNotEmpty()) {
                wordEdgeDao.insertEdges(newEdges)
            }
        }

        // Review low-confidence edges with REVIEWER model
        coroutineContext.ensureActive()
        val allEdgesForReview = wordEdgeDao.getAllEdgesFull(dictionaryId)
        edgeReviewer.reviewEdgesWithAi(allEdgesForReview, domains, dictionaryId)

        // Build pools from ALL edges in DB
        coroutineContext.ensureActive()
        val allEdges = wordEdgeDao.getAllEdgesFull(dictionaryId)
        val wordIds = domains.map { it.id }.toSet()
        return rebuildPoolsFromEdges(allEdges, wordIds)
    }

    private suspend fun callAiForEdges(
        prompt: String,
        target: WordDetails,
        window: List<WordDetails>,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): List<com.xty.englishhelper.data.repository.pool.ParsedEdge> {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_EDGE_RETRIES) {
            try {
                val response = callAi(prompt)
                val normalized = EdgeParser.normalizeResponse(response, unwrapEnabled, repairEnabled)
                val parsed = EdgeParser.parseAndValidateEdgeResponse(normalized, window.size)
                return EdgeParser.applyHardThresholds(parsed, target, window)
            } catch (e: NonRetryableEdgeException) {
                Log.w("WordPoolRepo", "Non-retryable error on attempt ${attempt + 1}", e)
                return emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                val retryable = isRetryableError(e)
                Log.w("WordPoolRepo", "AI edge call attempt ${attempt + 1}/$MAX_EDGE_RETRIES failed (retryable=$retryable)", e)
                if (!retryable) return emptyList()
                if (attempt < MAX_EDGE_RETRIES - 1) {
                    // BUG 9 fix: use exponential backoff, longer delay for 429
                    val baseDelay = if (isRateLimitError(e)) 2000L else 1000L
                    delay(baseDelay * (attempt + 1))
                }
            }
        }
        Log.w("WordPoolRepo", "AI edge call failed after $MAX_EDGE_RETRIES attempts", lastException)
        return emptyList()
    }

    /**
     * BUG 9 fix: 429 rate-limit errors are retryable with exponential backoff.
     * Network timeouts and connection errors are explicitly handled.
     * N7 fix: default to false, only retry known transient errors.
     */
    private fun isRetryableError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        // Non-retryable: auth errors
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized") || msg.contains("invalid api key")) {
            return false
        }
        // Retryable: rate limit (handled with longer backoff in caller)
        if (isRateLimitError(e)) return true
        // Retryable: network timeouts and connection errors
        if (e is SocketTimeoutException || e is java.net.ConnectException) return true
        if (e is IOException && (msg.contains("timeout") || msg.contains("connect"))) return true
        if (e is kotlinx.coroutines.TimeoutCancellationException) return true
        // Retryable: server errors (5xx)
        if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")) return true
        // Default: do not retry unknown errors (programming errors, etc.)
        return false
    }

    private fun isRateLimitError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("429") || msg.contains("rate limit")
    }

    // ── Reviewer ──

    /**
     * Union-Find based pool extraction from edge graph.
     * BUG 2 fix: uses rank-based MutableUnionFind instead of naive parent-only version.
     * Splits oversized components (>MAX_POOL_SIZE).
     */
    private fun rebuildPoolsFromEdges(
        edges: List<WordEdgeEntity>,
        wordIds: Set<Long>
    ): List<BuiltPoolWithWordIds> {
        // Filter: only use edges with confidence >= 0.3 and status != "optional"
        val significantEdges = edges.filter {
            it.confidence >= 0.3 && it.status != "optional"
        }
        if (significantEdges.isEmpty()) return emptyList()

        // BUG 2 fix: use rank-based MutableUnionFind instead of naive version
        val uf = MutableUnionFind()
        significantEdges.forEach { e ->
            uf.add(e.wordIdA)
            uf.add(e.wordIdB)
        }
        significantEdges.forEach { uf.union(it.wordIdA, it.wordIdB) }

        // Group by root
        val components = mutableMapOf<Long, MutableList<Long>>()
        wordIds.forEach { id ->
            val root = uf.find(id)
            if (root !in components) components[root] = mutableListOf()
            components[root]!!.add(id)
        }

        // Build wordId->edges map for quality scoring
        val edgesByWordId = mutableMapOf<Long, MutableList<WordEdgeEntity>>()
        significantEdges.forEach { e ->
            edgesByWordId.getOrPut(e.wordIdA) { mutableListOf() }.add(e)
            edgesByWordId.getOrPut(e.wordIdB) { mutableListOf() }.add(e)
        }

        // Build pools from components with >=2 members, split large ones
        val maxPoolSize = 15
        val result = mutableListOf<BuiltPoolWithWordIds>()
        components.values.forEach { members ->
            if (members.size < 2) return@forEach
            if (members.size <= maxPoolSize) {
                val score = PoolQualityScorer.computePoolQualityScore(members, edgesByWordId)
                result.add(BuiltPoolWithWordIds(focusWordId = null, memberWordIds = members, qualityScore = score))
            } else {
                members.chunked(maxPoolSize).forEach { chunk ->
                    if (chunk.size >= 2) {
                        val score = PoolQualityScorer.computePoolQualityScore(chunk, edgesByWordId)
                        result.add(BuiltPoolWithWordIds(focusWordId = null, memberWordIds = chunk, qualityScore = score))
                    }
                }
            }
        }
        return result
    }

    // ── AI helpers ──

    private suspend fun tryAiBatchCompletion(
        candidates: List<PoolCandidate>,
        existingPools: List<BuiltPool>
    ): List<BuiltPool> {
        val assignedIndices = existingPools.flatMap { it.memberIndices }.toSet()
        val orphans = candidates.filter { it.index !in assignedIndices }

        if (orphans.isEmpty()) return existingPools

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

        return if (allAiGroups.isEmpty()) existingPools
        else engine.mergeAiGroups(candidates, existingPools, allAiGroups)
    }

    private suspend fun callAiForGroups(prompt: String, listSize: Int): List<List<Int>> {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_EDGE_RETRIES) {
            try {
                val response = callAi(prompt)
                val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
                val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
                val normalized = EdgeParser.normalizeResponse(response, unwrapEnabled, repairEnabled)
                return EdgeParser.parseJsonIntArrayOfArrays(normalized, listSize)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                val retryable = isRetryableError(e)
                Log.w("WordPoolRepo", "AI batch call attempt ${attempt + 1}/$MAX_EDGE_RETRIES failed (retryable=$retryable)", e)
                if (!retryable) return emptyList()
                if (attempt < MAX_EDGE_RETRIES - 1) {
                    val baseDelay = if (isRateLimitError(e)) 2000L else 1000L
                    delay(baseDelay * (attempt + 1))
                }
            }
        }
        Log.w("WordPoolRepo", "AI batch call failed after $MAX_EDGE_RETRIES attempts", lastException)
        return emptyList()
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
}
