package com.xty.englishhelper.domain.model

/**
 * 词典的「关系图」装配结果——可视化词池的底层数据。
 *
 * 节点 = 词典里的全部词（轻量：仅 id + 拼写，释义按需懒加载）；
 * 边 = [com.xty.englishhelper.data.local.entity.WordEdgeEntity] 的轻量 typed 彩色关系；
 * 簇 = 边图的连通分量（即「词池/关系簇」），每簇内可做同心辐射布局。
 *
 * 边的端点用「节点下标」（指向 [nodes]）表示而非 wordId，便于布局/渲染时直接索引扁平数组。
 */
data class WordGraph(
    val nodes: List<WordGraphNode>,
    val edges: List<WordGraphEdge>,
    /** 连通分量（≥2 节点），已按词数降序。下标即 cluster id。 */
    val clusters: List<WordGraphCluster>,
    /** 无任何关系边的孤立词在 [nodes] 中的下标。 */
    val isolatedNodeIndices: List<Int>,
    /** 词典总词数（= [nodes].size）。 */
    val totalWords: Int,
    /** 关系边总数（= [edges].size）。 */
    val totalEdges: Int,
    /** 全部边按 5 大关系类别计数，用于仪表盘分布条。 */
    val clusterDistribution: Map<EdgeCluster, Int>
) {
    val isEmpty: Boolean get() = edges.isEmpty()
}

/** 关系图中的一个节点（轻量）。[clusterId] 为 -1 表示孤立词（无边）。 */
data class WordGraphNode(
    val wordId: Long,
    val spelling: String,
    val clusterId: Int,
    val degree: Int
)

/** 关系图中的一条轻量边；[aIndex]/[bIndex] 指向 [WordGraph.nodes]，完整 AI 依据按 [edgeId] 懒加载。 */
data class WordGraphEdge(
    val edgeId: Long,
    val aIndex: Int,
    val bIndex: Int,
    val type: EdgeType,
    val relationStrength: Int,
    val confidence: Double
)

/** 关系图弹窗按需加载的一条边详情。 */
data class WordGraphEdgeDetail(
    val edgeId: Long,
    val type: EdgeType,
    val relationStrength: Int,
    val confidence: Double,
    val status: String,
    val reason: String?,
    val exampleSentence: String?,
    val register: String?,
    val difficultyCefr: String?,
    val warningNote: String?
)

/**
 * 一个连通分量（词池/关系簇）。
 * @param coreNodeIndex 核心节点（簇内度数最高者）在 [WordGraph.nodes] 的下标，作为辐射布局的圆心。
 * @param nodeIndices   成员节点在 [WordGraph.nodes] 的下标。
 * @param relationCounts 簇内各关系类别的边数（卡片色点 / 聚合点主色用）。
 */
data class WordGraphCluster(
    val id: Int,
    val coreNodeIndex: Int,
    val nodeIndices: List<Int>,
    val relationCounts: Map<EdgeCluster, Int>
) {
    val size: Int get() = nodeIndices.size

    /** 主导关系类别（边数最多者）；空时为 null。 */
    val dominantCluster: EdgeCluster?
        get() = relationCounts.maxByOrNull { it.value }?.key
}
