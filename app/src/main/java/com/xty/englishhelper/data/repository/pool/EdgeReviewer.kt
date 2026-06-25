package com.xty.englishhelper.data.repository.pool

import android.util.Log
import androidx.room.withTransaction
import com.xty.englishhelper.data.local.AppDatabase
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
        throw RetryableEdgeException("审核返回不是 JSON 数组")
    }
    val arrayText = text.substring(arrayStart, arrayEnd + 1).trim()
    if (arrayText == "[]") return emptyList()

    val updatesByEdgeId = linkedMapOf<Long, ReviewUpdate>()
    var parsedVerdicts = 0
    val objPattern = Regex("""\{[^{}]+\}""")
    objPattern.findAll(arrayText).forEach { match ->
        val obj = match.value
        val idx = EdgeParser.extractJsonInt(obj, "i") ?: return@forEach
        if (idx !in batch.indices) return@forEach
        val edge = batch[idx]
        val verdict = EdgeParser.extractJsonString(obj, "verdict") ?: return@forEach
        parsedVerdicts++
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
                val newStatus = EdgeParser.extractJsonString(obj, "new_status")?.let { status ->
                    EdgeParser.VALID_STATUSES.firstOrNull { it == status }
                } ?: edge.status
                val newConfidence = EdgeParser.extractJsonDouble(obj, "new_confidence")
                    ?.coerceIn(0.0, 1.0)
                    ?: edge.confidence
                updatesByEdgeId[edge.id] = ReviewUpdate(
                    edgeId = edge.id,
                    newStatus = newStatus,
                    newConfidence = newConfidence
                )
            }

            else -> Unit
        }
    }

    if (parsedVerdicts == 0) {
        throw RetryableEdgeException("审核返回无法解析出任何有效裁决")
    }
    return updatesByEdgeId.values.toList()
}

private data class ReviewedBatchResult(
    val processedEdges: Int,
    val updates: List<ReviewUpdate>
)

/**
 * AI-powered reviewer for low-confidence and warning edges.
 *
 * 审核按“构建同款配置”执行：
 * - 批大小复用词池窗口大小；
 * - 每波最大并发复用词池并发设置；
 * - 请求节奏复用每分钟请求数上限；
 * - 失败等待复用宽松/激进重试模式。
 */
