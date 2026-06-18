package com.xty.englishhelper.domain.usecase.brainstorm

import com.xty.englishhelper.domain.model.EdgeNeighbor
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.domain.repository.WordRepository
import javax.inject.Inject

/**
 * 头脑风暴选词与排序（阶段B·选词精良）。
 *
 * 取代旧 `StudyViewModel.applyBrainstormOrder` 的「到期词 + 全部邻居 + 新词，BFS 分层」做法，改为：
 *  1. **边质量门槛**：只保留 `confidence ≥ minConfidence` 且非 `optional` 的关联边（对齐词池重建过滤）。
 *  2. **关联评分**：每条边按 `relationStrength × confidence × learningValue × 类型权重` 打分，
 *     易混淆 / 反义 / 形近 / 同义等对比型关系权重更高（最助记忆）；两词之间取最强边作为"键合强度"。
 *  3. **学习簇装配**：以到期词优先、其次新词为种子，沿最强键合贪心生长出上限 [clusterSize] 的连贯小簇
 *     （同义家族 / 易混三连 / 同根族）——把"中心辐射"用到学习顺序上，相关词成片背、互相强化。
 *  4. **新词锚定已知词**：新词种子按"与已掌握词（FSRS stability 高 / 到期词）的最强键合"降序排列，
 *     让新词优先挂靠到已知词所在的簇，形成脚手架式记忆。
 *
 * 关联数据缺失（未建词池）时自然退化为「到期词在前、新词在后」的单词簇顺序，不报错。
 */
