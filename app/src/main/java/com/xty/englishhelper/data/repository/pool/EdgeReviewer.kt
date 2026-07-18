package com.xty.englishhelper.data.repository.pool

import android.util.Log
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.PoolRetryMode
import com.xty.englishhelper.domain.model.WordDetails
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext

internal data class ReviewUpdate(
    val edgeId: Long,
    val newStatus: String,
    val newConfidence: Double
)

internal fun encodeReviewProgress(
    completedBatches: Int,
    totalBatches: Int,
    modifiedEdges: Int
): String = "review|$completedBatches|$totalBatches|$modifiedEdges"

internal fun parseReviewUpdates(
    text: String,
    batch: List<WordEdgeEntity>
): List<ReviewUpdate> {
    val arrayStart = text.indexOf('[')
    val arrayEnd = text.lastIndexOf(']')
    if (arrayStart < 0 || arrayEnd < arrayStart) {
        throw RetryableEdgeException("提纯返回不是 JSON 数组")
    }
    val arrayText = text.substring(arrayStart, arrayEnd + 1).trim()
    if (arrayText == "[]") return emptyList()

    val updatesByEdgeId = linkedMapOf<Long, ReviewUpdate>()
    val reviewedIndices = linkedSetOf<Int>()
    val objPattern = Regex("""\{[^{}]+\}""")
    val objects = objPattern.findAll(arrayText).toList()
    if (objects.size != batch.size) {
        throw RetryableEdgeException("提纯返回必须逐条覆盖本批次 ${batch.size} 条边，实际为 ${objects.size} 条")
    }
    objects.forEach { match ->
        val obj = match.value
        val idx = EdgeParser.extractJsonInt(obj, "i")
            ?: throw RetryableEdgeException("提纯裁决缺少索引 i")
        if (idx !in batch.indices) throw RetryableEdgeException("提纯裁决索引越界：$idx")
        if (!reviewedIndices.add(idx)) throw RetryableEdgeException("提纯裁决包含重复索引：$idx")
        val edge = batch[idx]
        val verdict = EdgeParser.extractJsonString(obj, "verdict")
            ?: throw RetryableEdgeException("提纯裁决缺少 verdict")
        when (verdict) {
            "keep" -> Unit
            "remove" -> {
                updatesByEdgeId[edge.id] = ReviewUpdate(
                    edgeId = edge.id,
                    newStatus = edge.status,
                    newConfidence = 0.0
                )
            }

            "adjust" -> {
                val newStatus = EdgeParser.extractJsonString(obj, "new_status")
                    ?.takeIf { it in EdgeParser.VALID_STATUSES }
                    ?: throw RetryableEdgeException("adjust 裁决缺少有效 new_status")
                val newConfidence = EdgeParser.extractJsonDouble(obj, "new_confidence")
                    ?.takeIf { it in 0.0..1.0 }
                    ?: throw RetryableEdgeException("adjust 裁决缺少有效 new_confidence")
                updatesByEdgeId[edge.id] = ReviewUpdate(
                    edgeId = edge.id,
                    newStatus = newStatus,
                    newConfidence = minOf(newConfidence, edge.confidence)
                )
            }

            else -> throw RetryableEdgeException("未知提纯裁决：$verdict")
        }
    }

    if (reviewedIndices != batch.indices.toSet()) {
        throw RetryableEdgeException("提纯返回未覆盖本批次全部边")
    }
    return updatesByEdgeId.values.toList()
}

private data class ReviewedBatchResult(
    val processedEdges: Int,
    val updates: List<ReviewUpdate>
)

/**
 * AI-powered purifier for existing word-pool edges.
 *
 * 提纯按“构建同款配置”执行：
 * - 批大小复用词池窗口大小；
 * - 每波最大并发复用词池并发设置；
 * - 请求节奏复用每分钟请求数上限；
 * - 失败等待复用宽松/激进重试模式。
 */
