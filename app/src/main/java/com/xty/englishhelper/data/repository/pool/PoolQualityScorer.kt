package com.xty.englishhelper.data.repository.pool

import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.domain.model.EdgeCluster
import com.xty.englishhelper.domain.model.EdgeType

/**
 * Stateless quality scorer for word pools.
 * Computes a 0-50 score across 10 dimensions.
 * Thresholds: 42-50 ready; 35-41 needs review; below 34 low quality.
 * Extracted from [com.xty.englishhelper.data.repository.WordPoolRepositoryImpl].
 */
internal object PoolQualityScorer {

    class Accumulator {
        private var edgeCount = 0
        private var confidenceSum = 0.0
        private var accurateCount = 0
        private var learningValueSum = 0
        private var reasonCount = 0
        private val clusterSet = linkedSetOf<EdgeCluster>()
        private val uniquePairs = linkedSetOf<Pair<Long, Long>>()
        private var optionalCount = 0
        private var difficultyAwareCount = 0
        private var registerAwareCount = 0
        private var evidenceCount = 0

        fun accept(edge: WordEdgeEntity) {
            edgeCount++
            confidenceSum += edge.confidence
            if (edge.status == "core" || edge.status == "support") accurateCount++
            learningValueSum += edge.learningValue
            if (!edge.reason.isNullOrBlank()) reasonCount++
            EdgeType.fromDbValue(edge.edgeType)?.cluster?.let(clusterSet::add)
            uniquePairs.add(minOf(edge.wordIdA, edge.wordIdB) to maxOf(edge.wordIdA, edge.wordIdB))
            if (edge.status == "optional") optionalCount++
            if (!edge.difficultyCefr.isNullOrBlank()) difficultyAwareCount++
            val reason = edge.reason?.lowercase() ?: ""
            if (
                reason.contains("语域") ||
                reason.contains("正式") ||
                reason.contains("口语") ||
                reason.contains("学术") ||
                reason.contains("register")
            ) {
                registerAwareCount++
            }
            if (!edge.evidenceSource.isNullOrBlank()) evidenceCount++
        }

        fun score(): Int {
            if (edgeCount == 0) return 0
            val size = edgeCount.toDouble()
            val relevance = (confidenceSum / size * 5).toInt().coerceIn(0, 5)
            val accuracy = (accurateCount / size * 5).toInt().coerceIn(0, 5)
            val learningScore = (learningValueSum / size).toInt().coerceIn(0, 5)
            val explainability = (reasonCount / size * 5).toInt().coerceIn(0, 5)
            val coverage = clusterSet.size.coerceIn(0, 5)
            val dedup = (uniquePairs.size.toDouble() / size * 5).toInt().coerceIn(0, 5)
            val noiseControl = ((1.0 - optionalCount / size) * 5).toInt().coerceIn(0, 5)
            val difficultyFit = (difficultyAwareCount / size * 5).toInt().coerceIn(0, 5)
            val registerFit = (registerAwareCount / size * 5).toInt().coerceIn(0, 5)
            val evidence = (evidenceCount / size * 5).toInt().coerceIn(0, 5)
            return relevance + accuracy + learningScore + explainability + coverage + dedup +
                noiseControl + difficultyFit + registerFit + evidence
        }
    }

    fun computePoolQualityScore(
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

        val accumulator = Accumulator()
        poolEdges.forEach(accumulator::accept)
        return accumulator.score()
    }
}
