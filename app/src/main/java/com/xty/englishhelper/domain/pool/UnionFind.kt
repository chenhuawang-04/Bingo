package com.xty.englishhelper.domain.pool

/**
 * Rank-based Union-Find with path compression.
 * Used by both [WordPoolEngine] (BALANCED build) and
 * [com.xty.englishhelper.data.repository.WordPoolRepositoryImpl] (QUALITY_FIRST pool extraction).
 */
internal class UnionFind(private val capacity: Int) {
    private val parent = IntArray(capacity) { it }
    private val rank = IntArray(capacity)

    fun find(x: Int): Int {
        if (parent[x] != x) parent[x] = find(parent[x])
        return parent[x]
    }

    fun union(x: Int, y: Int): Boolean {
        val rx = find(x)
        val ry = find(y)
        if (rx == ry) return false
        when {
            rank[rx] < rank[ry] -> parent[rx] = ry
            rank[rx] > rank[ry] -> parent[ry] = rx
            else -> {
                parent[ry] = rx
                rank[rx]++
            }
        }
        return true
    }
}

/**
 * Mutable-map-based Union-Find with rank and path compression.
 * Used for pool extraction where node keys are arbitrary [Long] IDs (word IDs).
 */
internal class MutableUnionFind {
    private val parent = mutableMapOf<Long, Long>()
    private val rank = mutableMapOf<Long, Int>()

    fun add(x: Long) {
        parent.putIfAbsent(x, x)
        rank.putIfAbsent(x, 0)
    }

    fun find(x: Long): Long {
        val r = parent[x] ?: return x
        if (r != x) {
            parent[x] = find(r)
        }
        return parent[x] ?: x
    }

    fun union(a: Long, b: Long) {
        add(a); add(b)
        val ra = find(a)
        val rb = find(b)
        if (ra == rb) return
        val rankA = rank[ra] ?: 0
        val rankB = rank[rb] ?: 0
        when {
            rankA < rankB -> parent[ra] = rb
            rankA > rankB -> parent[rb] = ra
            else -> {
                parent[rb] = ra
                rank[ra] = rankA + 1
            }
        }
    }
}
