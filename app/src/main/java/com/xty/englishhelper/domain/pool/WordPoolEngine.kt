package com.xty.englishhelper.domain.pool

import com.xty.englishhelper.domain.model.EdgeType

data class PoolCandidate(
    val index: Int,
    val wordId: Long,
    val spelling: String,
    val meanings: List<String>,
    val synonymSpellings: List<String>,
    val similarSpellings: List<String>,
    val cognateSpellings: List<String>,
    val associatedWordIds: List<Long>
)

data class BuiltPool(
    val memberIndices: List<Int>,
    val coreIndex: Int? = null
)

data class BalancedPoolRelation(
    val indexA: Int,
    val indexB: Int,
    val edgeType: EdgeType,
    val evidenceSource: String
)

data class BalancedPoolBuild(
    val pools: List<BuiltPool>,
    val relations: List<BalancedPoolRelation>
)

class WordPoolEngine {
    companion object {
        val MEANING_STOPWORDS = setOf(
            "使", "方法", "方式", "情况", "过程", "行为", "状态",
            "特点", "性质", "结果", "原因", "表示", "关于", "一种",
            "一个", "有关", "某人", "某事", "某物", "正式"
        )
        const val MAX_POOL_SIZE = 15

        fun normalize(text: String): String = text
            .lowercase()
            .replace(Regex("\\s*\\([^)]*\\)"), "")
            .replace(Regex("\\s+(v\\.|n\\.|adj\\.|adv\\.|prep\\.)\\s*$"), "")
            .replace("-", " ")
            .trim()
    }

    fun buildPools(candidates: List<PoolCandidate>): List<BuiltPool> =
        buildPoolsWithRelations(candidates).pools

