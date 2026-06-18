package com.xty.englishhelper.ui.components.pool

import androidx.compose.ui.graphics.Color
import com.xty.englishhelper.domain.model.EdgeCluster
import com.xty.englishhelper.domain.model.EdgeType

/**
 * 词关系边的统一配色与图例文案。原先散落在 StudyContent 的私有调色板，抽出此处供
 * 学习页关联标签、词池可视化（总览仪表盘 / 辐射大图）共用，保证全 App 颜色一致。
 *
 * 五大类用「色系」区分：绿=语义、蓝=形式、橙=词族、青=用法、粉=学习；
 * 同类内各 16 种边用同色系深浅区分。
 */

/** 单条边的精确颜色（16 种）。 */
fun edgeTypeColor(edgeType: EdgeType): Color = when (edgeType) {
    // Semantic cluster - Green family
    EdgeType.SEMANTIC_SYNONYM -> Color(0xFF388E3C)
    EdgeType.SEMANTIC_ANTONYM -> Color(0xFFD32F2F)
    EdgeType.SEMANTIC_OVERLAP -> Color(0xFF66BB6A)
    EdgeType.SEMANTIC_HYPERNYM -> Color(0xFF2E7D32)
    EdgeType.SEMANTIC_HYPONYM -> Color(0xFF81C784)
    // Form cluster - Blue family
    EdgeType.FORM_SPELLING -> Color(0xFF1976D2)
    EdgeType.FORM_HOMOPHONE -> Color(0xFF42A5F5)
    EdgeType.FORM_PRONUNCIATION -> Color(0xFF7B1FA2)
    EdgeType.FORM_MINIMAL_PAIR -> Color(0xFF5C6BC0)
    // Family cluster - Orange family
    EdgeType.FAMILY_INFLECTION -> Color(0xFFE64A19)
    EdgeType.FAMILY_DERIVATION -> Color(0xFFFF7043)
    EdgeType.FAMILY_SAME_ROOT -> Color(0xFFEF6C00)
    // Usage cluster - Teal family
    EdgeType.USAGE_COLLOCATION -> Color(0xFF00897B)
    EdgeType.USAGE_PHRASE -> Color(0xFF26A69A)
    EdgeType.USAGE_PATTERN -> Color(0xFF00695C)
    // Learning cluster - Pink family
    EdgeType.LEARNING_CONFUSABLE -> Color(0xFFC2185B)
    EdgeType.LEARNING_MISUSE_PAIR -> Color(0xFFE91E63)
}

/** 关系大类的代表色（5 种），用于聚合点主色、合批画线、图例。 */
fun clusterColor(cluster: EdgeCluster): Color = when (cluster) {
    EdgeCluster.SEMANTIC -> Color(0xFF388E3C)
    EdgeCluster.FORM -> Color(0xFF1976D2)
    EdgeCluster.FAMILY -> Color(0xFFEF6C00)
    EdgeCluster.USAGE -> Color(0xFF00897B)
    EdgeCluster.LEARNING -> Color(0xFFC2185B)
}

/** 图例顺序固定的 5 大类（含中文标签来自 [EdgeCluster.label]）。 */
val edgeClusterLegend: List<EdgeCluster> = listOf(
    EdgeCluster.SEMANTIC,
    EdgeCluster.FORM,
    EdgeCluster.FAMILY,
    EdgeCluster.USAGE,
    EdgeCluster.LEARNING
)
