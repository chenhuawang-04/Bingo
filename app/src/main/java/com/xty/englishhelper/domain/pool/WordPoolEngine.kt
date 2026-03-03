package com.xty.englishhelper.domain.pool

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

    fun buildPools(candidates: List<PoolCandidate>): List<BuiltPool> {
        if (candidates.isEmpty()) return emptyList()
        val n = candidates.size
        val uf = UnionFind(n)

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
                }
            }
        }

        // Signal B: Direct cross-reference
        candidates.forEach { c ->
            val allRefSpellings = c.synonymSpellings + c.similarSpellings + c.cognateSpellings
            allRefSpellings.forEach { ref ->
                val normRef = normalize(ref)
                val targets = normalizedLookup[normRef]
                if (targets != null) {
                    targets.forEach { targetIdx ->
                        if (targetIdx != c.index) {
                            uf.union(c.index, targetIdx)
                            val edge = if (c.index < targetIdx) Pair(c.index, targetIdx) else Pair(targetIdx, c.index)
                            directRefEdges.add(edge)
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

        return result
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
    ): List<BuiltPool> {
        val n = candidates.size
        val uf = UnionFind(n)
        val mergedEdges = mutableSetOf<Pair<Int, Int>>()

        // Replay existing pools as chain edges (O(k) per pool)
        existingPools.forEach { pool ->
            val members = pool.memberIndices.sorted()
            addChainEdges(members, uf, mergedEdges, n)
        }

        // Apply AI groups as chain edges (O(k) per group)
        aiGroups.forEach { group ->
            val valid = group.filter { it in 0 until n }.distinct().sorted()
            addChainEdges(valid, uf, mergedEdges, n)
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
        return result
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
        val remaining = component.toMutableSet()
        val result = mutableListOf<BuiltPool>()

        // Build adjacency in component
        val adjacency = mutableMapOf<Int, MutableSet<Int>>()
        for (idx in component) adjacency[idx] = mutableSetOf()

        val allEdges = directRefEdges + editDistEdges
        allEdges.forEach { (a, b) ->
            if (a in remaining && b in remaining) {
                adjacency[a]?.add(b)
                adjacency[b]?.add(a)
            }
        }

        while (remaining.isNotEmpty()) {
            // Calculate connection strength: directRef × 2, editDist × 1
            val strength = mutableMapOf<Int, Int>()
            remaining.forEach { idx ->
                var s = 0
                adjacency[idx]?.forEach { neighbor ->
                    if (neighbor in remaining) {
                        val edge = if (idx < neighbor) Pair(idx, neighbor) else Pair(neighbor, idx)
                        s += if (edge in directRefEdges) 2 else 1
                    }
                }
                strength[idx] = s
            }

            // Pick core: strength descending, then wordId ascending
            val core = remaining.sortedWith(
                compareByDescending<Int> { strength[it] ?: 0 }
                    .thenBy { candidates[it].wordId }
            ).first()

            // Get neighbors sorted by connection strength to core, then wordId ascending
            val neighbors = (adjacency[core] ?: emptySet())
                .filter { it in remaining && it != core }
                .sortedWith(
                    compareByDescending<Int> { strength[it] ?: 0 }
                        .thenBy { candidates[it].wordId }
                )
                .take(MAX_POOL_SIZE - 1)

            val poolMembers = mutableListOf(core)
            poolMembers.addAll(neighbors)

            if (poolMembers.size >= 2) {
                result.add(BuiltPool(memberIndices = poolMembers.sorted(), coreIndex = core))
            } else {
                // No neighbors via edges — fallback: grab closest nodes by wordId
                val fallbackNeighbors = remaining
                    .filter { it != core }
                    .sortedBy { candidates[it].wordId }
                    .take(MAX_POOL_SIZE - 1)
                poolMembers.addAll(fallbackNeighbors)
                if (poolMembers.size >= 2) {
                    result.add(BuiltPool(memberIndices = poolMembers.sorted(), coreIndex = core))
                }
            }

            remaining.removeAll(poolMembers.toSet())
        }

        return result
    }

    private fun extractChineseSubstrings(text: String, minLength: Int): List<String> {
        val result = mutableListOf<String>()
        // Extract contiguous Chinese character sequences
        val chineseRuns = Regex("[\\u4e00-\\u9fff]+").findAll(text)
        chineseRuns.forEach { match ->
            val run = match.value
            if (run.length >= minLength) {
                for (len in minLength..run.length) {
                    for (start in 0..run.length - len) {
                        result.add(run.substring(start, start + len))
                    }
                }
            }
        }
        return result
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

    private class UnionFind(n: Int) {
        val parent = IntArray(n) { it }
        val rank = IntArray(n)

        fun find(x: Int): Int {
            if (parent[x] != x) parent[x] = find(parent[x])
            return parent[x]
        }

        fun union(x: Int, y: Int) {
            val rx = find(x)
            val ry = find(y)
            if (rx == ry) return
            when {
                rank[rx] < rank[ry] -> parent[rx] = ry
                rank[rx] > rank[ry] -> parent[ry] = rx
                else -> {
                    parent[ry] = rx
                    rank[rx]++
                }
            }
        }
    }
}
