package com.xty.englishhelper.ui.screen.dictionary.pool

import com.xty.englishhelper.domain.model.WordGraph
import com.xty.englishhelper.ui.components.pool.clusterColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 关系大图的**布局引擎**（纯计算，无 Compose 依赖；由 ViewModel 在后台线程跑一次后缓存）。
 *
 * 设计要点（对应「最高性能」要求）：
 *  - 坐标存在**扁平 [FloatArray]**（1:1 缩放下即像素），与节点下标对齐，渲染时零对象遍历。
 *  - 每个连通分量按到核心的 **BFS 层级**做**同心辐射树**布局：核心居中、第 d 层落在第 d 圈，
 *    子节点落在父节点的角度楔形内 → 形成「辐射套辐射」。每层半径按该层节点数膨胀以杜绝重叠
 *    （故可承载远大于 15 的分量，不怕「超多词汇」挤成一团）。
 *  - 分量按外接半径**行包裹平铺**成一片「星系群」；孤立词在外围网格。
 *  - 构建**空间网格索引**：视口裁剪与点击命中都做到 O(屏内数量)。
 *  - 预算每簇**聚合点**（质心/半径/主色）供远景 LOD「一簇一个点」绘制，绘制量与总词数解耦。
 */
class PoolGraphLayout private constructor(
    val graph: WordGraph,
    /** 每个节点的世界坐标（下标对齐 [WordGraph.nodes]）。 */
    val nodeX: FloatArray,
    val nodeY: FloatArray,
    /** 每个节点关联的边下标（指向 [WordGraph.edges]），供从可见节点收集可见边。 */
    val nodeEdges: Array<IntArray>,
    /** 每个簇的中心（= 核心位置）/ 外接半径 / 远景聚合点半径 / 主色 ARGB（下标 = cluster id）。 */
    val clusterCenterX: FloatArray,
    val clusterCenterY: FloatArray,
    val clusterBoundingR: FloatArray,
    val clusterDotR: FloatArray,
    val clusterColorArgb: IntArray,
    /** 已绘制节点的世界包围盒（用于初始 fit 与平移夹取）。 */
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val includeIsolated: Boolean,
    private val cellSize: Float,
    private val grid: HashMap<Long, IntArray>
) {
    val worldWidth: Float get() = (maxX - minX).coerceAtLeast(1f)
    val worldHeight: Float get() = (maxY - minY).coerceAtLeast(1f)
    val clusterCount: Int get() = clusterCenterX.size

    /** 遍历与给定世界矩形相交的网格单元内的所有节点（视口裁剪用）。 */
    inline fun forEachNodeInWorldRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        action: (nodeIndex: Int) -> Unit
    ) {
        val cs = cellSizePublic
        val cx0 = floor(left / cs).toInt()
        val cx1 = floor(right / cs).toInt()
        val cy0 = floor(top / cs).toInt()
        val cy1 = floor(bottom / cs).toInt()
        var cx = cx0
        while (cx <= cx1) {
            var cy = cy0
            while (cy <= cy1) {
                val bucket = gridPublic[cellKey(cx, cy)]
                if (bucket != null) {
                    var i = 0
                    while (i < bucket.size) {
                        action(bucket[i]); i++
                    }
                }
                cy++
            }
            cx++
        }
    }

    /** 命中测试：返回离 (wx,wy) 最近且在 [maxDistWorld] 内的节点下标，无则 -1。 */
    fun nearestNodeAt(wx: Float, wy: Float, maxDistWorld: Float): Int {
        var best = -1
        var bestSq = maxDistWorld * maxDistWorld
        forEachNodeInWorldRect(wx - maxDistWorld, wy - maxDistWorld, wx + maxDistWorld, wy + maxDistWorld) { i ->
            val dx = nodeX[i] - wx
            val dy = nodeY[i] - wy
            val d = dx * dx + dy * dy
            if (d <= bestSq) {
                bestSq = d
                best = i
            }
        }
        return best
    }

    /** 远景：返回 (wx,wy) 命中的簇（聚合点）下标，无则 -1。 */
    fun nearestClusterAt(wx: Float, wy: Float): Int {
        var best = -1
        var bestSq = Float.MAX_VALUE
        for (c in 0 until clusterCount) {
            val dx = clusterCenterX[c] - wx
            val dy = clusterCenterY[c] - wy
            val d = dx * dx + dy * dy
            val rr = clusterBoundingR[c] * clusterBoundingR[c]
            if (d <= rr && d < bestSq) {
                bestSq = d
                best = c
            }
        }
        return best
    }

    // 暴露给 inline 函数访问（inline 不能触碰 private 成员）。
    @PublishedApi internal val cellSizePublic: Float get() = cellSize
    @PublishedApi internal val gridPublic: HashMap<Long, IntArray> get() = grid

    companion object {
        private const val NODE_RADIUS = 8f
        private const val RING_GAP = 88f          // 同心圈间距
        private const val MIN_ARC = 46f           // 同一圈上相邻节点的最小弧长（防重叠）
        private const val LABEL_PAD = 26f         // 外接半径预留给标签的余量
        private const val CLUSTER_GAP = 72f       // 簇间距
        private const val ISOLATED_GAP = 44f      // 孤立词网格间距
        private const val CELL_SIZE = 150f        // 空间网格单元

        private const val TWO_PI = 2.0 * Math.PI

        @PublishedApi
        internal fun cellKey(cx: Int, cy: Int): Long =
            (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)

        /** 构建布局。应在后台线程调用（O(V+E)）。 */
        fun build(graph: WordGraph, includeIsolated: Boolean): PoolGraphLayout {
            val n = graph.nodes.size
            val nodeX = FloatArray(n)
            val nodeY = FloatArray(n)

            // ── 全局邻接 + 每节点边下标 ──
            val neighbors = Array(n) { ArrayList<Int>() }
            val edgesOf = Array(n) { ArrayList<Int>() }
            graph.edges.forEachIndexed { idx, e ->
                neighbors[e.aIndex].add(e.bIndex)
                neighbors[e.bIndex].add(e.aIndex)
                edgesOf[e.aIndex].add(idx)
                edgesOf[e.bIndex].add(idx)
            }
            val nodeEdges = Array(n) { edgesOf[it].toIntArray() }

            val clusterCount = graph.clusters.size
            val clusterCenterX = FloatArray(clusterCount)
            val clusterCenterY = FloatArray(clusterCount)
            val clusterBoundingR = FloatArray(clusterCount)
            val clusterDotR = FloatArray(clusterCount)
            val clusterColorArgb = IntArray(clusterCount)

            // 复用的全局临时数组（按簇成员重置）。
            val depth = IntArray(n)
            val rangeStart = DoubleArray(n)
            val rangeEnd = DoubleArray(n)
            val leaf = IntArray(n)
            val visited = BooleanArray(n)

            // ── 第一遍：每簇本地辐射树布局（核心在原点），算出外接半径 ──
            graph.clusters.forEach { cluster ->
                val core = cluster.coreNodeIndex
                val members = cluster.nodeIndices

                // 重置临时态
                members.forEach { visited[it] = false; depth[it] = 0; leaf[it] = 0 }

                // BFS（生成树）
                val bfsOrder = ArrayList<Int>(members.size)
                val children = HashMap<Int, ArrayList<Int>>(members.size * 2)
                val queue = ArrayDeque<Int>()
                visited[core] = true
                depth[core] = 0
                queue.addLast(core)
                var maxDepth = 0
                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    bfsOrder.add(cur)
                    for (nb in neighbors[cur]) {
                        if (!visited[nb]) {
                            visited[nb] = true
                            depth[nb] = depth[cur] + 1
                            if (depth[nb] > maxDepth) maxDepth = depth[nb]
                            children.getOrPut(cur) { ArrayList() }.add(nb)
                            queue.addLast(nb)
                        }
                    }
                }

                // 每层节点数 → 每层半径（按需膨胀防重叠）
                val countAtDepth = IntArray(maxDepth + 1)
                bfsOrder.forEach { countAtDepth[depth[it]]++ }
                val depthRadius = DoubleArray(maxDepth + 1)
                for (d in 0..maxDepth) {
                    val byGap = d * RING_GAP.toDouble()
                    val byArc = countAtDepth[d] * MIN_ARC / TWO_PI
                    depthRadius[d] = max(byGap, byArc)
                }

                // 叶子计数（反向 BFS：子先于父）
                for (k in bfsOrder.indices.reversed()) {
                    val node = bfsOrder[k]
                    if (leaf[node] == 0) leaf[node] = 1
                }
                // 累加到父（再走一遍反向，确保父在子之后累加）
                for (k in bfsOrder.indices.reversed()) {
                    val node = bfsOrder[k]
                    val kids = children[node] ?: continue
                    var sum = 0
                    for (c in kids) sum += leaf[c]
                    if (sum > 0) leaf[node] = sum
                }

                // 角度楔形分配 + 落位（正向 BFS：父先于子）
                rangeStart[core] = 0.0
                rangeEnd[core] = TWO_PI
                var boundingR = 0.0
                for (node in bfsOrder) {
                    val mid = (rangeStart[node] + rangeEnd[node]) / 2.0
                    val r = depthRadius[depth[node]]
                    val lx = (r * cos(mid)).toFloat()
                    val ly = (r * sin(mid)).toFloat()
                    nodeX[node] = lx
                    nodeY[node] = ly
                    val dist = sqrt(lx * lx + ly * ly) + NODE_RADIUS + LABEL_PAD
                    if (dist > boundingR) boundingR = dist.toDouble()

                    val kids = children[node] ?: continue
                    var totalLeaf = 0
                    for (c in kids) totalLeaf += leaf[c]
                    if (totalLeaf <= 0) continue
                    val span = rangeEnd[node] - rangeStart[node]
                    var cursor = rangeStart[node]
                    for (c in kids) {
                        val w = span * leaf[c] / totalLeaf
                        rangeStart[c] = cursor
                        rangeEnd[c] = cursor + w
                        cursor += w
                    }
                }

                val cid = cluster.id
                clusterBoundingR[cid] = boundingR.toFloat().coerceAtLeast(RING_GAP)
                clusterDotR[cid] = (10.0 + 4.0 * sqrt(cluster.size.toDouble())).toFloat()
                clusterColorArgb[cid] = (cluster.dominantCluster?.let { clusterColor(it) }
                    ?: Color(0xFF9E9E9E)).toArgb()
            }

            // ── 行包裹平铺 ──
            val isolatedCount = if (includeIsolated) graph.isolatedNodeIndices.size else 0
            var totalArea = 0.0
            for (c in 0 until clusterCount) {
                val d = 2.0 * clusterBoundingR[c]
                totalArea += d * d
            }
            totalArea += isolatedCount * (ISOLATED_GAP.toDouble() * ISOLATED_GAP)
            var maxClusterDiameter = 0f
            for (c in 0 until clusterCount) maxClusterDiameter = max(maxClusterDiameter, 2f * clusterBoundingR[c])
            val rowWidth = max(sqrt(totalArea).toFloat(), maxClusterDiameter).coerceAtLeast(ISOLATED_GAP)

            // 按外接半径降序平铺
            val order = (0 until clusterCount).sortedByDescending { clusterBoundingR[it] }
            var cursorX = 0f
            var rowY = 0f
            var rowH = 0f
            for (c in order) {
                val diameter = 2f * clusterBoundingR[c]
                if (cursorX > 0f && cursorX + diameter > rowWidth) {
                    rowY += rowH + CLUSTER_GAP
                    cursorX = 0f
                    rowH = 0f
                }
                clusterCenterX[c] = cursorX + clusterBoundingR[c]
                clusterCenterY[c] = rowY + clusterBoundingR[c]
                cursorX += diameter + CLUSTER_GAP
                if (diameter > rowH) rowH = diameter
            }
            val clustersBottom = rowY + rowH

            // 把本地坐标偏移到簇中心（核心本地为原点 → 落在簇中心）
            graph.clusters.forEach { cluster ->
                val cxOff = clusterCenterX[cluster.id]
                val cyOff = clusterCenterY[cluster.id]
                for (i in cluster.nodeIndices) {
                    nodeX[i] += cxOff
                    nodeY[i] += cyOff
                }
            }

            // ── 孤立词网格（外围） ──
            if (includeIsolated && graph.isolatedNodeIndices.isNotEmpty()) {
                val isoStartY = clustersBottom + CLUSTER_GAP * 2f
                val cols = max(1, floor(rowWidth / ISOLATED_GAP).toInt())
                graph.isolatedNodeIndices.forEachIndexed { k, nodeIdx ->
                    val col = k % cols
                    val row = k / cols
                    nodeX[nodeIdx] = col * ISOLATED_GAP + ISOLATED_GAP / 2f
                    nodeY[nodeIdx] = isoStartY + row * ISOLATED_GAP + ISOLATED_GAP / 2f
                }
            }

            // ── 包围盒（仅已绘制节点） ──
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            fun expand(i: Int) {
                val x = nodeX[i]; val y = nodeY[i]
                if (x - NODE_RADIUS < minX) minX = x - NODE_RADIUS
                if (y - NODE_RADIUS < minY) minY = y - NODE_RADIUS
                if (x + NODE_RADIUS > maxX) maxX = x + NODE_RADIUS
                if (y + NODE_RADIUS > maxY) maxY = y + NODE_RADIUS
            }
            graph.clusters.forEach { it.nodeIndices.forEach(::expand) }
            if (includeIsolated) graph.isolatedNodeIndices.forEach(::expand)
            if (minX > maxX) { minX = 0f; minY = 0f; maxX = 1f; maxY = 1f }

            // ── 空间网格索引 ──
            val buckets = HashMap<Long, ArrayList<Int>>()
            fun bucketize(i: Int) {
                val key = cellKey(floor(nodeX[i] / CELL_SIZE).toInt(), floor(nodeY[i] / CELL_SIZE).toInt())
                buckets.getOrPut(key) { ArrayList() }.add(i)
            }
            graph.clusters.forEach { it.nodeIndices.forEach(::bucketize) }
            if (includeIsolated) graph.isolatedNodeIndices.forEach(::bucketize)
            val grid = HashMap<Long, IntArray>(buckets.size * 2)
            buckets.forEach { (k, v) -> grid[k] = v.toIntArray() }

            return PoolGraphLayout(
                graph = graph,
                nodeX = nodeX,
                nodeY = nodeY,
                nodeEdges = nodeEdges,
                clusterCenterX = clusterCenterX,
                clusterCenterY = clusterCenterY,
                clusterBoundingR = clusterBoundingR,
                clusterDotR = clusterDotR,
                clusterColorArgb = clusterColorArgb,
                minX = minX,
                minY = minY,
                maxX = maxX,
                maxY = maxY,
                includeIsolated = includeIsolated,
                cellSize = CELL_SIZE,
                grid = grid
            )
        }

        /** 节点绘制半径（世界单位），供渲染层共享。 */
        const val nodeRadiusWorld: Float = NODE_RADIUS
    }
}
