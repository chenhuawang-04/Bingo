package com.xty.englishhelper.ui.screen.dictionary.pool

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.EdgeCluster
import com.xty.englishhelper.domain.model.WordGraph
import com.xty.englishhelper.domain.model.WordGraphCluster
import com.xty.englishhelper.ui.components.pool.clusterColor
import com.xty.englishhelper.ui.components.pool.edgeClusterLegend

/** 词池总览：顶部仪表盘 + 下方词池簇画廊。点卡进入大图并定位该簇；亦可「查看全图」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolOverviewScreen(
    onBack: () -> Unit,
    onOpenGraph: (focusClusterId: Int) -> Unit,
    viewModel: PoolViewerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("词池可视化") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        val graph = state.graph
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("加载失败：${state.error}", color = MaterialTheme.colorScheme.error)
            }

            graph == null || graph.isEmpty -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(
                    "尚无关系数据。\n请先在词典页用「生成词池（质量优先）」整理出词与词之间的关系。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> PoolOverviewContent(
                graph = graph,
                onOpenGraph = onOpenGraph,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

@Composable
private fun PoolOverviewContent(
    graph: WordGraph,
    onOpenGraph: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                DashboardCard(graph)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onOpenGraph(-1) }, modifier = Modifier.fillMaxWidth()) {
                    Text("查看全图（拖动 / 缩放浏览全部词）")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "词池簇 ${graph.clusters.size} 个",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(graph.clusters, key = { it.id }) { cluster ->
            ClusterCard(
                cluster = cluster,
                coreSpelling = graph.nodes[cluster.coreNodeIndex].spelling,
                onClick = { onOpenGraph(cluster.id) }
            )
        }
    }
}

@Composable
private fun DashboardCard(graph: WordGraph) {
    val covered = graph.totalWords - graph.isolatedNodeIndices.size
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("词池簇", graph.clusters.size.toString())
                StatItem("覆盖词", covered.toString())
                StatItem("关系边", graph.totalEdges.toString())
            }
            if (graph.isolatedNodeIndices.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "另有 ${graph.isolatedNodeIndices.size} 个未关联词",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            DistributionBar(graph.clusterDistribution)
            Spacer(Modifier.height(8.dp))
            Legend(graph.clusterDistribution)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 五类关系按边数占比的色条。 */
@Composable
private fun DistributionBar(distribution: Map<EdgeCluster, Int>) {
    val total = distribution.values.sum().coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        edgeClusterLegend.forEach { cluster ->
            val count = distribution[cluster] ?: 0
            if (count > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(count.toFloat() / total)
                        .background(clusterColor(cluster))
                )
            }
        }
    }
}

@Composable
private fun Legend(distribution: Map<EdgeCluster, Int>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        edgeClusterLegend.forEach { cluster ->
            val count = distribution[cluster] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(clusterColor(cluster))
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "${cluster.label}$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClusterCard(
    cluster: WordGraphCluster,
    coreSpelling: String,
    onClick: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                coreSpelling,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${cluster.size} 词",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                edgeClusterLegend.forEach { c ->
                    if ((cluster.relationCounts[c] ?: 0) > 0) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(clusterColor(c))
                        )
                    }
                }
            }
        }
    }
}
