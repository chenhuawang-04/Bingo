package com.xty.englishhelper.data.repository.pool

import android.util.Log
import com.xty.englishhelper.domain.model.EdgeCluster
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.util.AiJsonRepairer
import com.xty.englishhelper.util.AiResponseUnwrapper

/** Thrown when an AI response is malformed but may succeed on retry. */
internal class RetryableEdgeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown when an AI response is malformed and retrying will not help. */
internal class NonRetryableEdgeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 中止整个词池构建的信号：某个分块的 AI 响应在重试 MAX_EDGE_RETRIES 次后仍不合规
 * （或遇到不可重试错误）。携带面向用户的中文报告信息，最终呈现为任务失败 + 错误提示。
 * 与 [RetryableEdgeException]/[NonRetryableEdgeException] 不同：那两者只影响单次调用，
 * 本异常会让构建停下来并把进度落库以便续传。
 */
internal class PoolBuildDataException(message: String) : Exception(message)

/** Parsed representation of a single edge returned by the edge-generation AI. */
internal data class ParsedEdge(
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

/**
 * Pure functions for parsing AI responses, validating edge data, applying hard thresholds,
 * and normalizing response text.
 * Extracted from [com.xty.englishhelper.data.repository.WordPoolRepositoryImpl].
 */
internal object EdgeParser {

    private val INT_ARRAY_PATTERN = Regex("""\[\s*(\d+(?:\s*,\s*\d+)*)\s*\]""")
    internal val VALID_STATUSES = setOf("core", "support", "warning", "optional")
    internal val ENTRY_TYPE_PATTERN = Regex(""""entry_type"\s*:\s*"(word|root|phrase)"""")

    // ── JSON extraction helpers ──

    fun extractJsonInt(json: String, key: String): Int? {
        val match = Regex(""""$key"\s*:\s*(-?\d+)""").find(json) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun extractJsonString(json: String, key: String): String? {
        val match = Regex(""""$key"\s*:\s*"([^"]*?)"""").find(json) ?: return null
        return match.groupValues[1]
    }

    fun extractJsonDouble(json: String, key: String): Double? {
        val match = Regex(""""$key"\s*:\s*([\d.]+)""").find(json) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    // ── Response normalization ──

    fun normalizeResponse(text: String, unwrapEnabled: Boolean, repairEnabled: Boolean): String {
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

    fun stripCodeFence(text: String): String {
        return text
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .replace("'''json", "", ignoreCase = true)
            .replace("'''", "")
            .trim()
    }

    fun extractFirstJsonObject(text: String): String? {
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

    // ── Edge response parsing ──

    fun parseAndValidateEdgeResponse(text: String, maxValue: Int): List<ParsedEdge> {
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
        var objectsSeen = 0
        objPattern.findAll(arrayText).forEach { match ->
            objectsSeen++
            val obj = match.value
            try {
                val index = extractJsonInt(obj, "i")
                    ?: throw RetryableEdgeException("Missing 'i' field")
                if (index !in 0 until maxValue) {
                    throw RetryableEdgeException("Candidate index $index is outside 0 until $maxValue")
                }

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
                Log.w("EdgeParser", "Failed to parse edge object: $obj", e)
            }
        }
        // 数组里有内容（已排除 "[]"）却找不到任何 JSON 对象 → 服务器返回的结构不符合要求，
        // 按可重试错误处理；空数组在上面已作为“合法的无边响应”提前返回，不会走到这里。
        if (objectsSeen == 0) {
            throw RetryableEdgeException("响应数组非空但未包含任何 JSON 对象: ${arrayText.take(120)}")
        }
        return results
    }

    /**
     * Hard threshold filter: apply 5 gates to each parsed edge.
     * Returns only edges that pass all applicable gates.
     *
     * Gates:
     * 1. sense_match -- SEMANTIC cluster edges must have reason binding to a specific sense
     * 2. pos_rule -- SEMANTIC cluster edges should be same POS
     * 3. evidence_rule -- must have non-blank reason
     * 4. naturalness_rule -- confidence >= 0.4
     * 5. incremental_value_rule -- no near-duplicate edges for same word pair + same cluster
     */
    fun applyHardThresholds(
        edges: List<ParsedEdge>,
        target: WordDetails,
        window: List<WordDetails>
    ): List<ParsedEdge> {
        val targetPOS = target.meanings.map { it.pos }.toSet()
        val seen = mutableSetOf<Pair<Int, EdgeCluster>>()
        return edges.filter { edge ->
            // Gate 3: evidence_rule -- must have reason
            if (edge.reason.isNullOrBlank()) return@filter false

            // Gate 4: naturalness_rule -- minimum confidence
            if (edge.confidence < 0.4) return@filter false

            // Gate 1: sense_match -- SEMANTIC cluster edges must bind to a specific sense
            // BUG 4 fix: added the missing sense_match gate
            // N6 fix: use more specific word-boundary patterns to reduce false positives
            if (edge.edgeType.cluster == EdgeCluster.SEMANTIC) {
                val reasonLower = edge.reason?.lowercase() ?: ""
                val explicitlyUnboundSense = listOf(
                    "未绑定义项",
                    "没有绑定义项",
                    "未指明释义",
                    "未说明释义",
                    "no specific sense",
                    "not tied to a sense"
                ).any(reasonLower::contains)
                if (explicitlyUnboundSense) return@filter false
                // The reason should reference a specific sense/meaning/definition.
                // Use longer, more specific Chinese phrases (>=2 chars) and word-boundary
                // English terms to avoid false positives from single characters like "指".
                val hasSenseBinding =
                    // Chinese multi-char sense indicators (2+ chars to avoid single-char false matches)
                    reasonLower.contains("释义") ||
                    reasonLower.contains("含义") ||
                    reasonLower.contains("定义") ||
                    reasonLower.contains("意为") ||
                    reasonLower.contains("表示") ||
                    reasonLower.contains("同义词") ||
                    reasonLower.contains("近义词") ||
                    reasonLower.contains("反义词") ||
                    reasonLower.contains("上下位") ||
                    reasonLower.contains("词义") ||
                    reasonLower.contains("语义") ||
                    reasonLower.contains("指代") ||
                    // English sense-related terms (word-boundary matching)
                    Regex("\\bsynonym\\b").containsMatchIn(reasonLower) ||
                    Regex("\\bantonym\\b").containsMatchIn(reasonLower) ||
                    Regex("\\bhypernym\\b").containsMatchIn(reasonLower) ||
                    Regex("\\bhyponym\\b").containsMatchIn(reasonLower) ||
                    Regex("\\bsense\\b").containsMatchIn(reasonLower) ||
                    Regex("\\bmeaning\\b").containsMatchIn(reasonLower) ||
                    Regex("\\bdefinition\\b").containsMatchIn(reasonLower) ||
                    Regex("\\brefers?\\s+to\\b").containsMatchIn(reasonLower)
                if (!hasSenseBinding) return@filter false
            }

            // Gate 2: pos_rule -- for SEMANTIC cluster, check if POS differs
            if (edge.edgeType.cluster == EdgeCluster.SEMANTIC) {
                val otherWord = window.getOrNull(edge.index)
                if (otherWord != null) {
                    val otherPOS = otherWord.meanings.map { it.pos }.toSet()
                    // If no POS overlap at all, this is likely a cross-POS relation -- reject
                    if (targetPOS.isNotEmpty() && otherPOS.isNotEmpty() && targetPOS.intersect(otherPOS).isEmpty()) {
                        // Allow if status is warning (learning/confusable pair)
                        if (edge.status != "warning") {
                            return@filter false
                        }
                    }
                }
            }

            // Gate 5: only accepted edges reserve the deduplication key. A rejected edge must
            // not suppress a later valid edge for the same candidate and relationship cluster.
            seen.add(edge.index to edge.edgeType.cluster)
        }
    }

    // ── Entry type response parsing ──

    fun parseEntryTypeResponse(text: String): List<Pair<Long, String>> {
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

    // ── Int array of arrays parsing ──

    fun parseJsonIntArrayOfArrays(text: String, maxValue: Int): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
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
}
