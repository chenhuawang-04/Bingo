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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onProgress: (Int, Int, String?) -> Unit
    ): List<BuiltPoolWithWordIds> {
        val chunkSize = settingsDataStore.getPoolWindowSize()          // 每块候选词数
        val maxConcurrent = settingsDataStore.getPoolMaxConcurrent()   // 同一词内各块的最大并发
        val requestsPerMinute = settingsDataStore.getPoolRequestsPerMinute()
        val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
        val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
        Log.i("WordPoolRepo", "QUALITY_FIRST 配置: chunkSize=$chunkSize, maxConcurrent=$maxConcurrent, rpm=$requestsPerMinute")

        if (rebuildMode == RebuildMode.FULL) {
            wordEdgeDao.deleteByDictionary(dictionaryId)
        }

        val semaphore = Semaphore(maxConcurrent)
        val rateLimiter = EdgeRateLimiter(requestsPerMinute)

        // 一次性载入全部词（含完整释义），按 spelling 升序。各块直接复用这些对象，不再回查数据库。
        val allWords: List<WordDetails> = wordDao.getWordsByDictionaryOnce(dictionaryId).map { it.toDomain() }
        val totalWords = allWords.size
        Log.i("WordPoolRepo", "词典共 $totalWords 个词")
        if (totalWords <= 1) {
            onProgress(totalWords, totalWords, null)
            return emptyList()
        }

        // AI 调用统计（构建后据此判断是否“全部失败”）。在并发块中写入，故用原子类型。
        val successfulAiCalls = AtomicInteger(0)
        val failedAiCalls = AtomicInteger(0)
        val firstAiError = AtomicReference<String?>(null)

        // 已完整处理的词数：用于进度展示与断点续传。
        // 倒序处理时，已处理的是“末尾若干个词”，startIndex 即上次已处理的数量。
        val alreadyProcessed = if (startIndex > 0) startIndex.coerceIn(0, totalWords) else 0
        val processed = AtomicInteger(alreadyProcessed)

        // ============================================================
        // 外层循环：从后向前（倒序）。
        //   末词前驱最多 → 分块最多 → 立刻并发多次请求，可即时观察。
        //   index 0 无前驱，循环结束后统一计入进度。
        //   断点续传：跳过末尾已处理的 alreadyProcessed 个词，从 (totalWords-1-alreadyProcessed) 开始。
        // ============================================================
        val firstIndexToProcess = (totalWords - 1 - alreadyProcessed).coerceAtLeast(0)
        for (wordIndex in firstIndexToProcess downTo 1) {
            if (isCancelled()) {
                Log.d("WordPoolRepo", "构建被取消 @wordIndex=$wordIndex")
                break
            }
            awaitWhilePaused(isPaused, isCancelled)
            if (isCancelled()) break

            val currentWord = allWords[wordIndex]

            // 续传时，中断点这个词可能写入过不完整的边，先清除再重做。
            if (wordIndex == firstIndexToProcess && alreadyProcessed > 0) {
                wordEdgeDao.deleteEdgesForWord(dictionaryId, currentWord.id)
            }

            // 内层：前驱词 [0, wordIndex) 按 chunkSize 分块。
            // 例：301 个词处理末词时，前驱 300 个、chunkSize=50 → 6 块：[0,50) [50,100) … [250,300)
            val chunks = allWords.subList(0, wordIndex).chunked(chunkSize)
            val wordsDone = processed.get()
            val edgesForWord = AtomicInteger(0)
            val chunksDone = AtomicInteger(0)
            Log.d("WordPoolRepo", "词 '${currentWord.spelling}' (剩余#$wordIndex): 对比前 $wordIndex 个词，分 ${chunks.size} 块")

            // 该词开始：先报告一次（0 块完成），让详情页立即显示总块数。
            onProgress(wordsDone, totalWords, encodeProgress(currentWord.spelling, 0, chunks.size, 0))

            // 同一词的各块并发执行（并发度 maxConcurrent，整体频率 requestsPerMinute）；
            // 全部完成后才进入下一个词。
            val newEdges = coroutineScope {
                chunks.map { chunk ->
                    async {
                        semaphore.withPermit {
                            awaitWhilePaused(isPaused, isCancelled)
                            if (isCancelled()) return@withPermit emptyList()
                            rateLimiter.acquire()
                            if (isCancelled()) return@withPermit emptyList()

                            // chunk 本身就是完整的 WordDetails，直接构建 prompt，无需回查数据库。
                            val prompt = EdgePromptBuilder.buildEdgePrompt(currentWord, chunk)
                            val errorRef = arrayOfNulls<String>(1)
                            val parsed = callAiForEdges(prompt, currentWord, chunk, unwrapEnabled, repairEnabled, errorRef)

                            if (parsed.isNotEmpty()) {
                                successfulAiCalls.incrementAndGet()
                                edgesForWord.addAndGet(parsed.size)
                            } else {
                                failedAiCalls.incrementAndGet()
                                errorRef[0]?.let { firstAiError.compareAndSet(null, it) }
                            }

                            // 本块完成，更新进度（已完成块数单调递增）。
                            val done = chunksDone.incrementAndGet()
                            onProgress(wordsDone, totalWords, encodeProgress(currentWord.spelling, done, chunks.size, edgesForWord.get()))
                            Log.d("WordPoolRepo", "  块[$done/${chunks.size}] '${currentWord.spelling}' vs ${chunk.size}词 → ${parsed.size}条边")

                            parsed.mapNotNull { edge ->
                                val other = chunk.getOrNull(edge.index) ?: return@mapNotNull null
                                toEdgeEntity(currentWord, other, edge, dictionaryId)
                            }
                        }
                    }
                }.awaitAll().flatten()
            }

            if (newEdges.isNotEmpty()) {
                wordEdgeDao.insertEdges(newEdges)
            }

            // 该词处理完毕：完成数 +1，发出一次进度（块数清零，待下一个词重新填充）。
            val done = processed.incrementAndGet()
            onProgress(done, totalWords, encodeProgress(currentWord.spelling, 0, 0, newEdges.size))
        }

        // index 0 的词无前驱，处理结束后计入完成数，使进度抵达 100%。
        if (!isCancelled()) {
            processed.incrementAndGet()
            onProgress(processed.get().coerceAtMost(totalWords), totalWords, null)
        }

        // ============================================================
        // 构建完成，统计 AI 调用情况
        // ============================================================
        val totalAiAttempts = successfulAiCalls.get() + failedAiCalls.get()
        if (!isCancelled() && totalAiAttempts > 0 && successfulAiCalls.get() == 0) {
            val errorMsg = "所有 AI 调用均失败（共 $totalAiAttempts 次），未生成任何边。" +
                (firstAiError.get()?.let { "\n首个错误: $it" } ?: "")
            Log.e("WordPoolRepo", errorMsg)
            throw IllegalStateException(errorMsg)
        }
        if (totalAiAttempts > 0) {
            Log.i("WordPoolRepo", "AI 调用统计: 成功 ${successfulAiCalls.get()}/$totalAiAttempts, 失败 ${failedAiCalls.get()}")
        }

        // ============================================================
        // 审查低置信度边
        // ============================================================
        coroutineContext.ensureActive()
        val allEdgesForReview = wordEdgeDao.getAllEdgesFull(dictionaryId)

        val allWordIds = mutableSetOf<Long>()
        allEdgesForReview.forEach { edge ->
            allWordIds.add(edge.wordIdA)
            allWordIds.add(edge.wordIdB)
        }

        // 复用前面已载入的 allWords，无需再次查库。
        val reviewModified = edgeReviewer.reviewEdgesWithAi(allEdgesForReview, allWords, dictionaryId)

        coroutineContext.ensureActive()
        val allEdges = if (reviewModified) {
            wordEdgeDao.getAllEdgesFull(dictionaryId)
        } else {
            allEdgesForReview
        }
        return rebuildPoolsFromEdges(allEdges, allWordIds)
    }

    // ── QUALITY_FIRST 小工具 ──

    /** 进度消息编码：单词|已完成块数|总块数|本词已找到的边数。详情页据此解析展示。 */
    private fun encodeProgress(word: String, chunksDone: Int, chunkTotal: Int, edges: Int): String =
        "$word|$chunksDone|$chunkTotal|$edges"

    /** 暂停期间挂起等待（标志位由 BackgroundTaskManager 控制），取消时立即返回。 */
    private suspend fun awaitWhilePaused(isPaused: () -> Boolean, isCancelled: () -> Boolean) {
        while (isPaused() && !isCancelled()) {
            delay(300)
        }
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

    private suspend fun callAiForEdges(
        prompt: String,
        target: WordDetails,
        window: List<WordDetails>,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean,
        errorOut: Array<String?> = arrayOfNulls(1)
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
                errorOut[0] = e.message?.take(200) ?: e.javaClass.simpleName
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
                if (!retryable) {
                    errorOut[0] = e.message?.take(200) ?: e.javaClass.simpleName
                    return emptyList()
                }
                if (attempt < MAX_EDGE_RETRIES - 1) {
                    delay(AiErrorUtils.retryDelay(e, attempt))
                }
            }
        }
        Log.w("WordPoolRepo", "AI edge call failed after $MAX_EDGE_RETRIES attempts", lastException)
        errorOut[0] = lastException?.message?.take(200) ?: "AI 调用失败（已重试 $MAX_EDGE_RETRIES 次）"
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
        val maxPoolSize = WordPoolEngine.MAX_POOL_SIZE
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
