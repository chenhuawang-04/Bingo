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
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.pool.BuiltPool
import com.xty.englishhelper.domain.pool.PoolCandidate
import com.xty.englishhelper.domain.pool.WordPoolEngine
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.util.AiResponseUnwrapper
import com.xty.englishhelper.util.AiJsonRepairer
import com.xty.englishhelper.util.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private data class BuiltPoolWithWordIds(
    val focusWordId: Long?,
    val memberWordIds: List<Long>
)

/**
 * Simple token-bucket rate limiter.
 * @param maxPerMinute maximum requests per minute; 0 = unlimited
 */
private class RateLimiter(maxPerMinute: Int) {
    private val minIntervalMs = if (maxPerMinute > 0) 60_000L / maxPerMinute else 0L
    private var lastAcquireMs = 0L
    private val lock = Any()

    suspend fun acquire() {
        if (minIntervalMs <= 0) return
        val waitMs = synchronized(lock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastAcquireMs
            if (elapsed >= minIntervalMs) {
                lastAcquireMs = now
                0L
            } else {
                val nextAvailable = lastAcquireMs + minIntervalMs
                val delay = nextAvailable - now
                lastAcquireMs = nextAvailable
                delay
            }
        }
        if (waitMs > 0) {
            kotlinx.coroutines.delay(waitMs)
        }
    }
}