    fun buildPoolsWithRelations(candidates: List<PoolCandidate>): BalancedPoolBuild {
        if (candidates.isEmpty()) return BalancedPoolBuild(emptyList(), emptyList())
        val n = candidates.size
        val uf = UnionFind(n)
        val relations = linkedSetOf<BalancedPoolRelation>()

        fun addRelation(a: Int, b: Int, type: EdgeType, source: String) {
            if (a == b) return
            val (first, second) = if (a < b) a to b else b to a
            relations += BalancedPoolRelation(first, second, type, source)
        }

        // Build normalized spelling lookup: normalizedSpelling -> list of candidate indices
        val normalizedLookup = mutableMapOf<String, MutableList<Int>>()
        candidates.forEach { c ->
            val norm = normalize(c.spelling)
            normalizedLookup.getOrPut(norm) { mutableListOf() }.add(c.index)
        }

        // Build wordId -> index lookup
        val wordIdToIndex = mutableMapOf<Long, Int>()
        candidates.forEach { c -> wordIdToIndex[c.wordId] = c.index }

        // Track edge types for splitting: direct references vs edit distance
        val directRefEdges = mutableSetOf<Pair<Int, Int>>()
        val editDistEdges = mutableSetOf<Pair<Int, Int>>()

        // Signal A: Edit distance (Levenshtein ≤ 2, word length ≥ 4)
        val normalizedSpellings = candidates.map { normalize(it.spelling) }
        for (i in 0 until n) {
            if (normalizedSpellings[i].length < 4) continue
            for (j in i + 1 until n) {
                if (normalizedSpellings[j].length < 4) continue
                if (kotlin.math.abs(normalizedSpellings[i].length - normalizedSpellings[j].length) > 2) continue
                if (levenshtein(normalizedSpellings[i], normalizedSpellings[j]) <= 2) {
                    uf.union(i, j)
                    val edge = if (i < j) Pair(i, j) else Pair(j, i)
                    editDistEdges.add(edge)
                    addRelation(i, j, EdgeType.FORM_SPELLING, "balanced_local")
                }
            }
        }

        // Signal B: Direct cross-reference
        candidates.forEach { c ->
            val typedRefs = c.synonymSpellings.map { it to EdgeType.SEMANTIC_SYNONYM } +
                c.similarSpellings.map { it to EdgeType.LEARNING_CONFUSABLE } +
                c.cognateSpellings.map { it to EdgeType.FAMILY_SAME_ROOT }
            typedRefs.forEach { (ref, edgeType) ->
                val normRef = normalize(ref)
                val targets = normalizedLookup[normRef]
                if (targets != null) {
                    targets.forEach { targetIdx ->
                        if (targetIdx != c.index) {
                            uf.union(c.index, targetIdx)
                            val edge = if (c.index < targetIdx) Pair(c.index, targetIdx) else Pair(targetIdx, c.index)
                            directRefEdges.add(edge)
                            addRelation(c.index, targetIdx, edgeType, "balanced_local")
                        }
                    }
                }
            }
        }

        // Signal C: Indirect cross-reference (transitive)
        // If ≥2 words reference the same normalize(spelling) even if not in the dictionary
        val refToSources = mutableMapOf<String, MutableList<Int>>()
        candidates.forEach { c ->
            val allRefSpellings = c.synonymSpellings + c.similarSpellings + c.cognateSpellings
            allRefSpellings.forEach { ref ->
                val normRef = normalize(ref)
                refToSources.getOrPut(normRef) { mutableListOf() }.add(c.index)
            }
        }
        refToSources.values.forEach { sources ->
            if (sources.size >= 2) {
                for (i in 0 until sources.size - 1) {
                    for (j in i + 1 until sources.size) {
                        if (sources[i] != sources[j]) {
                            uf.union(sources[i], sources[j])
                            val edge = if (sources[i] < sources[j]) Pair(sources[i], sources[j]) else Pair(sources[j], sources[i])
                            directRefEdges.add(edge)
                            addRelation(sources[i], sources[j], EdgeType.SEMANTIC_OVERLAP, "balanced_local")
                        }
                    }
                }
            }
        }

        // Signal D: Meaning substring match (Chinese ≥3 chars, not stopword)
        val meaningSubstringToWords = mutableMapOf<String, MutableList<Int>>()
        candidates.forEach { c ->
            val substrings = mutableSetOf<String>()
            c.meanings.forEach { meaning ->
                extractChineseSubstrings(meaning, 3).forEach { sub ->
                    if (sub !in MEANING_STOPWORDS) {
                        substrings.add(sub)
                    }
                }
            }
            substrings.forEach { sub ->
                meaningSubstringToWords.getOrPut(sub) { mutableListOf() }.add(c.index)
            }
        }
        meaningSubstringToWords.values.forEach { indices ->
            if (indices.size >= 2) {
                for (i in 0 until indices.size - 1) {
                    for (j in i + 1 until indices.size) {
                        uf.union(indices[i], indices[j])
                        val edge = if (indices[i] < indices[j]) Pair(indices[i], indices[j]) else Pair(indices[j], indices[i])
                        directRefEdges.add(edge)
                        addRelation(indices[i], indices[j], EdgeType.SEMANTIC_OVERLAP, "balanced_local")
                    }
                }
            }
        }

        // Signal E: word_associations (already filtered >= 0.35)
        candidates.forEach { c ->
            c.associatedWordIds.forEach { assocId ->
                val targetIdx = wordIdToIndex[assocId]
                if (targetIdx != null && targetIdx != c.index) {
                    uf.union(c.index, targetIdx)
                    val edge = if (c.index < targetIdx) Pair(c.index, targetIdx) else Pair(targetIdx, c.index)
                    directRefEdges.add(edge)
                    addRelation(c.index, targetIdx, EdgeType.FAMILY_SAME_ROOT, "balanced_local")
                }
            }
        }

        // Extract connected components (≥2 members)
        val components = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            val root = uf.find(i)
            components.getOrPut(root) { mutableListOf() }.add(i)
        }

        val result = mutableListOf<BuiltPool>()
        components.values.forEach { component ->
            if (component.size < 2) return@forEach

            if (component.size <= MAX_POOL_SIZE) {
                result.add(BuiltPool(memberIndices = component.sorted()))
            } else {
                // Split large component
                result.addAll(
                    splitComponent(component, candidates, directRefEdges, editDistEdges)
                )
            }
        }