class EdgeReviewer @javax.inject.Inject constructor(
    private val db: AppDatabase,
    private val wordEdgeDao: WordEdgeDao,
    private val aiApiClientProvider: AiApiClientProvider,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val MAX_RETRIES = 4
        private const val LENIENT_RETRY_UNIT_MS = 10_000L
        private const val LENIENT_RETRY_MAX_MS = 120_000L
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
    ): Boolean {
        val needsReview = edges.filter { it.confidence < 0.6 || it.status == "warning" }
        if (needsReview.isEmpty()) {
            onReviewStart(0, 0)
            onProgress(0, 0, encodeReviewProgress(0, 0, 0))
            return false
        }

        val batchSize = settingsDataStore.getPoolWindowSize().coerceAtLeast(1)
        val batches = needsReview.chunked(batchSize)
        val totalEdges = needsReview.size
        val totalBatches = batches.size
        val wordMap = domains.associateBy { it.id }
        val failureTally = AtomicInteger(0)

        onReviewStart(totalEdges, totalBatches)
        onProgress(0, totalEdges, encodeReviewProgress(0, totalBatches, 0))

        val initialMaxConcurrent = settingsDataStore.getPoolMaxConcurrent()
        val initialRpm = settingsDataStore.getPoolRequestsPerMinute()
        val initialRetryMode = settingsDataStore.getPoolRetryMode()
        val rateLimiter = EdgeRateLimiter(initialRpm)
        var lastMaxConcurrent = initialMaxConcurrent
        var lastRpm = initialRpm
        var lastRetryMode = initialRetryMode

        var processedEdges = 0
        var completedBatches = 0
        var modifiedEdges = 0
        val pendingUpdates = mutableListOf<ReviewUpdate>()
        var cursor = 0

        while (cursor < batches.size) {
            awaitWhilePaused(isPaused, isCancelled)
            if (isCancelled()) coroutineContext.ensureActive()
            coroutineContext.ensureActive()

            val currentMaxConcurrent = settingsDataStore.getPoolMaxConcurrent()
            val currentRpm = settingsDataStore.getPoolRequestsPerMinute()
            val currentRetryMode = settingsDataStore.getPoolRetryMode()
            if (
                currentMaxConcurrent != lastMaxConcurrent ||
                currentRpm != lastRpm ||
                currentRetryMode != lastRetryMode
            ) {
                Log.i(
                    "EdgeReviewer",
                    "审核配置热更新: maxConcurrent $lastMaxConcurrent->$currentMaxConcurrent, " +
                        "rpm $lastRpm->$currentRpm, retry $lastRetryMode->$currentRetryMode"
                )
                lastMaxConcurrent = currentMaxConcurrent
                lastRpm = currentRpm
                lastRetryMode = currentRetryMode
            }
            rateLimiter.updateRate(currentRpm)
            val waveEnd = (cursor + currentMaxConcurrent).coerceAtMost(batches.size)
            val semaphore = Semaphore(currentMaxConcurrent)

            coroutineScope {
                val deferreds = (cursor until waveEnd).associateWith { batchIndex ->
                    async {
                        semaphore.withPermit {
                            awaitWhilePaused(isPaused, isCancelled)
                            if (isCancelled()) coroutineContext.ensureActive()
                            rateLimiter.acquire()
                            if (isCancelled()) coroutineContext.ensureActive()

                            val batch = batches[batchIndex]
                            val prompt = EdgePromptBuilder.buildReviewPrompt(batch, wordMap)
                            val changed = reviewBatchWithRetry(
                                prompt = prompt,
                                batch = batch,
                                retryMode = currentRetryMode,
                                failureTally = failureTally
                            ) { attempt, response, error, success ->
                                onBatchAttempt(batchIndex, attempt, response, error, success)
                            }
                            ReviewedBatchResult(
                                processedEdges = batch.size,
                                updates = changed
                            )
                        }
                    }
                }

                for (batchIndex in cursor until waveEnd) {
                    val result = deferreds.getValue(batchIndex).await()
                    processedEdges = (processedEdges + result.processedEdges).coerceAtMost(totalEdges)
                    pendingUpdates += result.updates
                    modifiedEdges += result.updates.size
                    completedBatches++
                    onProgress(
                        processedEdges,
                        totalEdges,
                        encodeReviewProgress(completedBatches, totalBatches, modifiedEdges)
                    )
                }
            }

            cursor = waveEnd
        }
        if (pendingUpdates.isEmpty()) return false
        applyReviewUpdates(pendingUpdates)
        return true
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
                lastError = e.message ?: "审核返回不合规"
                onAttempt(attempt, raw, lastError, false)
                Log.w("EdgeReviewer", "审核批次第 ${attempt + 1}/$MAX_RETRIES 次失败，准备重试: $lastError")
                waitAfterFailure(attempt, retryMode, failureTally, e)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                onAttempt(attempt, raw, lastError, false)
                val retryable = AiErrorUtils.isRetryableError(e)
                Log.w(
                    "EdgeReviewer",
                    "审核批次第 ${attempt + 1}/$MAX_RETRIES 次失败 (retryable=$retryable): $lastError",
                    e
                )
                if (!retryable) {
                    throw IllegalStateException("审核批次失败：$lastError", e)
                }
                waitAfterFailure(attempt, retryMode, failureTally, e)
            }
        }
        throw IllegalStateException(
            "审核批次在 $MAX_RETRIES 次尝试后仍失败：${lastError ?: "未收到可用响应"}"
        )
    }

    private suspend fun applyReviewUpdates(updates: List<ReviewUpdate>) {
        if (updates.isEmpty()) return
        val latestByEdgeId = linkedMapOf<Long, ReviewUpdate>()
        updates.forEach { update ->
            latestByEdgeId[update.edgeId] = update
        }
        db.withTransaction {
            latestByEdgeId.values.forEach { update ->
                wordEdgeDao.updateEdgeStatus(update.edgeId, update.newStatus, update.newConfidence)
            }
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
