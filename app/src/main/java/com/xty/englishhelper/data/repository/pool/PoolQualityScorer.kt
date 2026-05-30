package com.xty.englishhelper.data.repository.pool

import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.domain.model.EdgeType

/**
 * Stateless quality scorer for word pools.
 * Computes a 0-50 score across 10 dimensions.
 * Thresholds: 42-50 ready; 35-41 needs review; below 34 low quality.
 * Extracted from [com.xty.englishhelper.data.repository.WordPoolRepositoryImpl].
 */
internal object PoolQualityScorer {

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

        val edgeList = poolEdges.toList()
        val size = edgeList.size.toDouble()

        // 1. Relevance (0-5): average confidence mapped to 0-5
        val avgConfidence = edgeList.map { it.confidence }.average()
        val relevance = (avgConfidence * 5).toInt().coerceIn(0, 5)

        // 2. Accuracy (0-5): proportion of edges with status core/support
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

        // 6. Dedup (0-5): fewer near-duplicate edges = better
        val uniquePairs = edgeList.map { minOf(it.wordIdA, it.wordIdB) to maxOf(it.wordIdA, it.wordIdB) }.distinct().size
        val dedup = if (edgeList.isEmpty()) 0 else (uniquePairs.toDouble() / edgeList.size * 5).toInt().coerceIn(0, 5)

        // 7. Noise control (0-5): fewer optional edges = better
        val optionalRatio = edgeList.count { it.status == "optional" }.toDouble() / size
        val noiseControl = ((1.0 - optionalRatio) * 5).toInt().coerceIn(0, 5)

        // 8. Difficulty fit (0-5): average confidence as proxy
        val difficultyFit = (avgConfidence * 5).toInt().coerceIn(0, 5)

        // 9. Register fit (0-5): proportion of edges with reason indicating register awareness
        // BUG 5 fix: removed coerceAtLeast(3) that was inflating scores
        val registerAware = edgeList.count {
            val r = it.reason?.lowercase() ?: ""
            r.contains("语域") || r.contains("正式") || r.contains("口语") || r.contains("学术") || r.contains("register")
        }
        val registerFit = if (edgeList.isEmpty()) 0
        else (registerAware.toDouble() / size * 5).toInt().coerceIn(0, 5)

        // 10. Evidence (0-5): proportion with evidence_source
        val evidenceRatio = edgeList.count { !it.evidenceSource.isNullOrBlank() }.toDouble() / size
        val evidence = (evidenceRatio * 5).toInt().coerceIn(0, 5)

        return relevance + accuracy + learningScore + explainability + coverage + dedup + noiseControl + difficultyFit + registerFit + evidence
    }
}