class BuildBrainstormSessionUseCase @Inject constructor(
    private val wordPoolRepository: WordPoolRepository,
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository
) {
    suspend operator fun invoke(
        dueWords: List<WordDetails>,
        newWords: List<WordDetails>,
        clusterSize: Int,
        minConfidence: Double
    ): BrainstormSession {
        val seedWords = dueWords + newWords
        if (seedWords.isEmpty()) {
            return BrainstormSession(emptyList(), emptyMap(), emptyMap())
        }
        val dictionaryId = seedWords.first().dictionaryId
        val dueIds = dueWords.map { it.id }.toSet()

        // 1. 质量门槛：过滤弱边 / optional 边。
        val rawAdj = wordPoolRepository.getWordEdgeAdjacencyDetailed(dictionaryId)
        val gatedAdj: Map<Long, List<EdgeNeighbor>> = rawAdj.mapValues { (_, neighbors) ->
            neighbors.filter { it.confidence >= minConfidence && it.status != "optional" }
        }

        // 2. 候选词集合：到期词 + 其（已过滤）邻居（补入以强化簇，即便邻居既非到期也非新词）+ 新词。
        val byId = LinkedHashMap<Long, WordDetails>()
        dueWords.forEach { byId[it.id] = it }
        for (w in dueWords) {
            for (n in gatedAdj[w.id].orEmpty()) {
                if (n.neighborId !in byId) {
                    wordRepository.getWordById(n.neighborId)?.let { byId[n.neighborId] = it }
                }
            }
        }
        newWords.forEach { if (it.id !in byId) byId[it.id] = it }

        // 3. 会话内子图：每词在会话内的邻居 + 键合强度（两词间取最强边得分）。
        val sessionAdj = buildSessionAdjacency(byId.keys, gatedAdj)

        // 已掌握程度（FSRS stability）：用于新词锚定与簇内排序的锚点选择。
        val stability = studyRepository.getStudyStatesForDictionary(dictionaryId)
            .associate { it.wordId to it.stability }

        // 4. 簇装配 + 排序。
        val (ordered, clusterOf) = assembleClusters(
            byId = byId,
            sessionAdj = sessionAdj,
            stability = stability,
            dueIds = dueIds,
            newWordIds = newWords.map { it.id }.toSet(),
            clusterSize = clusterSize.coerceAtLeast(1)
        )

        // 会话内、已过滤的邻接（仅两端都在会话内）——供 UI「关联词 / 为何相关」展示。
        val sessionGated = byId.keys.associateWith { id ->
            gatedAdj[id].orEmpty().filter { it.neighborId in byId }
        }

        return BrainstormSession(
            orderedWords = ordered,
            clusterOf = clusterOf,
            gatedAdjacency = sessionGated
        )
    }

    private fun buildSessionAdjacency(
        wordIds: Set<Long>,
        gatedAdj: Map<Long, List<EdgeNeighbor>>
    ): Map<Long, List<Bond>> {
        val out = HashMap<Long, MutableList<Bond>>()
        for (id in wordIds) {
            val best = HashMap<Long, Double>()
            for (n in gatedAdj[id].orEmpty()) {
                if (n.neighborId !in wordIds) continue
                val s = associationScore(n)
                val cur = best[n.neighborId]
                if (cur == null || s > cur) best[n.neighborId] = s
            }
            if (best.isNotEmpty()) {
                out[id] = best.map { Bond(it.key, it.value) }.toMutableList()
            }
        }
        return out
    }

    private fun assembleClusters(
        byId: Map<Long, WordDetails>,
        sessionAdj: Map<Long, List<Bond>>,
        stability: Map<Long, Double>,
        dueIds: Set<Long>,
        newWordIds: Set<Long>,
        clusterSize: Int
    ): Pair<List<WordDetails>, Map<Long, Int>> {
        val placed = HashSet<Long>()

        // 种子顺序：到期词先（保持最久未复习在前的传入序），其次新词（按与已知词的锚定强度降序）。
        val dueSeeds = byId.keys.filter { it in dueIds }
        val knownIds = dueIds + stability.keys.filter { (stability[it] ?: 0.0) > 0.0 }
        val newSeeds = byId.keys.filter { it !in dueIds }.sortedWith(
            compareByDescending<Long> { id -> anchorScore(id, sessionAdj, knownIds) }
                .thenBy { it }
        )
        val seedOrder = dueSeeds + newSeeds

        val orderedIds = mutableListOf<Long>()
        val clusterOf = HashMap<Long, Int>()
        var clusterIndex = 0

        for (seed in seedOrder) {
            if (seed in placed) continue
            val cluster = growCluster(seed, sessionAdj, placed, dueIds, stability, clusterSize)
            val orderedCluster = orderWithinCluster(cluster, sessionAdj, stability)
            for (id in orderedCluster) {
                orderedIds.add(id)
                clusterOf[id] = clusterIndex
            }
            clusterIndex++
        }

        val ordered = orderedIds.mapNotNull { byId[it] }
        return ordered to clusterOf
    }

    /** 从 [seed] 出发沿最强键合贪心生长一个簇（连通扩张，最多 [clusterSize] 个词）。 */
    private fun growCluster(
        seed: Long,
        sessionAdj: Map<Long, List<Bond>>,
        placed: HashSet<Long>,
        dueIds: Set<Long>,
        stability: Map<Long, Double>,
        clusterSize: Int
    ): List<Long> {
        val cluster = mutableListOf(seed)
        placed.add(seed)
        val bestBond = HashMap<Long, Double>()  // 候选词 -> 到当前簇的最强键合

        fun pushFrontier(w: Long) {
            for (b in sessionAdj[w].orEmpty()) {
                if (b.other in placed) continue
                val cur = bestBond[b.other]
                if (cur == null || b.score > cur) bestBond[b.other] = b.score
            }
        }
        pushFrontier(seed)

        while (cluster.size < clusterSize) {
            val pick = bestBond.entries
                .filter { it.key !in placed }
                .maxWithOrNull(
                    compareBy<Map.Entry<Long, Double>> { it.value }
                        .thenBy { if (it.key in dueIds) 1 else 0 }
                        .thenBy { stability[it.key] ?: 0.0 }
                        .thenByDescending { it.key }
                ) ?: break
            cluster.add(pick.key)
            placed.add(pick.key)
            pushFrontier(pick.key)
        }
        return cluster
    }

    /** 簇内排序：锚点（最已知 / 度数最高）在前，其余沿键合贪心成链，使相关词彼此相邻出现。 */
    private fun orderWithinCluster(
        cluster: List<Long>,
        sessionAdj: Map<Long, List<Bond>>,
        stability: Map<Long, Double>
    ): List<Long> {
        if (cluster.size <= 1) return cluster
        val set = cluster.toHashSet()
        val degree = cluster.associateWith { w -> sessionAdj[w].orEmpty().count { it.other in set } }
        val anchor = cluster.maxWith(
            compareBy<Long> { stability[it] ?: 0.0 }
                .thenBy { degree[it] ?: 0 }
                .thenByDescending { it }
        )
        val out = mutableListOf(anchor)
        val emitted = hashSetOf(anchor)
        val bestBond = HashMap<Long, Double>()

        fun push(w: Long) {
            for (b in sessionAdj[w].orEmpty()) {
                if (b.other !in set || b.other in emitted) continue
                val cur = bestBond[b.other]
                if (cur == null || b.score > cur) bestBond[b.other] = b.score
            }
        }
        push(anchor)

        while (out.size < cluster.size) {
            val next = bestBond.entries
                .filter { it.key !in emitted }
                .maxWithOrNull(
                    compareBy<Map.Entry<Long, Double>> { it.value }
                        .thenBy { stability[it.key] ?: 0.0 }
                        .thenByDescending { it.key }
                )
            if (next == null) {
                // 安全兜底：簇本应连通；若有残余，按 id 追加。
                cluster.filter { it !in emitted }.sorted().forEach { out.add(it); emitted.add(it) }
                break
            }
            out.add(next.key)
            emitted.add(next.key)
            push(next.key)
        }
        return out
    }

    /** 某新词与"已知词"的最强键合——越大越该优先（锚定到已知知识）。 */
    private fun anchorScore(
        id: Long,
        sessionAdj: Map<Long, List<Bond>>,
        knownIds: Set<Long>
    ): Double = sessionAdj[id].orEmpty()
        .filter { it.other in knownIds }
        .maxOfOrNull { it.score } ?: 0.0

    /** 关联边记忆价值评分：强度 × 置信度 × 学习价值 × 类型权重。 */
    private fun associationScore(n: EdgeNeighbor): Double =
        n.relationStrength.coerceIn(1, 5) *
            n.confidence.coerceIn(0.0, 1.0) *
            n.learningValue.coerceIn(1, 5) *
            typeWeight(n.type)

    /** 类型权重：对比 / 易混型关系最助记忆，权重最高；用法类略低。 */
    private fun typeWeight(type: EdgeType): Double = when (type) {
        EdgeType.LEARNING_CONFUSABLE -> 1.5
        EdgeType.LEARNING_MISUSE_PAIR -> 1.4
        EdgeType.SEMANTIC_ANTONYM -> 1.4
        EdgeType.FORM_MINIMAL_PAIR -> 1.3
        EdgeType.SEMANTIC_SYNONYM -> 1.3
        EdgeType.FORM_SPELLING -> 1.2
        EdgeType.FAMILY_SAME_ROOT -> 1.1
        EdgeType.FAMILY_DERIVATION -> 1.1
        EdgeType.FAMILY_INFLECTION -> 1.0
        EdgeType.FORM_HOMOPHONE -> 1.0
        EdgeType.FORM_PRONUNCIATION -> 1.0
        EdgeType.SEMANTIC_OVERLAP -> 0.9
        EdgeType.SEMANTIC_HYPERNYM -> 0.85
        EdgeType.SEMANTIC_HYPONYM -> 0.85
        EdgeType.USAGE_COLLOCATION -> 0.8
        EdgeType.USAGE_PHRASE -> 0.75
        EdgeType.USAGE_PATTERN -> 0.7
    }

    private data class Bond(val other: Long, val score: Double)
}

/**
 * 一次头脑风暴会话的选词结果。
 * @param orderedWords 最终学习顺序（按簇成片排列，簇内相关词相邻）。
 * @param clusterOf wordId -> 簇下标（供阶段C簇掌握度、阶段D面包屑使用）。
 * @param gatedAdjacency 会话内、已过质量门槛的关联邻接（供 UI「关联词 / 为何相关」展示）。
 */
data class BrainstormSession(
    val orderedWords: List<WordDetails>,
    val clusterOf: Map<Long, Int>,
    val gatedAdjacency: Map<Long, List<EdgeNeighbor>>
)
