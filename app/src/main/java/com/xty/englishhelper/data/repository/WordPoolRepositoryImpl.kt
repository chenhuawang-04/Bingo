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
import com.xty.englishhelper.data.repository.pool.AiErrorUtils
import com.xty.englishhelper.data.repository.pool.EdgeParser
import com.xty.englishhelper.data.repository.pool.EdgePromptBuilder
import com.xty.englishhelper.data.repository.pool.EdgeRateLimiter
import com.xty.englishhelper.data.repository.pool.EdgeReviewer
import com.xty.englishhelper.data.repository.pool.EntryTypeClassifier
import com.xty.englishhelper.data.repository.pool.NonRetryableEdgeException
import com.xty.englishhelper.data.repository.pool.PoolQualityScorer
import com.xty.englishhelper.data.repository.pool.RetryableEdgeException
import com.xty.englishhelper.data.repository.pool.WordStream
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.pool.BuiltPool
import com.xty.englishhelper.domain.pool.MutableUnionFind
import com.xty.englishhelper.domain.pool.PoolCandidate
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
        onProgress: (Int, Int, String?) -> Unit
    ) {
        val pools: List<BuiltPoolWithWordIds> = when (strategy) {
            PoolStrategy.BALANCED, PoolStrategy.BALANCED_WITH_AI -> {
                // BALANCED needs all candidates in memory for the engine
                val words = wordDao.getWordsByDictionaryOnce(dictionaryId)
                val total = words.size
                onProgress(0, total, null)
                buildBalanced(words, dictionaryId, strategy, isCancelled, isPaused, onProgress, total)
            }
            PoolStrategy.QUALITY_FIRST ->
                buildQualityFirstStreaming(dictionaryId, startIndex, rebuildMode, isCancelled, isPaused, onProgress)
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
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onProgress: (Int, Int, String?) -> Unit,
        total: Int
    ): List<BuiltPoolWithWordIds> {
        val associations = wordPoolDao.getAssociationsInDictionary(dictionaryId)
        val assocMap = mutableMapOf<Long, MutableList<Long>>()
        associations.forEach { pair ->
            assocMap.getOrPut(pair.wordId) { mutableListOf() }.add(pair.associatedWordId)
            assocMap.getOrPut(pair.associatedWordId) { mutableListOf() }.add(pair.wordId)
        }

        val candidates = words.mapIndexed { index, wwd ->
            if (isCancelled()) return emptyList()
            while (isPaused()) {
                kotlinx.coroutines.delay(500)
                if (isCancelled()) return emptyList()
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

        var pools = engine.buildPools(candidates)
        onProgress(total, total, null)

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

    // ── QUALITY_FIRST build (streaming, memory-efficient) ──

    /**
     * Lightweight word reference for history tracking.
     * Only stores fields needed for AI prompt building (id, spelling, first meaning).
     * ~50 bytes per word vs ~500 bytes for full WordDetails.
     */
    private data class WordRef(
        val id: Long,
        val spelling: String,
        val pos: String,
        val definition: String
    ) {
        companion object {
            fun from(details: WordDetails): WordRef {
                val m = details.meanings.firstOrNull()
                return WordRef(details.id, details.spelling, m?.pos ?: "", m?.definition ?: "")
            }
        }
    }

    /**
     * Streaming QUALITY_FIRST build.
     * Uses cursor-based pagination (WordStream) and lightweight WordRef for history.
     * Memory: ~2MB regardless of dictionary size.
     * Edge correctness: compares against ALL previous words (same as non-streaming).
     */
    private suspend fun buildQualityFirstStreaming(
        dictionaryId: Long,
        startIndex: Int,
        rebuildMode: RebuildMode,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onProgress: (Int, Int, String?) -> Unit
    ): List<BuiltPoolWithWordIds> {
        val windowSize = settingsDataStore.getPoolWindowSize()
        val maxConcurrent = settingsDataStore.getPoolMaxConcurrent()
        val requestsPerMinute = settingsDataStore.getPoolRequestsPerMinute()

        if (rebuildMode == RebuildMode.FULL) {
            wordEdgeDao.deleteByDictionary(dictionaryId)
        }

        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()

        val semaphore = Semaphore(maxConcurrent)
        val rateLimiter = EdgeRateLimiter(requestsPerMinute)

        // Count total words for progress
        val totalWords = wordDao.countAllWords(dictionaryId)
        val processedWords = java.util.concurrent.atomic.AtomicInteger(0)

        // Stream words in batches, process each against all previous words
        val stream = WordStream(wordDao, dictionaryId)
        val history = mutableListOf<WordRef>() // Lightweight refs (~50 bytes each)
        val resumeIndex = if (startIndex >= 0) startIndex else Int.MAX_VALUE
        var wordIndex = 0

        while (stream.hasNext()) {
            if (isCancelled()) {
                Log.d("WordPoolRepo", "QualityFirst streaming build cancelled")
                break
            }

            // Pause: wait here before loading next batch. Already-in-flight AI calls
            // continue running and their results are processed normally.
            while (isPaused()) {
                delay(500)
                if (isCancelled()) break
            }

            // Load next batch of words (full details needed for AI prompt)
            val batch = stream.take(windowSize)
            if (batch.isEmpty()) break

            // Process each word in batch against ALL previous words (chunked)
            for (currentWord in batch) {
                if (isCancelled()) break
                if (history.isEmpty()) {
                    history.add(WordRef.from(currentWord))
                    wordIndex++
                    val count = processedWords.incrementAndGet()
                    onProgress(count, totalWords, currentWord.spelling)
                    continue
                }

                // Skip edge generation for words before resume index, but still build history
                if (wordIndex < resumeIndex) {
                    history.add(WordRef.from(currentWord))
                    wordIndex++
                    val count = processedWords.incrementAndGet()
                    onProgress(count, totalWords, currentWord.spelling)
                    continue
                }

                // Split history into chunks for concurrent AI calls
                val chunks = history.chunked(windowSize)

                val windowResults = coroutineScope {
                    chunks.map { chunk ->
                        async {
                            semaphore.withPermit {
                                // Pause: wait before sending this AI request.
                                // Other in-flight requests complete normally.
                                while (isPaused()) {
                                    delay(200)
                                    if (isCancelled()) return@withPermit emptyList()
                                }

                                rateLimiter.acquire()
                                if (isCancelled()) return@withPermit emptyList()

                                // Batch load full WordDetails for this chunk from DB
                                // IMPORTANT: preserve input order — AI edge.index is based on prompt order
                                val chunkIds = chunk.map { it.id }
                                val detailsById = wordDao.getWordsByIds(chunkIds)
                                    .associateBy { it.word.id }
                                val chunkDetails = chunkIds.mapNotNull { detailsById[it]?.toDomain() }

                                val prompt = EdgePromptBuilder.buildEdgePrompt(currentWord, chunkDetails)
                                val aiResult = callAiForEdges(prompt, currentWord, chunkDetails, unwrapEnabled, repairEnabled)

                                aiResult.mapNotNull { edge ->
                                    if (edge.index !in chunkDetails.indices) return@mapNotNull null
                                    val otherWord = chunkDetails[edge.index]
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

                // Batch insert edges
                val newEdges = windowResults.flatten()
                if (newEdges.isNotEmpty()) {
                    wordEdgeDao.insertEdges(newEdges)
                }

                // Add current word to history (lightweight ref)
                history.add(WordRef.from(currentWord))
                wordIndex++

                // Update progress once per word (not per chunk)
                val count = processedWords.incrementAndGet()
                onProgress(count, totalWords, currentWord.spelling)
            }
        }

        // Review low-confidence edges with REVIEWER model
        coroutineContext.ensureActive()
        val allEdgesForReview = wordEdgeDao.getAllEdgesFull(dictionaryId)

        // Collect all word IDs for reviewer
        val allWordIds = mutableSetOf<Long>()
        allEdgesForReview.forEach { edge ->
            allWordIds.add(edge.wordIdA)
            allWordIds.add(edge.wordIdB)
        }

        // Load words for reviewer context (one-time cost, GC'd after review)
        val allWordsForReviewer = wordDao.getWordsByDictionaryOnce(dictionaryId)
        val domainsForReviewer = allWordsForReviewer.map { it.toDomain() }

        val reviewModified = edgeReviewer.reviewEdgesWithAi(allEdgesForReview, domainsForReviewer, dictionaryId)

        // Build pools from edges in DB; re-fetch only if reviewer modified any edges
        coroutineContext.ensureActive()
        val allEdges = if (reviewModified) {
            wordEdgeDao.getAllEdgesFull(dictionaryId)
        } else {
            allEdgesForReview
        }
        return rebuildPoolsFromEdges(allEdges, allWordIds)
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
                val raw = callAi(prompt)
                if (raw.isBlank()) {
                    Log.w("WordPoolRepo", "AI returned empty response on attempt ${attempt + 1}")
                    lastException = RetryableEdgeException("Empty AI response")
                    if (attempt < MAX_EDGE_RETRIES - 1) delay(1000L * (attempt + 1))
                    continue
                }
                val normalized = EdgeParser.normalizeResponse(raw, unwrapEnabled, repairEnabled)
                if (normalized.isBlank()) {
                    Log.w("WordPoolRepo", "Normalized response is blank on attempt ${attempt + 1}, raw=${raw.take(200)}")
                    lastException = RetryableEdgeException("Normalized response is blank")
                    if (attempt < MAX_EDGE_RETRIES - 1) delay(1000L * (attempt + 1))
                    continue
                }
                val parsed = EdgeParser.parseAndValidateEdgeResponse(normalized, window.size)
                return EdgeParser.applyHardThresholds(parsed, target, window)
            } catch (e: NonRetryableEdgeException) {
                Log.w("WordPoolRepo", "Non-retryable error on attempt ${attempt + 1}", e)
                return emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableEdgeException) {
                // Parser detected malformed response — retryable by definition
                lastException = e
                Log.w("WordPoolRepo", "Malformed AI response on attempt ${attempt + 1}/$MAX_EDGE_RETRIES, retrying", e)
                if (attempt < MAX_EDGE_RETRIES - 1) {
                    delay(1000L * (attempt + 1))
                }
            } catch (e: Exception) {
                lastException = e
                val retryable = AiErrorUtils.isRetryableError(e)
                Log.w("WordPoolRepo", "AI edge call attempt ${attempt + 1}/$MAX_EDGE_RETRIES failed (retryable=$retryable)", e)
                if (!retryable) return emptyList()
                if (attempt < MAX_EDGE_RETRIES - 1) {
                    delay(AiErrorUtils.retryDelay(e, attempt))
                }
            }
        }
        Log.w("WordPoolRepo", "AI edge call failed after $MAX_EDGE_RETRIES attempts", lastException)
        return emptyList()
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
                // Sort members by edge connectivity so connected words stay in same chunk
                // Pre-compute degree from edgesByWordId (O(edges) instead of O(members*edges))
                val degreeMap = mutableMapOf<Long, Int>()
                members.forEach { wordId ->
                    degreeMap[wordId] = edgesByWordId[wordId]?.size ?: 0
                }
                val sortedMembers = members.sortedByDescending { degreeMap[it] ?: 0 }
                sortedMembers.chunked(maxPoolSize).forEach { chunk ->
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
                if (!retryable) return emptyList()
                if (attempt < MAX_EDGE_RETRIES - 1) {
                    delay(AiErrorUtils.retryDelay(e, attempt))
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