@Singleton
class WordPoolRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val wordPoolDao: WordPoolDao,
    private val wordEdgeDao: WordEdgeDao,
    private val wordDao: WordDao,
    private val aiApiClientProvider: AiApiClientProvider,
    private val settingsDataStore: SettingsDataStore
) : WordPoolRepository {

    private val engine = WordPoolEngine()

    companion object {
        private val EDGE_RESPONSE_PATTERN_I_T = Regex("""\{\s*"i"\s*:\s*(\d+)\s*,\s*"t"\s*:\s*"(\w+)"\s*\}""")
        private val EDGE_RESPONSE_PATTERN_T_I = Regex("""\{\s*"t"\s*:\s*"(\w+)"\s*,\s*"i"\s*:\s*(\d+)\s*\}""")
        private val INT_ARRAY_PATTERN = Regex("""\[\s*(\d+(?:\s*,\s*\d+)*)\s*\]""")
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
                        updatedAt = now
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

    override suspend fun getWordEdgeAdjacency(dictionaryId: Long): Map<Long, Map<Long, Set<EdgeType>>> {
        val edges = wordEdgeDao.getAllEdges(dictionaryId)
        val adj = mutableMapOf<Long, MutableMap<Long, MutableSet<EdgeType>>>()
        edges.forEach { e ->
            val type = EdgeType.fromDbValue(e.edgeType)
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
        // Get associations filtered by same dictionary
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

        // Optional AI batch completion for orphaned words
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

        // For FULL mode, clear existing edges upfront
        if (rebuildMode == RebuildMode.FULL) {
            wordEdgeDao.deleteByDictionary(dictionaryId)
        }

        // Determine resume index: startIndex if >= 0, otherwise start from end
        val resumeIndex = if (startIndex >= 0) startIndex else domains.size - 1

        // Cache settings for the loop
        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()

        // Concurrency controls
        val semaphore = Semaphore(maxConcurrent)
        val rateLimiter = RateLimiter(requestsPerMinute)

        // Total AI calls: sum of ceil(i/windowSize) for i in 1..resumeIndex
        val totalAiCalls = (1..resumeIndex).sumOf { i ->
            (i + windowSize - 1) / windowSize
        }
        val completedCalls = java.util.concurrent.atomic.AtomicInteger(0)

        // For each word, slide window across ALL previous words
        for (i in resumeIndex.downTo(0)) {
            // Cooperative cancel check
            if (isCancelled()) {
                Log.d("WordPoolRepo", "QualityFirst build cancelled at index $i")
                break
            }

            val currentWord = domains[i]
            if (i == 0) continue  // first word has nothing to compare

            // Build window tasks for this word
            data class WindowTask(val windowStart: Int, val windowEnd: Int)
            val windowTasks = mutableListOf<WindowTask>()
            var windowEnd = i
            while (windowEnd > 0) {
                val windowStart = maxOf(0, windowEnd - windowSize)
                windowTasks.add(WindowTask(windowStart, windowEnd))
                windowEnd = windowStart
            }

            // Process windows concurrently with semaphore + rate limiter
            val windowResults = windowTasks.map { task ->
                coroutineScope {
                    async {
                        semaphore.withPermit {
                            rateLimiter.acquire()
                            if (isCancelled()) return@withPermit emptyList()

                            val window = domains.subList(task.windowStart, task.windowEnd)
                            val prompt = buildEdgePrompt(currentWord, window)
                            val aiResult = callAiForEdges(prompt, window.size, unwrapEnabled, repairEnabled)

                            val count = completedCalls.incrementAndGet()
                            onProgress(count, totalAiCalls)

                            aiResult.mapNotNull { (j, typeStr) ->
                                if (j !in window.indices) return@mapNotNull null
                                val otherWord = window[j]
                                val edgeType = parseEdgeType(typeStr)
                                val (idA, idB) = if (currentWord.id < otherWord.id) {
                                    currentWord.id to otherWord.id
                                } else {
                                    otherWord.id to currentWord.id
                                }
                                WordEdgeEntity(
                                    wordIdA = idA,
                                    wordIdB = idB,
                                    edgeType = edgeType.dbValue,
                                    dictionaryId = dictionaryId
                                )
                            }
                        }
                    }
                }
            }.awaitAll()

            // Collect and persist edges for this word
            val newEdges = windowResults.flatten()
            if (newEdges.isNotEmpty()) {
                wordEdgeDao.insertEdges(newEdges)
            }
        }

        // Build pools from ALL edges in DB (including previously persisted ones)
        coroutineContext.ensureActive()
        val allEdgeProjections = wordEdgeDao.getAllEdges(dictionaryId)
        val allEdges = allEdgeProjections.map {
            WordEdgeEntity(wordIdA = it.wordIdA, wordIdB = it.wordIdB, edgeType = it.edgeType, dictionaryId = dictionaryId)
        }
        val wordIds = domains.map { it.id }.toSet()
        return rebuildPoolsFromEdges(allEdges, wordIds)
    }

    private fun buildEdgePrompt(
        target: com.xty.englishhelper.domain.model.WordDetails,
        window: List<com.xty.englishhelper.domain.model.WordDetails>
    ): String {
        return buildString {
            appendLine("你是词汇学习助手。下面是1个目标词和${window.size}个候选词（index. spelling: 首条中文释义）。")
            appendLine("目标词：${target.spelling}（${target.meanings.firstOrNull()?.definition ?: ""}）")
            appendLine("请找出目标词与候选词之间的关系，返回JSON数组，每个元素包含候选词序号和关系类型。")
            appendLine("关系类型：S=形近(拼写相似), M=意近(语义相似), R=词根相似, P=发音相似")
            appendLine("返回格式：[{\"i\":1,\"t\":\"S\"},{\"i\":5,\"t\":\"M\"}]，若无相关词返回 []。")
            appendLine(Constants.JSON_STRICT_RULES)
            appendLine()
            window.forEachIndexed { idx, w ->
                val firstMeaning = w.meanings.firstOrNull()?.definition ?: ""
                appendLine("$idx. ${w.spelling}: $firstMeaning")
            }
        }
    }

    private suspend fun callAiForEdges(
        prompt: String,
        windowSize: Int,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean,
        retryCount: Int = 0
    ): List<Pair<Int, String>> {
        return try {
            val response = callAi(prompt)
            val normalized = normalizeResponse(response, unwrapEnabled, repairEnabled)
            parseEdgeResponse(normalized, windowSize)
        } catch (e: Exception) {
            if (retryCount < 1) {
                Log.w("WordPoolRepo", "AI edge call failed, retrying", e)
                callAiForEdges(prompt, windowSize, unwrapEnabled, repairEnabled, retryCount + 1)
            } else {
                Log.w("WordPoolRepo", "AI edge call failed after retry", e)
                emptyList()
            }
        }
    }

    private fun parseEdgeResponse(text: String, maxValue: Int): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        // Try {"i":N,"t":"X"} pattern first
        EDGE_RESPONSE_PATTERN_I_T.findAll(text).forEach { match ->
            val index = match.groupValues[1].toIntOrNull()
            val type = match.groupValues[2]
            if (index != null && index in 0 until maxValue) {
                result.add(index to type)
            }
        }
        if (result.isNotEmpty()) return result
        // Fallback: {"t":"X","i":N} pattern
        EDGE_RESPONSE_PATTERN_T_I.findAll(text).forEach { match ->
            val type = match.groupValues[1]
            val index = match.groupValues[2].toIntOrNull()
            if (index != null && index in 0 until maxValue) {
                result.add(index to type)
            }
        }
        return result
    }

    private fun parseEdgeType(typeStr: String): EdgeType {
        return when (typeStr.uppercase()) {
            "S" -> EdgeType.SPELLING
            "M" -> EdgeType.MEANING
            "R" -> EdgeType.ROOT
            "P" -> EdgeType.PRONUNCIATION
            else -> EdgeType.AI_GENERAL
        }
    }

    /**
     * Union-Find based pool extraction from edge graph.
     * Splits oversized components (>MAX_POOL_SIZE).
     */
    private fun rebuildPoolsFromEdges(
        edges: List<WordEdgeEntity>,
        wordIds: Set<Long>
    ): List<BuiltPoolWithWordIds> {
        if (edges.isEmpty()) return emptyList()

        // Union-Find: only include words that appear in edges
        val parent = mutableMapOf<Long, Long>()
        edges.forEach { e ->
            parent.putIfAbsent(e.wordIdA, e.wordIdA)
            parent.putIfAbsent(e.wordIdB, e.wordIdB)
        }
        fun find(x: Long): Long {
            var r = x
            while (parent[r] != r) r = parent[r]!!
            var c = x
            while (c != r) {
                val next = parent[c]!!
                parent[c] = r
                c = next
            }
            return r
        }
        fun union(a: Long, b: Long) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        edges.forEach { union(it.wordIdA, it.wordIdB) }

        // Group by root
        val components = mutableMapOf<Long, MutableList<Long>>()
        wordIds.forEach { id ->
            val root = find(id)
            if (root !in components) components[root] = mutableListOf()
            components[root]!!.add(id)
        }

        // Build pools from components with >=2 members, split large ones
        val maxPoolSize = 15
        val result = mutableListOf<BuiltPoolWithWordIds>()
        components.values.forEach { members ->
            if (members.size < 2) return@forEach
            if (members.size <= maxPoolSize) {
                result.add(BuiltPoolWithWordIds(focusWordId = null, memberWordIds = members))
            } else {
                // Split into chunks
                members.chunked(maxPoolSize).forEach { chunk ->
                    if (chunk.size >= 2) {
                        result.add(BuiltPoolWithWordIds(focusWordId = null, memberWordIds = chunk))
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

        // Process in batches of 40
        val allAiGroups = mutableListOf<List<Int>>()
        orphans.chunked(40).forEach { batch ->
            coroutineContext.ensureActive()
            val prompt = buildString {
                appendLine("以下单词来自同一词库。找出其中形近（拼写相似、易混淆）或意近（语义相关）的分组，每组至少2词。")
                appendLine("返回严格JSON: [[0,3],[1,5,12]]（数字为列表序号，不含无关词）。")
                appendLine(Constants.JSON_STRICT_RULES)
                appendLine()
                batch.forEachIndexed { i, c ->
                    val firstMeaning = c.meanings.firstOrNull() ?: ""
                    appendLine("$i. ${c.spelling}: $firstMeaning")
                }
            }

            val groups = callAiForGroups(prompt, batch.size)
            // Map batch-local indices back to global indices
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

    private suspend fun callAiForGroups(prompt: String, listSize: Int, retryCount: Int = 0): List<List<Int>> {
        return try {
            val response = callAi(prompt)
            val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
            val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
            val normalized = normalizeResponse(response, unwrapEnabled, repairEnabled)
            parseJsonIntArrayOfArrays(normalized, listSize)
        } catch (e: Exception) {
            if (retryCount < 1) {
                Log.w("WordPoolRepo", "AI batch call failed, retrying", e)
                callAiForGroups(prompt, listSize, retryCount + 1)
            } else {
                Log.w("WordPoolRepo", "AI batch call failed after retry", e)
                emptyList()
            }
        }
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
            maxTokens = 1024
        )
    }

    private fun parseJsonIntArrayOfArrays(text: String, maxValue: Int): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        // Match each inner array
        INT_ARRAY_PATTERN.findAll(text).forEach { match ->
            val indices = match.groupValues[1]
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 0 until maxValue }
            if (indices.size >= 2) {
                result.add(indices)
            }
        }
        return result
    }

    private fun normalizeResponse(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): String {
        val cleaned = stripCodeFence(text)
        val unwrapped = if (unwrapEnabled) {
            val candidate = extractFirstJsonObject(cleaned) ?: cleaned.trim()
            AiResponseUnwrapper.unwrapJsonEnvelope(candidate) ?: cleaned
        } else {
            cleaned
        }
        val stripped = stripCodeFence(unwrapped)
        return if (repairEnabled) AiJsonRepairer.repair(stripped) else stripped
    }

    private fun stripCodeFence(text: String): String {
        return text
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .replace("'''json", "", ignoreCase = true)
            .replace("'''", "")
            .trim()
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            when (ch) {
                '\\' -> escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
