package com.xty.englishhelper.data.repository.pool

import android.util.Log
import androidx.room.withTransaction
import com.xty.englishhelper.data.local.AppDatabase
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.local.entity.WordEdgeExcludedEntity
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.WordDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * AI-powered reviewer for low-confidence and warning edges.
 * Extracted from [com.xty.englishhelper.data.repository.WordPoolRepositoryImpl].
 *
 * BUG 8 fix: parseAndApplyReview wraps delete+insert in a DB transaction.
 */
class EdgeReviewer @javax.inject.Inject constructor(
    private val db: AppDatabase,
    private val wordEdgeDao: WordEdgeDao,
    private val aiApiClientProvider: AiApiClientProvider,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val MAX_RETRIES = 3
    }

    /**
     * Review edges with confidence < 0.6 or status == "warning" using the REVIEWER AI model.
     * @return true if any edges were modified (removed or adjusted), false otherwise.
     */
    suspend fun reviewEdgesWithAi(
        edges: List<WordEdgeEntity>,
        domains: List<WordDetails>,
        dictionaryId: Long
    ): Boolean {
        val needsReview = edges.filter { it.confidence < 0.6 || it.status == "warning" }
        if (needsReview.isEmpty()) return false

        val wordMap = domains.associateBy { it.id }
        var modified = false

        needsReview.chunked(20).forEach { batch ->
            coroutineContext.ensureActive()
            val prompt = EdgePromptBuilder.buildReviewPrompt(batch, wordMap)
            try {
                val normalized = callAiForReviewerWithRetry(prompt)
                    ?: return@forEach // all retries exhausted, skip this batch
                if (parseAndApplyReview(normalized, batch)) {
                    modified = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("EdgeReviewer", "Edge review batch failed, keeping original edges", e)
            }
        }
        return modified
    }

    /**
     * Call AI with retry logic, blank response check, and normalization.
     * Returns normalized response string, or null if all retries exhausted.
     */
    private suspend fun callAiForReviewerWithRetry(prompt: String): String? {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val raw = callAiForReviewer(prompt)
                if (raw.isBlank()) {
                    Log.w("EdgeReviewer", "AI returned empty response on attempt ${attempt + 1}")
                    lastException = RetryableEdgeException("Empty AI response")
                    if (attempt < MAX_RETRIES - 1) delay(1000L * (attempt + 1))
                    continue
                }
                val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
                val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
                val normalized = EdgeParser.normalizeResponse(raw, unwrapEnabled, repairEnabled)
                if (normalized.isBlank()) {
                    Log.w("EdgeReviewer", "Normalized response is blank on attempt ${attempt + 1}, raw=${raw.take(200)}")
                    lastException = RetryableEdgeException("Normalized response is blank")
                    if (attempt < MAX_RETRIES - 1) delay(1000L * (attempt + 1))
                    continue
                }
                return normalized
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableEdgeException) {
                lastException = e
                Log.w("EdgeReviewer", "Malformed AI response on attempt ${attempt + 1}/$MAX_RETRIES, retrying", e)
                if (attempt < MAX_RETRIES - 1) delay(1000L * (attempt + 1))
            } catch (e: Exception) {
                lastException = e
                val retryable = AiErrorUtils.isRetryableError(e)
                Log.w("EdgeReviewer", "AI review attempt ${attempt + 1}/$MAX_RETRIES failed (retryable=$retryable)", e)
                if (!retryable) return null
                if (attempt < MAX_RETRIES - 1) delay(AiErrorUtils.retryDelay(e, attempt))
            }
        }
        Log.w("EdgeReviewer", "AI review failed after $MAX_RETRIES attempts", lastException)
        return null
    }

    /**
     * Parse the reviewer AI response and apply verdicts to the DB.
     * BUG 8 fix: wraps delete+insert in a single transaction for atomicity.
     * @return true if any edges were removed or adjusted.
     */
    private suspend fun parseAndApplyReview(text: String, batch: List<WordEdgeEntity>): Boolean {
        val arrayStart = text.indexOf('[')
        val arrayEnd = text.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd < 0) return false
        val arrayText = text.substring(arrayStart, arrayEnd + 1)

        // Collect all operations first, then apply in a transaction
        data class RemoveOp(val edge: WordEdgeEntity, val excluded: WordEdgeExcludedEntity)
        data class AdjustOp(val edgeId: Long, val newStatus: String, val newConfidence: Double)

        val removeOps = mutableListOf<RemoveOp>()
        val adjustOps = mutableListOf<AdjustOp>()

        val objPattern = Regex("""\{[^{}]+\}""")
        objPattern.findAll(arrayText).forEach { match ->
            val obj = match.value
            try {
                val idx = EdgeParser.extractJsonInt(obj, "i") ?: return@forEach
                if (idx !in batch.indices) return@forEach
                val edge = batch[idx]

                val verdict = EdgeParser.extractJsonString(obj, "verdict") ?: return@forEach
                when (verdict) {
                    "remove" -> {
                        removeOps.add(RemoveOp(
                            edge = edge,
                            excluded = WordEdgeExcludedEntity(
                                wordIdA = edge.wordIdA,
                                wordIdB = edge.wordIdB,
                                dictionaryId = edge.dictionaryId,
                                edgeType = edge.edgeType,
                                reason = EdgeParser.extractJsonString(obj, "note") ?: "审核移除"
                            )
                        ))
                    }
                    "adjust" -> {
                        val newStatus = EdgeParser.extractJsonString(obj, "new_status")?.let { s ->
                            EdgeParser.VALID_STATUSES.firstOrNull { it == s }
                        } ?: edge.status
                        val newConfidence = EdgeParser.extractJsonDouble(obj, "new_confidence")?.coerceIn(0.0, 1.0) ?: edge.confidence
                        adjustOps.add(AdjustOp(edge.id, newStatus, newConfidence))
                    }
                    // "keep" -> no-op
                }
            } catch (e: Exception) {
                Log.w("EdgeReviewer", "Failed to parse review item: $obj", e)
            }
        }

        // BUG 8 fix: apply all operations in a single transaction
        if (removeOps.isNotEmpty() || adjustOps.isNotEmpty()) {
            db.withTransaction {
                removeOps.forEach { op ->
                    wordEdgeDao.deleteEdgeById(op.edge.id)
                    wordEdgeDao.insertExcluded(listOf(op.excluded))
                }
                adjustOps.forEach { op ->
                    wordEdgeDao.updateEdgeStatus(op.edgeId, op.newStatus, op.newConfidence)
                }
            }
            return true
        }
        return false
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
