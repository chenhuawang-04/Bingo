package com.xty.englishhelper.domain.pool

data class QualityPoolEdge(
    val wordIdA: Long,
    val wordIdB: Long
)

data class QualityFirstPoolPlan(
    val pools: List<List<Long>>,
    val connectedComponents: List<Set<Long>>
) {
    val coveredWordIds: Set<Long> = pools.flatten().toSet()
    val oversizedComponentCount: Int = connectedComponents.count { it.size > WordPoolEngine.MAX_POOL_SIZE }
}

/**
 * Builds connected pools from an undirected edge graph without dropping vertices.
 * Pools may share connector vertices when a connected component cannot be partitioned
 * into disjoint connected groups of size 2..[maxPoolSize] (for example, a large star).
 */
object QualityFirstPoolPlanner {

    fun plan(
        edges: Collection<QualityPoolEdge>,
        maxPoolSize: Int = WordPoolEngine.MAX_POOL_SIZE
    ): QualityFirstPoolPlan {
        require(maxPoolSize >= 2) { "maxPoolSize must be at least 2" }

        val adjacency = linkedMapOf<Long, MutableSet<Long>>()
        edges.forEach { edge ->
            if (edge.wordIdA == edge.wordIdB) return@forEach
            adjacency.getOrPut(edge.wordIdA) { linkedSetOf() }.add(edge.wordIdB)
            adjacency.getOrPut(edge.wordIdB) { linkedSetOf() }.add(edge.wordIdA)
        }
        return planAdjacency(adjacency, maxPoolSize)
    }

    fun planAdjacency(
        adjacency: Map<Long, Set<Long>>,
        maxPoolSize: Int = WordPoolEngine.MAX_POOL_SIZE
    ): QualityFirstPoolPlan {
        require(maxPoolSize >= 2) { "maxPoolSize must be at least 2" }
        if (adjacency.isEmpty()) return QualityFirstPoolPlan(emptyList(), emptyList())

        val components = connectedComponents(adjacency)
        val pools = components.flatMap { component ->
            splitConnectedComponent(component, adjacency, maxPoolSize)
        }
        return QualityFirstPoolPlan(pools = pools, connectedComponents = components)
    }

    fun isConnectedPool(
        members: Collection<Long>,
        edges: Collection<QualityPoolEdge>
    ): Boolean {
        val memberSet = members.toSet()
        if (memberSet.size < 2) return false
        val adjacency = memberSet.associateWith { linkedSetOf<Long>() }.toMutableMap()
        edges.forEach { edge ->
            if (edge.wordIdA in memberSet && edge.wordIdB in memberSet && edge.wordIdA != edge.wordIdB) {
                adjacency.getValue(edge.wordIdA).add(edge.wordIdB)
                adjacency.getValue(edge.wordIdB).add(edge.wordIdA)
            }
        }
        val visited = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()
        queue.addLast(memberSet.first())
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            adjacency[current].orEmpty().forEach { neighbor ->
                if (neighbor !in visited) queue.addLast(neighbor)
            }
        }
        return visited == memberSet
    }

    private fun connectedComponents(
        adjacency: Map<Long, Set<Long>>
    ): List<Set<Long>> {
        val unseen = adjacency.keys.toMutableSet()
        val components = mutableListOf<Set<Long>>()
        while (unseen.isNotEmpty()) {
            val start = unseen.minOrNull() ?: break
            val component = linkedSetOf<Long>()
            val queue = ArrayDeque<Long>()
            queue.addLast(start)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (!component.add(current)) continue
                unseen.remove(current)
                adjacency[current].orEmpty().sorted().forEach { neighbor ->
                    if (neighbor !in component) queue.addLast(neighbor)
                }
            }
            components += component
        }
        return components.sortedWith(
            compareByDescending<Set<Long>> { it.size }.thenBy { it.minOrNull() ?: Long.MAX_VALUE }
        )
    }

    private fun splitConnectedComponent(
        component: Set<Long>,
        adjacency: Map<Long, Set<Long>>,
        maxPoolSize: Int
    ): List<List<Long>> {
        if (component.size <= maxPoolSize) return listOf(component.sorted())

        val remaining = component.toMutableSet()
        val pools = mutableListOf<List<Long>>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.sortedWith(nodeComparator(adjacency, emptySet())).first()
            val group = linkedSetOf(seed)
            remaining.remove(seed)

            while (group.size < maxPoolSize) {
                val uncoveredCandidates = group
                    .flatMap { adjacency[it].orEmpty() }
                    .filter { it in remaining }
                    .distinct()

                val next = if (uncoveredCandidates.isNotEmpty()) {
                    uncoveredCandidates.sortedWith(nodeComparator(adjacency, group)).first()
                } else if (group.size == 1) {
                    // A remaining leaf may only connect through an already-covered articulation node.
                    adjacency[seed].orEmpty()
                        .filter { it in component }
                        .sortedWith(nodeComparator(adjacency, group))
                        .firstOrNull()
                } else {
                    null
                } ?: break

                group.add(next)
                remaining.remove(next)
            }

            check(group.size >= 2) { "Connected component produced a singleton pool for word $seed" }
            pools += group.sorted()
        }
        return pools
    }

    private fun nodeComparator(
        adjacency: Map<Long, Set<Long>>,
        currentGroup: Set<Long>
    ): Comparator<Long> = compareByDescending<Long> { candidate ->
        adjacency[candidate].orEmpty().count { it in currentGroup }
    }.thenByDescending { candidate ->
        adjacency[candidate].orEmpty().size
    }.thenBy { it }
}
