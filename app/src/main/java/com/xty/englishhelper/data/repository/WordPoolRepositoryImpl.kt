package com.xty.englishhelper.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.xty.englishhelper.data.local.AppDatabase
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.local.entity.WordEdgeExcludedEntity
import com.xty.englishhelper.data.local.entity.WordPoolEntity
import com.xty.englishhelper.data.local.entity.WordPoolMemberEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.EdgeCluster
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordDetails
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

private data class ParsedEdge(
    val index: Int,
    val edgeType: EdgeType,
    val status: String,
    val learningValue: Int,
    val relationStrength: Int,
    val confidence: Double,
    val reason: String?,
    val warningNote: String?,
    val evidenceSource: String? = null,
    val register: String? = null,
    val exampleSentence: String? = null,
    val difficultyCefr: String? = null
)

private class RetryableEdgeException(message: String, cause: Throwable? = null) : Exception(message, cause)
private class NonRetryableEdgeException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
        private val INT_ARRAY_PATTERN = Regex("""\[\s*(\d+(?:\s*,\s*\d+)*)\s*\]""")
        private val VALID_STATUSES = setOf("core", "support", "warning", "optional")
        private const val MAX_EDGE_RETRIES = 4
        private val ENTRY_TYPE_PATTERN = Regex(""""entry_type"\s*:\s*"(word|root|phrase)"""")
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

    override suspend fun getWordEdgeAdjacency(dictionaryId: Long): Map<Long, Map<Long, Set<EdgeType>>> {
        val edges = wordEdgeDao.getAllEdges(dictionaryId)
        val adj = mutableMapOf<Long, MutableMap<Long, MutableSet<EdgeType>>>()
        edges.forEach { e ->
            val type = EdgeType.fromDbValue(e.edgeType) ?: return@forEach
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
                }
            }.awaitAll()

            // Collect and persist edges for this word
            val newEdges = windowResults.flatten()
            if (newEdges.isNotEmpty()) {
                wordEdgeDao.insertEdges(newEdges)
            }
        }

        // Review low-confidence edges with REVIEWER model
        coroutineContext.ensureActive()
        val allEdgesForReview = wordEdgeDao.getAllEdgesFull(dictionaryId)
        reviewEdgesWithAi(allEdgesForReview, domains, dictionaryId)

        // Build pools from ALL edges in DB (including previously persisted ones)
        coroutineContext.ensureActive()
        val allEdges = wordEdgeDao.getAllEdgesFull(dictionaryId)
        val wordIds = domains.map { it.id }.toSet()
        return rebuildPoolsFromEdges(allEdges, wordIds)
    }

    /**
     * Multi-lane edge prompt: builds a sense-first, POS-aware prompt that
     * instructs the AI to evaluate candidates across 5 relationship clusters.
     * Each lane has its own inclusion criteria and hard thresholds.
     */
    private fun buildEdgePrompt(
        target: WordDetails,
        window: List<WordDetails>
    ): String {
        val targetPOS = target.meanings.map { it.pos }.distinct().joinToString("/")
        val targetSenses = target.meanings.take(4).mapIndexed { idx, m ->
            "${idx + 1}. [${m.pos}] ${m.definition}"
        }.joinToString("\n")

        return buildString {
            appendLine("你是英语词池整理 Agent。你的任务是围绕目标词构建对学习者真正有价值的高质量词池。")
            appendLine()
            appendLine("目标词：${target.spelling}")
            appendLine("词性：$targetPOS")
            appendLine("义项：")
            appendLine(targetSenses)
            appendLine()
            appendLine("候选词（index. spelling [词性]: 释义）：")
            window.forEachIndexed { idx, w ->
                val m = w.meanings.firstOrNull()
                val posTag = m?.pos ?: ""
                val def = m?.definition ?: ""
                appendLine("$idx. ${w.spelling} [$posTag]: $def")
            }
            appendLine()
            appendLine("【核心原则】")
            appendLine("- 必须先区分词性，再区分义项。")
            appendLine("- 词池以\"义项\"为单位，不得把不同义项混在一起。")
            appendLine("- 语义类边默认只收同词性；跨词性归入 family lane。")
            appendLine("- \"语言学相关\"不等于\"值得加入\"；对每个候选都判断学习价值。")
            appendLine("- 不得把罕见、古旧、不自然、牵强附会的词当作高优先级。")
            appendLine("- 不得把同根词、同前缀词自动视为应纳入。")
            appendLine()
            appendLine("【5条召回通道】对每个候选词，检查以下5个通道：")
            appendLine("1. SEMANTIC — 语义概念关系（同义/近义/反义/上下位/语义重叠）")
            appendLine("   纳入条件：必须绑定到目标词的某个具体义项，且同词性。")
            appendLine("2. FORM — 形式与语音关系（拼写形近/同音/发音相似/最小对立体）")
            appendLine("   纳入条件：混淆风险高或发音训练价值高。")
            appendLine("3. FAMILY — 词族与构词关系（屈折/派生/同根）")
            appendLine("   纳入条件：构词透明度高，学习收益明确。")
            appendLine("4. USAGE — 语用与搭配关系（搭配/短语/句型模式）")
            appendLine("   纳入条件：有真实用例或高频搭配证据。")
            appendLine("5. LEARNING — 学习与错误关系（易混淆/常见误用/对比学习对）")
            appendLine("   纳入条件：有 learner evidence 或显著教学收益。")
            appendLine()
            appendLine("【硬门槛】以下任一条不满足则必须排除：")
            appendLine("- sense_match: 关系必须绑定到目标词的某个义项（form/family/learning 类除外）")
            appendLine("- pos_rule: 语义类必须同词性；跨词性只能进 family/learning lane")
            appendLine("- evidence_rule: 至少有一个可信理由支撑纳入")
            appendLine("- naturalness_rule: 候选词必须是自然常用词，不收罕见/古旧词")
            appendLine("- incremental_value_rule: 不能是已有候选的近重复或低收益远亲")
            appendLine()
            appendLine("【允许的 edge_type 值（16种）】")
            appendLine("SEMANTIC_SYNONYM / SEMANTIC_ANTONYM / SEMANTIC_OVERLAP / SEMANTIC_HYPERNYM / SEMANTIC_HYPONYM")
            appendLine("FORM_SPELLING / FORM_HOMOPHONE / FORM_PRONUNCIATION / FORM_MINIMAL_PAIR")
            appendLine("FAMILY_INFLECTION / FAMILY_DERIVATION / FAMILY_SAME_ROOT")
            appendLine("USAGE_COLLOCATION / USAGE_PHRASE / USAGE_PATTERN")
            appendLine("LEARNING_CONFUSABLE / LEARNING_MISUSE_PAIR")
            appendLine()
            appendLine("【status 取值】")
            appendLine("core — 核心关联，必须掌握")
            appendLine("support — 辅助关联，有助于理解")
            appendLine("warning — 存在混淆风险，需特别注意")
            appendLine("optional — 可选了解")
            appendLine()
            appendLine("【返回JSON格式】")
            appendLine("""[{"i":1,"edge_type":"SEMANTIC_SYNONYM","relation_strength":4,"learning_value":5,"status":"core","register":"neutral","reason":"两词都表示'大的'，高频同义替换","warning_note":"","confidence":0.9,"evidence_source":"高频同义替换","example_sentence":"The big house is large.","difficulty_cefr":"A1"}]""")
            appendLine()
            appendLine("字段说明：")
            appendLine("- i: 候选词序号（从0开始）")
            appendLine("- edge_type: 上述16种之一")
            appendLine("- relation_strength: 1-5，关系紧密程度")
            appendLine("- learning_value: 1-5，学习价值")
            appendLine("- status: core/support/warning/optional")
            appendLine("- register: 语域（neutral/formal/informal/academic/technical，无法判断填 neutral）")
            appendLine("- reason: 一句话说明为什么有关系（中文）")
            appendLine("- warning_note: 如有混淆风险给出警示（中文，无则空字符串）")
            appendLine("- confidence: 0.0-1.0，你对这个判断的自信程度")
            appendLine("- evidence_source: 证据来源简述（如\"拼写相似+高频词\"、\"常见搭配\"、\"词典标注易混\"，不超过10字）")
            appendLine("- example_sentence: 展示该关联的例句（英文，无则空字符串）")
            appendLine("- difficulty_cefr: 该边的难度等级（A1/A2/B1/B2/C1/C2，无法判断留空）")
            appendLine()
            appendLine("【自检要求】")
            appendLine("输出结果前请自查：")
            appendLine("1. 是否有边只因拼写/词根相近就纳入，但实际学习价值很低？")
            appendLine("2. 是否有边的关系类型标注错误？（如把易混淆词标为同义词）")
            appendLine("3. status=core 的边是否确实应为核心？")
            appendLine("4. 是否有重复或近重复的边？")
            appendLine("5. 是否混义项或混词性？")
            appendLine("6. 是否有低频噪声或不自然搭配？")
            appendLine("如发现问题请自行修正后再输出。")
            appendLine()
            appendLine("只返回确实存在关联的词对。无关联则返回 []。")
            appendLine(Constants.JSON_STRICT_RULES)
        }
    }

    private suspend fun callAiForEdges(
        prompt: String,
        target: WordDetails,
        window: List<WordDetails>,
        unwrapEnabled: Boolean,
        repairEnabled: Boolean
    ): List<ParsedEdge> {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_EDGE_RETRIES) {
            try {
                val response = callAi(prompt)
                val normalized = normalizeResponse(response, unwrapEnabled, repairEnabled)
                val parsed = parseAndValidateEdgeResponse(normalized, window.size)
                return applyHardThresholds(parsed, target, window)
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
                    delay(1000L * (attempt + 1))
                }
            }
        }
        Log.w("WordPoolRepo", "AI edge call failed after $MAX_EDGE_RETRIES attempts", lastException)
        return emptyList()
    }

    private fun isRetryableError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized") || msg.contains("invalid api key")) {
            return false
        }
        if (msg.contains("429") || msg.contains("rate limit")) {
            return false
        }
        return true
    }

    private fun parseAndValidateEdgeResponse(text: String, maxValue: Int): List<ParsedEdge> {
        val arrayStart = text.indexOf('[')
        val arrayEnd = text.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd < 0 || arrayEnd <= arrayStart) {
            throw RetryableEdgeException("No JSON array found in response")
        }
        val arrayText = text.substring(arrayStart, arrayEnd + 1)

        // Empty array is valid
        val content = arrayText.removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return emptyList()

        val results = mutableListOf<ParsedEdge>()
        val objPattern = Regex("""\{[^{}]+\}""")
        objPattern.findAll(arrayText).forEach { match ->
            val obj = match.value
            try {
                val index = extractJsonInt(obj, "i")
                    ?: throw RetryableEdgeException("Missing 'i' field")
                if (index !in 0 until maxValue) return@forEach

                val edgeTypeStr = extractJsonString(obj, "edge_type")
                    ?: throw RetryableEdgeException("Missing edge_type field")
                val edgeType = EdgeType.entries.firstOrNull { it.dbValue == edgeTypeStr }
                    ?: throw RetryableEdgeException("Unknown edge_type: $edgeTypeStr")

                val relStrength = extractJsonInt(obj, "relation_strength")?.coerceIn(1, 5) ?: 3
                val learnValue = extractJsonInt(obj, "learning_value")?.coerceIn(1, 5) ?: 3
                val status = extractJsonString(obj, "status")?.let { s ->
                    VALID_STATUSES.firstOrNull { it == s }
                } ?: "core"
                val confidence = extractJsonDouble(obj, "confidence")?.coerceIn(0.0, 1.0) ?: 0.5
                val reason = extractJsonString(obj, "reason")
                val warningNote = extractJsonString(obj, "warning_note")
                val evidenceSource = extractJsonString(obj, "evidence_source")
                val register = extractJsonString(obj, "register")
                val exampleSentence = extractJsonString(obj, "example_sentence")
                val difficultyCefr = extractJsonString(obj, "difficulty_cefr")

                results.add(ParsedEdge(index, edgeType, status, learnValue, relStrength, confidence, reason, warningNote, evidenceSource, register, exampleSentence, difficultyCefr))
            } catch (e: RetryableEdgeException) {
                throw e
            } catch (e: Exception) {
                Log.w("WordPoolRepo", "Failed to parse edge object: $obj", e)
            }
        }
        return results
    }

    private fun extractJsonInt(json: String, key: String): Int? {
        val match = Regex(""""$key"\s*:\s*(-?\d+)""").find(json) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun extractJsonString(json: String, key: String): String? {
        val match = Regex(""""$key"\s*:\s*"([^"]*?)"""").find(json) ?: return null
        return match.groupValues[1]
    }

    private fun extractJsonDouble(json: String, key: String): Double? {
        val match = Regex(""""$key"\s*:\s*([\d.]+)""").find(json) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    // ── Reviewer ──

    private suspend fun reviewEdgesWithAi(
        edges: List<WordEdgeEntity>,
        domains: List<WordDetails>,
        dictionaryId: Long
    ) {
        val needsReview = edges.filter { it.confidence < 0.6 || it.status == "warning" }
        if (needsReview.isEmpty()) return

        val wordMap = domains.associateBy { it.id }

        needsReview.chunked(20).forEach { batch ->
            coroutineContext.ensureActive()
            val prompt = buildReviewPrompt(batch, wordMap)
            try {
                val response = callAiForReviewer(prompt)
                val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
                val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
                val normalized = normalizeResponse(response, unwrapEnabled, repairEnabled)
                parseAndApplyReview(normalized, batch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WordPoolRepo", "Edge review batch failed, keeping original edges", e)
            }
        }
    }

    private fun buildReviewPrompt(
        edges: List<WordEdgeEntity>,
        wordMap: Map<Long, WordDetails>
    ): String {
        return buildString {
            appendLine("你是英语词池质量审核员。请逐条审核以下 ${edges.size} 条边：")
            appendLine()
            edges.forEachIndexed { idx, edge ->
                val wordA = wordMap[edge.wordIdA]?.spelling ?: "?"
                val wordB = wordMap[edge.wordIdB]?.spelling ?: "?"
                appendLine("$idx. $wordA ↔ $wordB | 类型:${edge.edgeType} | 状态:${edge.status} | 置信度:${edge.confidence} | 语域:${edge.register ?: "neutral"} | 难度:${edge.difficultyCefr ?: "未知"} | 理由:${edge.reason ?: "无"}")
            }
            appendLine()
            appendLine("审核标准：")
            appendLine("1. 关系类型(edge_type)是否准确？（如易混淆词不应标为同义词）")
            appendLine("2. status 是否合理？（core=核心/support=辅助/warning=易混/optional=可选）")
            appendLine("3. confidence 是否恰当？（低置信度边应降级或移除）")
            appendLine("4. 是否混入了仅\"相关\"但学习价值低的词？（如仅词根相关但现代意义差异大）")
            appendLine("5. 是否混入了罕见、古旧或证据薄弱的边？")
            appendLine("6. 是否遗漏了高价值的易混词或核心搭配？")
            appendLine("7. reason 是否充分解释了纳入原因？")
            appendLine("8. 是否存在重复或近重复的边？")
            appendLine("9. 难度与语域是否匹配目标学习者？（如低频学术词不应标为A1核心）")
            appendLine("10. excluded_candidates 是否合理？是否有本应排除但漏掉的候选？")
            appendLine()
            appendLine("返回JSON数组，每个元素：")
            appendLine("""[{"i":0,"verdict":"keep","new_status":"core","new_confidence":0.8,"note":"调整原因"}]""")
            appendLine("verdict: keep=保留原样 / adjust=调整status或confidence / remove=移除此边")
            appendLine("adjust 时必须提供 new_status 和 new_confidence；keep/remove 时可省略。")
            appendLine("note: 简要说明审核意见（中文）。")
            appendLine(Constants.JSON_STRICT_RULES)
        }
    }

    private suspend fun callAiForReviewer(prompt: String): String {
        val config = settingsDataStore.getAiConfig(AiSettingsScope.REVIEWER)
        val client = aiApiClientProvider.getClient(config.provider)
        return client.sendMessage(
            url = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = 2048
        )
    }

    private suspend fun parseAndApplyReview(text: String, batch: List<WordEdgeEntity>) {
        val arrayStart = text.indexOf('[')
        val arrayEnd = text.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd < 0) return
        val arrayText = text.substring(arrayStart, arrayEnd + 1)

        val objPattern = Regex("""\{[^{}]+\}""")
        objPattern.findAll(arrayText).forEach { match ->
            val obj = match.value
            try {
                val idx = extractJsonInt(obj, "i") ?: return@forEach
                if (idx !in batch.indices) return@forEach
                val edge = batch[idx]

                val verdict = extractJsonString(obj, "verdict") ?: return@forEach
                when (verdict) {
                    "remove" -> {
                        wordEdgeDao.deleteEdgeById(edge.id)
                        wordEdgeDao.insertExcluded(listOf(
                            WordEdgeExcludedEntity(
                                wordIdA = edge.wordIdA,
                                wordIdB = edge.wordIdB,
                                dictionaryId = edge.dictionaryId,
                                edgeType = edge.edgeType,
                                reason = extractJsonString(obj, "note") ?: "审核移除"
                            )
                        ))
                    }
                    "adjust" -> {
                        val newStatus = extractJsonString(obj, "new_status")?.let { s ->
                            VALID_STATUSES.firstOrNull { it == s }
                        } ?: edge.status
                        val newConfidence = extractJsonDouble(obj, "new_confidence")?.coerceIn(0.0, 1.0) ?: edge.confidence
                        wordEdgeDao.updateEdgeStatus(edge.id, newStatus, newConfidence)
                    }
                    // "keep" → no-op
                }
            } catch (e: Exception) {
                Log.w("WordPoolRepo", "Failed to parse review item: $obj", e)
            }
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
        // Filter: only use edges with confidence >= 0.3 and status != "optional"
        val significantEdges = edges.filter {
            it.confidence >= 0.3 && it.status != "optional"
        }
        if (significantEdges.isEmpty()) return emptyList()

        // Union-Find: only include words that appear in edges
        val parent = mutableMapOf<Long, Long>()
        significantEdges.forEach { e ->
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

        significantEdges.forEach { union(it.wordIdA, it.wordIdB) }

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
        // Build wordId→edges map for quality scoring
        val edgesByWordId = mutableMapOf<Long, MutableList<WordEdgeEntity>>()
        significantEdges.forEach { e ->
            edgesByWordId.getOrPut(e.wordIdA) { mutableListOf() }.add(e)
            edgesByWordId.getOrPut(e.wordIdB) { mutableListOf() }.add(e)
        }

        components.values.forEach { members ->
            if (members.size < 2) return@forEach
            if (members.size <= maxPoolSize) {
                val score = computePoolQualityScore(members, edgesByWordId)
                result.add(BuiltPoolWithWordIds(focusWordId = null, memberWordIds = members, qualityScore = score))
            } else {
                // Split into chunks
                members.chunked(maxPoolSize).forEach { chunk ->
                    if (chunk.size >= 2) {
                        val score = computePoolQualityScore(chunk, edgesByWordId)
                        result.add(BuiltPoolWithWordIds(focusWordId = null, memberWordIds = chunk, qualityScore = score))
                    }
                }
            }
        }
        return result
    }

    /**
     * Hard threshold filter: apply 5 gates to each parsed edge.
     * Returns only edges that pass all applicable gates.
     *
     * Gates:
     * 1. sense_match — semantic edges must have reason binding to a specific sense
     * 2. pos_rule — SEMANTIC cluster edges should be same POS (heuristic: check reason for POS mismatch)
     * 3. evidence_rule — must have non-blank reason
     * 4. naturalness_rule — confidence >= 0.4 (AI is fairly confident this is a natural relation)
     * 5. incremental_value_rule — no near-duplicate edges for same word pair + same cluster
     */
    private fun applyHardThresholds(
        edges: List<ParsedEdge>,
        target: WordDetails,
        window: List<WordDetails>
    ): List<ParsedEdge> {
        val targetPOS = target.meanings.map { it.pos }.toSet()
        val seen = mutableSetOf<Pair<Int, EdgeCluster>>()
        return edges.filter { edge ->
            // Gate 3: evidence_rule — must have reason
            if (edge.reason.isNullOrBlank()) return@filter false

            // Gate 4: naturalness_rule — minimum confidence
            if (edge.confidence < 0.4) return@filter false

            // Gate 5: incremental_value_rule — deduplicate same index + same cluster
            val key = edge.index to edge.edgeType.cluster
            if (!seen.add(key)) return@filter false

            // Gate 2: pos_rule — for SEMANTIC cluster, check if POS differs
            if (edge.edgeType.cluster == EdgeCluster.SEMANTIC) {
                val otherWord = window.getOrNull(edge.index)
                if (otherWord != null) {
                    val otherPOS = otherWord.meanings.map { it.pos }.toSet()
                    // If no POS overlap at all, this is likely a cross-POS relation — reject
                    if (targetPOS.isNotEmpty() && otherPOS.isNotEmpty() && targetPOS.intersect(otherPOS).isEmpty()) {
                        // Allow if status is warning (learning/confusable pair)
                        if (edge.status != "warning") {
                            return@filter false
                        }
                    }
                }
            }

            true
        }
    }

    /**
     * Compute pool quality score (max 50).
     * 10 dimensions per research report: relevance, accuracy, learningValue,
     * explainability, coverage, dedup, noiseControl, difficultyFit, registerFit, evidence.
     * Thresholds: 42-50 可上线; 35-41 需审查; 34 以下低质量.
     */
    private fun computePoolQualityScore(
        memberWordIds: List<Long>,
        edgesByWordId: Map<Long, List<WordEdgeEntity>>
    ): Int {
        val memberIdSet = memberWordIds.toSet()
        // Collect edges within this pool
        val poolEdges = mutableSetOf<WordEdgeEntity>()
        memberWordIds.forEach { wid ->
            edgesByWordId[wid]?.forEach { e ->
                if (e.wordIdA in memberIdSet && e.wordIdB in memberIdSet) {
                    poolEdges.add(e)
                }
            }
        }
        if (poolEdges.isEmpty()) return 0

        val edgeList = poolEdges.toList()
        val size = edgeList.size.toDouble()

        // 1. Relevance (0-5): average confidence mapped to 0-5
        val avgConfidence = edgeList.map { it.confidence }.average()
        val relevance = (avgConfidence * 5).toInt().coerceIn(0, 5)

        // 2. Accuracy (0-5): proportion of edges with status core/support (not warning/optional)
        val accurateCount = edgeList.count { it.status == "core" || it.status == "support" }
        val accuracy = (accurateCount / size * 5).toInt().coerceIn(0, 5)

        // 3. Learning value (0-5): average learning_value
        val avgLearningValue = edgeList.map { it.learningValue }.average()
        val learningScore = avgLearningValue.toInt().coerceIn(0, 5)

        // 4. Explainability (0-5): proportion with non-blank reason
        val reasonRatio = edgeList.count { !it.reason.isNullOrBlank() }.toDouble() / size
        val explainability = (reasonRatio * 5).toInt().coerceIn(0, 5)

        // 5. Coverage / diversity (0-5): number of distinct EdgeClusters involved
        val clusterCount = edgeList.mapNotNull { e ->
            EdgeType.fromDbValue(e.edgeType)?.cluster
        }.distinct().size
        val coverage = clusterCount.coerceIn(0, 5)

        // 6. Dedup (0-5): fewer near-duplicate edges = better (unique word pairs ratio)
        val uniquePairs = edgeList.map { minOf(it.wordIdA, it.wordIdB) to maxOf(it.wordIdA, it.wordIdB) }.distinct().size
        val dedup = if (edgeList.isEmpty()) 0 else (uniquePairs.toDouble() / edgeList.size * 5).toInt().coerceIn(0, 5)

        // 7. Noise control (0-5): fewer optional edges = better
        val optionalRatio = edgeList.count { it.status == "optional" }.toDouble() / size
        val noiseControl = ((1.0 - optionalRatio) * 5).toInt().coerceIn(0, 5)

        // 8. Difficulty fit (0-5): average confidence as proxy (high confidence = well-understood = good fit)
        val difficultyFit = (avgConfidence * 5).toInt().coerceIn(0, 5)

        // 9. Register fit (0-5): proportion of edges with reason indicating register awareness
        val registerAware = edgeList.count {
            val r = it.reason?.lowercase() ?: ""
            r.contains("语域") || r.contains("正式") || r.contains("口语") || r.contains("学术") || r.contains("register")
        }
        val registerFit = if (edgeList.isEmpty()) 0
        else (registerAware.toDouble() / size * 5).toInt().coerceIn(0, 5).coerceAtLeast(3) // baseline 3 if edges exist

        // 10. Evidence (0-5): proportion with evidence_source
        val evidenceRatio = edgeList.count { !it.evidenceSource.isNullOrBlank() }.toDouble() / size
        val evidence = (evidenceRatio * 5).toInt().coerceIn(0, 5)

        return relevance + accuracy + learningScore + explainability + coverage + dedup + noiseControl + difficultyFit + registerFit + evidence
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

    private suspend fun callAiForGroups(prompt: String, listSize: Int): List<List<Int>> {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_EDGE_RETRIES) {
            try {
                val response = callAi(prompt)
                val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
                val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
                val normalized = normalizeResponse(response, unwrapEnabled, repairEnabled)
                return parseJsonIntArrayOfArrays(normalized, listSize)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                val retryable = isRetryableError(e)
                Log.w("WordPoolRepo", "AI batch call attempt ${attempt + 1}/$MAX_EDGE_RETRIES failed (retryable=$retryable)", e)
                if (!retryable) return emptyList()
                if (attempt < MAX_EDGE_RETRIES - 1) {
                    delay(1000L * (attempt + 1))
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

    // ── TEMPORARY: Entry Type Classification ──
    // THIS ENTIRE SECTION SHOULD BE REMOVED after all dictionaries are classified.
    // See: docs/entry-type-classification-temp.md

    override suspend fun classifyEntryTypes(
        dictionaryId: Long,
        isCancelled: () -> Boolean,
        onProgress: (classified: Int, total: Int) -> Unit
    ): Int {
        val totalUnclassified = wordDao.countWordsWithoutEntryType(dictionaryId)
        if (totalUnclassified == 0) return 0

        var totalClassified = 0
        val batchSize = 50

        while (true) {
            if (isCancelled()) break

            val batch = wordDao.getWordsWithoutEntryType(dictionaryId, batchSize)
            if (batch.isEmpty()) break

            val prompt = buildEntryTypePrompt(batch)
            val response = try {
                callAi(prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WordPoolRepo", "Entry type classification batch failed", e)
                break
            }

            val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
            val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
            val normalized = normalizeResponse(response, unwrapEnabled, repairEnabled)

            val results = parseEntryTypeResponse(normalized)
            val resultMap = results.associateBy { it.first }

            batch.forEach { word ->
                val entryType = resultMap[word.id]
                if (entryType != null) {
                    wordDao.updateEntryType(word.id, entryType.second)
                    totalClassified++
                }
            }

            onProgress(totalClassified, totalUnclassified)
        }

        return totalClassified
    }

    private fun buildEntryTypePrompt(words: List<com.xty.englishhelper.data.local.dao.EntryTypeClassificationInput>): String {
        return buildString {
            appendLine("你是英语构词学与词典学专家。下面有 ${words.size} 个词条，请根据你的语言学知识判断每个词条属于哪一类。")
            appendLine()
            appendLine("【三类词条的定义与特征】")
            appendLine()
            appendLine("1. word — 普通英语单词")
            appendLine("   特征：在现代英语中是独立使用的词汇单位，有明确的词性（名词/动词/形容词/副词等），")
            appendLine("   有完整的词义定义，可以独立出现在句子中。")
            appendLine("   例子：affect（动词，影响）、resilient（形容词，有韧性的）、make（动词，做）、happy、run、computer")
            appendLine()
            appendLine("2. root — 拉丁/希腊词根")
            appendLine("   特征：来自拉丁语或希腊语的构词成分，在现代英语中不能独立使用，")
            appendLine("   但作为核心语素派生出一组有规律的同族词。词根的含义需要通过它所派生的词来理解。")
            appendLine("   关键判断标准：这个形式本身是否是现代英语中可以独立使用的词？如果不是，而是作为构词成分存在于一串派生词中，它就是 root。")
            appendLine("   例子：")
            appendLine("   - spect（拉丁语\"看\"）→ spectator, inspect, spectacle, respect, suspect")
            appendLine("   - port（拉丁语\"携带\"）→ transport, export, import, portable")
            appendLine("   - duct（拉丁语\"引导\"）→ conduct, produce, reduce, deduct")
            appendLine("   - ceive/cept（拉丁语\"拿取\"）→ receive, accept, concept, deceive")
            appendLine("   - graph/gram（希腊语\"写\"）→ photograph, diagram, telegram")
            appendLine("   注意：如果一个形式既是词根又恰好是现代英语中的独立单词（如 port 作为\"港口\"），应归类为 word。只有当它纯粹作为构词成分存在时才是 root。")
            appendLine()
            appendLine("3. phrase — 多词表达 / 短语动词")
            appendLine("   特征：由两个或多个词组合而成的固定表达，整体的语义不等于各组成部分的字面意思之和。")
            appendLine("   通常包含介词或副词，与动词组合后产生全新的含义。")
            appendLine("   例子：make up（编造/化妆）、take care of（照顾）、give in（屈服）、look forward to（期待）、break down（崩溃/分解）")
            appendLine()
            appendLine("【返回格式】")
            appendLine("返回JSON数组，每个元素包含 id 和 entry_type：")
            appendLine("""[{"id":1,"entry_type":"word"},{"id":2,"entry_type":"root"},{"id":3,"entry_type":"phrase"}]""")
            appendLine()
            appendLine("entry_type 只能是 word、root、phrase 三者之一。")
            appendLine(Constants.JSON_STRICT_RULES)
            appendLine()
            appendLine("待分类词条：")
            words.forEach { w ->
                appendLine("${w.id}. ${w.spelling}")
            }
        }
    }

    private fun parseEntryTypeResponse(text: String): List<Pair<Long, String>> {
        val results = mutableListOf<Pair<Long, String>>()
        val arrayStart = text.indexOf('[')
        val arrayEnd = text.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd < 0) return results
        val arrayText = text.substring(arrayStart, arrayEnd + 1)

        val objPattern = Regex("""\{[^{}]+\}""")
        objPattern.findAll(arrayText).forEach { match ->
            val obj = match.value
            try {
                val id = extractJsonInt(obj, "id")?.toLong() ?: return@forEach
                val entryType = extractJsonString(obj, "entry_type") ?: return@forEach
                if (entryType in setOf("word", "root", "phrase")) {
                    results.add(id to entryType)
                }
            } catch (_: Exception) {}
        }
        return results
    }
}