        return BalancedPoolBuild(result, relations.toList())
    }

    /**
     * Merge AI-produced groups into existing results.
     * [aiGroups] is a list of index-lists from the AI response.
     * Runs union-find on the combined set, then splits as usual.
     */
    fun mergeAiGroups(
        candidates: List<PoolCandidate>,
        existingPools: List<BuiltPool>,
        aiGroups: List<List<Int>>
    ): List<BuiltPool> = mergeAiGroupsWithRelations(
        candidates = candidates,
        existingBuild = BalancedPoolBuild(existingPools, emptyList()),
        aiGroups = aiGroups
    ).pools

    fun mergeAiGroupsWithRelations(
        candidates: List<PoolCandidate>,
        existingBuild: BalancedPoolBuild,
        aiGroups: List<List<Int>>
    ): BalancedPoolBuild {
        val n = candidates.size
        val uf = UnionFind(n)
        val mergedEdges = mutableSetOf<Pair<Int, Int>>()
        val relations = existingBuild.relations.toMutableSet()

        if (existingBuild.relations.isNotEmpty()) {
            existingBuild.relations.forEach { relation ->
                val a = relation.indexA
                val b = relation.indexB
                if (a in 0 until n && b in 0 until n && a != b) {
                    uf.union(a, b)
                    mergedEdges += if (a < b) a to b else b to a
                }
            }
        } else {
            // Compatibility path for callers that only provide legacy pool membership.
            existingBuild.pools.forEach { pool ->
                addChainEdges(pool.memberIndices.sorted(), uf, mergedEdges, n)
            }
        }

        // Apply AI groups as chain edges (O(k) per group)
        aiGroups.forEach { group ->
            val valid = group.filter { it in 0 until n }.distinct().sorted()
            addChainEdges(valid, uf, mergedEdges, n)
            for (i in 0 until valid.size - 1) {
                relations += BalancedPoolRelation(
                    indexA = valid[i],
                    indexB = valid[i + 1],
                    edgeType = EdgeType.LEARNING_CONFUSABLE,
                    evidenceSource = "balanced_ai"
                )
            }
        }

        // Extract and split
        val components = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            val root = uf.find(i)
            components.getOrPut(root) { mutableListOf() }.add(i)
        }

        val result = mutableListOf<BuiltPool>()
        components.values.forEach { component ->
            if (component.size < 2) return@forEach
            if (component.size <= MAX_POOL_SIZE) {
                result.add(BuiltPool(memberIndices = component.sorted()))
            } else {
                result.addAll(
                    splitComponent(component, candidates, mergedEdges, emptySet())
                )
            }
        }
        return BalancedPoolBuild(result, relations.toList())
    }

    /** Build sorted chain edges: (a0,a1), (a1,a2), … — O(k) edges, keeps group connected. */
    private fun addChainEdges(
        sortedIndices: List<Int>,
        uf: UnionFind,
        edges: MutableSet<Pair<Int, Int>>,
        n: Int
    ) {
        for (i in 0 until sortedIndices.size - 1) {
            val a = sortedIndices[i]
            val b = sortedIndices[i + 1]
            if (a in 0 until n && b in 0 until n) {
                uf.union(a, b)
                edges.add(if (a < b) a to b else b to a)
            }
        }
    }

    private fun splitComponent(
        component: List<Int>,
        candidates: List<PoolCandidate>,
        directRefEdges: Set<Pair<Int, Int>>,
        editDistEdges: Set<Pair<Int, Int>>
    ): List<BuiltPool> {
        val allEdges = directRefEdges + editDistEdges
        val componentSet = component.toSet()
        val plan = QualityFirstPoolPlanner.plan(
            allEdges
                .filter { (a, b) -> a in componentSet && b in componentSet }
                .map { (a, b) -> QualityPoolEdge(a.toLong(), b.toLong()) }
        )
        val adjacency = mutableMapOf<Int, MutableSet<Int>>()
        allEdges.forEach { (a, b) ->
            if (a in componentSet && b in componentSet) {
                adjacency.getOrPut(a) { linkedSetOf() }.add(b)
                adjacency.getOrPut(b) { linkedSetOf() }.add(a)
            }
        }
        return plan.pools.map { members ->
            val indices = members.map(Long::toInt)
            val core = indices.maxWithOrNull(
                compareBy<Int> { adjacency[it].orEmpty().size }
                    .thenByDescending { candidates[it].wordId }
            )
            BuiltPool(memberIndices = indices.sorted(), coreIndex = core)
        }
    }

    private fun extractChineseSubstrings(text: String, minLength: Int): List<String> {
        val seen = linkedSetOf<String>()
        // Extract contiguous Chinese character sequences
        // BUG 7 fix: cap at max 3 substrings per run to avoid O(n^3) performance
        // N5 fix: use LinkedHashSet for O(1) dedup instead of O(n) List.contains
        val chineseRuns = Regex("[\\u4e00-\\u9fff]+").findAll(text)
        chineseRuns.forEach { match ->
            val run = match.value
            if (run.length >= minLength) {
                var count = 0
                // Greedily extract from longest to shortest, deduplicating
                for (len in run.length downTo minLength) {
                    if (count >= 3) break
                    for (start in 0..run.length - len) {
                        if (count >= 3) break
                        val substr = run.substring(start, start + len)
                        if (seen.add(substr)) {
                            count++
                        }
                    }
                }
            }
        }
        return seen.toList()
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }

}