class EdgeReviewer @javax.inject.Inject constructor(
    private val wordEdgeDao: WordEdgeDao,
    private val aiApiClientProvider: AiApiClientProvider,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val MAX_RETRIES = 4
        private const val LENIENT_RETRY_UNIT_MS = 10_000L
        private const val LENIENT_RETRY_MAX_MS = 120_000L
        private val EXCLUDED_REVIEW_SOURCES = listOf("user_note", "balanced_local", "balanced_ai")
    }

    suspend fun reviewEdgesWithAi(
        edges: List<WordEdgeEntity>,
        domains: List<WordDetails>,
        isCancelled: () -> Boolean = { false },
        isPaused: () -> Boolean = { false },
        onReviewStart: (totalEdges: Int, totalBatches: Int) -> Unit = { _, _ -> },
        onBatchAttempt: (
            batchIndex: Int,
            attempt: Int,
            response: String?,
            error: String?,
            success: Boolean
        ) -> Unit = { _, _, _, _, _ -> },
        onProgress: (current: Int, total: Int, message: String?) -> Unit = { _, _, _ -> }
    ): Boolean = reviewEdgesWithAi(
        edges = edges,
        wordSpellings = domains.associate { it.id to it.spelling },
        isCancelled = isCancelled,
        isPaused = isPaused,
        onReviewStart = onReviewStart,
        onBatchAttempt = onBatchAttempt,
        onProgress = onProgress
    )

    suspend fun reviewDictionaryEdgesWithAi(
        dictionaryId: Long,
        wordSpellings: Map<Long, String>,
        isCancelled: () -> Boolean = { false },
        isPaused: () -> Boolean = { false },
        onReviewStart: (totalEdges: Int, totalBatches: Int) -> Unit = { _, _ -> },
        onBatchAttempt: (
            batchIndex: Int,
            attempt: Int,
            response: String?,
            error: String?,
            success: Boolean
        ) -> Unit = { _, _, _, _, _ -> },
        onProgress: (current: Int, total: Int, message: String?) -> Unit = { _, _, _ -> }
    ): Boolean {
        val totalEdges = wordEdgeDao.countEdgesExcludingSources(dictionaryId, EXCLUDED_REVIEW_SOURCES)
        if (totalEdges <= 0) {
            onReviewStart(0, 0)
            onProgress(0, 0, encodeReviewProgress(0, 0, 0))
            return false
        }

        val batchSize = settingsDataStore.getPoolWindowSize().coerceAtLeast(1)
        val totalBatches = (totalEdges + batchSize - 1) / batchSize
        val failureTally = AtomicInteger(0)
        val rateLimiter = EdgeRateLimiter(settingsDataStore.getPoolRequestsPerMinute())

        onReviewStart(totalEdges, totalBatches)
        onProgress(0, totalEdges, encodeReviewProgress(0, totalBatches, 0))

        var lastMaxConcurrent = settingsDataStore.getPoolMaxConcurrent().coerceAtLeast(1)
        var lastRpm = settingsDataStore.getPoolRequestsPerMinute()
        var lastRetryMode = settingsDataStore.getPoolRetryMode()
        var lastEdgeId = 0L
        var processedEdges = 0
        var completedBatches = 0
        var modifiedEdges = 0
        var appliedAnyUpdate = false

        while (processedEdges < totalEdges) {
            awaitWhilePaused(isPaused, isCancelled)
            if (isCancelled()) coroutineContext.ensureActive()
            coroutineContext.ensureActive()

            val currentMaxConcurrent = settingsDataStore.getPoolMaxConcurrent().coerceAtLeast(1)
            val currentRpm = settingsDataStore.getPoolRequestsPerMinute()
            val currentRetryMode = settingsDataStore.getPoolRetryMode()
            if (
                currentMaxConcurrent != lastMaxConcurrent ||
                currentRpm != lastRpm ||
                currentRetryMode != lastRetryMode
            ) {
                Log.i(
                    "EdgeReviewer",
                    "提纯配置热更新: maxConcurrent $lastMaxConcurrent->$currentMaxConcurrent, " +
                        "rpm $lastRpm->$currentRpm, retry $lastRetryMode->$currentRetryMode"
                )
                lastMaxConcurrent = currentMaxConcurrent
                lastRpm = currentRpm
                lastRetryMode = currentRetryMode
            }
            rateLimiter.updateRate(currentRpm)

            val waveEdgeLimit = (batchSize * currentMaxConcurrent).coerceAtLeast(batchSize)
            val edgeWave = wordEdgeDao.getEdgesPageExcludingSources(
                dictionaryId,
                lastEdgeId,
                EXCLUDED_REVIEW_SOURCES,
                waveEdgeLimit
            )
            if (edgeWave.isEmpty()) break

            val results = processReviewWave(
                batches = edgeWave.chunked(batchSize),
                batchStartIndex = completedBatches,
                wordSpellings = wordSpellings,
                maxConcurrent = currentMaxConcurrent,
                retryMode = currentRetryMode,
                rateLimiter = rateLimiter,
                failureTally = failureTally,
                isCancelled = isCancelled,
                isPaused = isPaused,
                onBatchAttempt = onBatchAttempt
            )
            results.forEach { result ->
                if (result.updates.isNotEmpty()) {
                    applyReviewUpdates(result.updates)
                    appliedAnyUpdate = true
                }
                processedEdges = (processedEdges + result.processedEdges).coerceAtMost(totalEdges)
                modifiedEdges += result.updates.size
                completedBatches++
                onProgress(
                    processedEdges,
                    totalEdges,
                    encodeReviewProgress(completedBatches, totalBatches, modifiedEdges)
                )
            }
            lastEdgeId = edgeWave.last().id
        }

        return appliedAnyUpdate
    }

    private suspend fun reviewEdgesWithAi(
        edges: List<WordEdgeEntity>,
        wordSpellings: Map<Long, String>,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onReviewStart: (totalEdges: Int, totalBatches: Int) -> Unit,
        onBatchAttempt: (
            batchIndex: Int,
            attempt: Int,
            response: String?,
            error: String?,
            success: Boolean
        ) -> Unit,
        onProgress: (current: Int, total: Int, message: String?) -> Unit
    ): Boolean {
        val reviewTargets = edges
        if (reviewTargets.isEmpty()) {
            onReviewStart(0, 0)
            onProgress(0, 0, encodeReviewProgress(0, 0, 0))
            return false
        }

        val batchSize = settingsDataStore.getPoolWindowSize().coerceAtLeast(1)
        val batches = reviewTargets.chunked(batchSize)
        val totalEdges = reviewTargets.size
        val totalBatches = batches.size
        val failureTally = AtomicInteger(0)

        onReviewStart(totalEdges, totalBatches)
        onProgress(0, totalEdges, encodeReviewProgress(0, totalBatches, 0))

        var lastMaxConcurrent = settingsDataStore.getPoolMaxConcurrent().coerceAtLeast(1)
        var lastRpm = settingsDataStore.getPoolRequestsPerMinute()
        var lastRetryMode = settingsDataStore.getPoolRetryMode()
        val rateLimiter = EdgeRateLimiter(lastRpm)

        var processedEdges = 0
        var completedBatches = 0
        var modifiedEdges = 0
        var appliedAnyUpdate = false
        var cursor = 0

        while (cursor < batches.size) {
            awaitWhilePaused(isPaused, isCancelled)
            if (isCancelled()) coroutineContext.ensureActive()
            coroutineContext.ensureActive()

            val currentMaxConcurrent = settingsDataStore.getPoolMaxConcurrent().coerceAtLeast(1)
            val currentRpm = settingsDataStore.getPoolRequestsPerMinute()
            val currentRetryMode = settingsDataStore.getPoolRetryMode()
            if (
                currentMaxConcurrent != lastMaxConcurrent ||
                currentRpm != lastRpm ||
                currentRetryMode != lastRetryMode
            ) {
                Log.i(
                    "EdgeReviewer",
                    "提纯配置热更新: maxConcurrent $lastMaxConcurrent->$currentMaxConcurrent, " +
                        "rpm $lastRpm->$currentRpm, retry $lastRetryMode->$currentRetryMode"
                )
                lastMaxConcurrent = currentMaxConcurrent
                lastRpm = currentRpm
                lastRetryMode = currentRetryMode
            }
            rateLimiter.updateRate(currentRpm)
            val waveEnd = (cursor + currentMaxConcurrent).coerceAtMost(batches.size)
            val results = processReviewWave(
                batches = batches.subList(cursor, waveEnd),
                batchStartIndex = cursor,
                wordSpellings = wordSpellings,
                maxConcurrent = currentMaxConcurrent,
                retryMode = currentRetryMode,
                rateLimiter = rateLimiter,
                failureTally = failureTally,
                isCancelled = isCancelled,
                isPaused = isPaused,
                onBatchAttempt = onBatchAttempt
            )
            results.forEach { result ->
                if (result.updates.isNotEmpty()) {
                    applyReviewUpdates(result.updates)
                    appliedAnyUpdate = true
                }
                processedEdges = (processedEdges + result.processedEdges).coerceAtMost(totalEdges)
                modifiedEdges += result.updates.size
                completedBatches++
                onProgress(
                    processedEdges,
                    totalEdges,
                    encodeReviewProgress(completedBatches, totalBatches, modifiedEdges)
                )
            }

            cursor = waveEnd
        }
        return appliedAnyUpdate
    }

    private suspend fun processReviewWave(
        batches: List<List<WordEdgeEntity>>,
        batchStartIndex: Int,
        wordSpellings: Map<Long, String>,
        maxConcurrent: Int,
        retryMode: PoolRetryMode,
        rateLimiter: EdgeRateLimiter,
        failureTally: AtomicInteger,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        onBatchAttempt: (
            batchIndex: Int,
            attempt: Int,
            response: String?,
            error: String?,
            success: Boolean
        ) -> Unit
    ): List<ReviewedBatchResult> = coroutineScope {
        val semaphore = Semaphore(maxConcurrent)
        val deferreds = batches.mapIndexed { localIndex, batch ->
            async {
                semaphore.withPermit {
                    awaitWhilePaused(isPaused, isCancelled)
                    if (isCancelled()) coroutineContext.ensureActive()
                    rateLimiter.acquire()
                    if (isCancelled()) coroutineContext.ensureActive()

                    val prompt = EdgePromptBuilder.buildReviewPrompt(batch, wordSpellings)
                    val changed = reviewBatchWithRetry(
                        prompt = prompt,
                        batch = batch,
                        retryMode = retryMode,
                        failureTally = failureTally
                    ) { attempt, response, error, success ->
                        onBatchAttempt(batchStartIndex + localIndex, attempt, response, error, success)
                    }
                    ReviewedBatchResult(
                        processedEdges = batch.size,
                        updates = changed
                    )
                }
            }
        }
        deferreds.map { it.await() }
    }

    private suspend fun reviewBatchWithRetry(
        prompt: String,
        batch: List<WordEdgeEntity>,
        retryMode: PoolRetryMode,
        failureTally: AtomicInteger,
        onAttempt: (attempt: Int, response: String?, error: String?, success: Boolean) -> Unit
    ): List<ReviewUpdate> {
        var lastError: String? = null
        for (attempt in 0 until MAX_RETRIES) {
            var raw: String? = null
            try {
                raw = callAiForReviewer(prompt)
                if (raw.isBlank()) {
                    throw RetryableEdgeException("空响应")
                }
                val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
                val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
                val normalized = EdgeParser.normalizeResponse(raw, unwrapEnabled, repairEnabled)
                if (normalized.isBlank()) {
                    throw RetryableEdgeException("标准化后为空")
                }

                val modifiedEdges = parseReviewUpdates(normalized, batch)
                onAttempt(attempt, raw, null, true)
                return modifiedEdges
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableEdgeException) {
                lastError = e.message ?: "提纯返回不合规"
                onAttempt(attempt, raw, lastError, false)
                Log.w("EdgeReviewer", "提纯批次第 ${attempt + 1}/$MAX_RETRIES 次失败，准备重试: $lastError")
                waitAfterFailure(attempt, retryMode, failureTally, e)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                onAttempt(attempt, raw, lastError, false)
                val retryable = AiErrorUtils.isRetryableError(e)
                Log.w(
                    "EdgeReviewer",
                    "提纯批次第 ${attempt + 1}/$MAX_RETRIES 次失败 (retryable=$retryable): $lastError",
                    e
                )
                if (!retryable) {
                    throw IllegalStateException("提纯批次失败：$lastError", e)
                }
                waitAfterFailure(attempt, retryMode, failureTally, e)
            }
        }
        throw IllegalStateException(
            "提纯批次在 $MAX_RETRIES 次尝试后仍失败：${lastError ?: "未收到可用响应"}"
        )
    }

    private suspend fun applyReviewUpdates(updates: List<ReviewUpdate>) {
        if (updates.isEmpty()) return
        val latestByEdgeId = linkedMapOf<Long, ReviewUpdate>()
        updates.forEach { update ->
            latestByEdgeId[update.edgeId] = update
        }
        latestByEdgeId.values.forEach { update ->
            wordEdgeDao.updateEdgeStatus(update.edgeId, update.newStatus, update.newConfidence)
        }
    }

    private suspend fun awaitWhilePaused(
        isPaused: () -> Boolean,
        isCancelled: () -> Boolean
    ) {
        while (isPaused() && !isCancelled()) {
            delay(500)
        }
    }

    private suspend fun waitAfterFailure(
        attempt: Int,
        retryMode: PoolRetryMode,
        failureTally: AtomicInteger,
        error: Exception?
    ) {
        val totalFailures = failureTally.incrementAndGet()
        if (attempt >= MAX_RETRIES - 1) return
        val delayMs = when (retryMode) {
            PoolRetryMode.LENIENT ->
                (totalFailures.toLong() * LENIENT_RETRY_UNIT_MS).coerceAtMost(LENIENT_RETRY_MAX_MS)

            PoolRetryMode.AGGRESSIVE ->
                if (error != null) AiErrorUtils.retryDelay(error, attempt) else 1000L * (attempt + 1)
        }
        if (delayMs > 0) delay(delayMs)
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
}
