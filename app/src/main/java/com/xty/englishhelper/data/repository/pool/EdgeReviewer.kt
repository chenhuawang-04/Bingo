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
    /**
     * Review edges with confidence < 0.6 or status == "warning" using the REVIEWER AI model.
     */
    suspend fun reviewEdgesWithAi(
        edges: List<WordEdgeEntity>,
        domains: List<WordDetails>,
        dictionaryId: Long
    ) {
        val needsReview = edges.filter { it.confidence < 0.6 || it.status == "warning" }
        if (needsReview.isEmpty()) return

        val wordMap = domains.associateBy { it.id }

        needsReview.chunked(20).forEach { batch ->
            coroutineContext.ensureActive()
            val prompt = EdgePromptBuilder.buildReviewPrompt(batch, wordMap)
            try {
                val response = callAiForReviewer(prompt)
                val unwrapEnabled = settingsDataStore.getAiResponseUnwrapEnabled()
                val repairEnabled = settingsDataStore.getAiJsonRepairEnabled()
                val normalized = EdgeParser.normalizeResponse(response, unwrapEnabled, repairEnabled)
                parseAndApplyReview(normalized, batch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("EdgeReviewer", "Edge review batch failed, keeping original edges", e)
            }
        }
    }

    /**
     * Parse the reviewer AI response and apply verdicts to the DB.
     * BUG 8 fix: wraps delete+insert in a single transaction for atomicity.
     */
    private suspend fun parseAndApplyReview(text: String, batch: List<WordEdgeEntity>) {
        val arrayStart = text.indexOf('[')
        val arrayEnd = text.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd < 0) return
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
}
