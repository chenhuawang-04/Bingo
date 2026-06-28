package com.xty.englishhelper.ui.screen.dictionary.pool

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.xty.englishhelper.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.WordGraphEdgeDetail
import com.xty.englishhelper.domain.model.WordGraphEdge
import com.xty.englishhelper.ui.components.pool.clusterColor
import com.xty.englishhelper.ui.components.pool.edgeClusterLegend
import com.xty.englishhelper.ui.components.pool.edgeTypeColor
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 关系大图渲染页：**单 [Canvas] + 视口裁剪 + 三级 LOD + 按色合批**，承载「展示所有词」的同时
 * 把每帧绘制量压到「屏内可见」量级，与词典总词数解耦。
 *
 * 性能要点：
 *  - 坐标全部预算在 [PoolGraphLayout] 的扁平数组里；绘制循环零对象分配（[Color] 为内联值类）。
 *  - 视口裁剪：经 [PoolGraphLayout.forEachNodeInWorldRect] 走空间网格，只遍历屏内单元 → O(屏内节点)。
 *  - 三级 LOD：远景「一簇一聚合点」（绘制量= 簇数，与词数无关）；中景「点+按 5 大类合批的边」；
 *    近景再叠词标签（标签仅在放大时出现，故被可见节点数天然限幅）。
 *  - 边合批：5 条 [Path]（按关系大类）一帧仅 5 次 `drawPath`，而非每边一次。
 *  - 命中测试同样走网格（[PoolGraphLayout.nearestNodeAt] / [nearestClusterAt]），O(屏内)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolGraphScreen(
    onBack: () -> Unit,
    onWordClick: (wordId: Long) -> Unit,
    viewModel: PoolGraphViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    AppTopBarEffect(
        title = { Text(stringResource(R.string.pool_graph_title)) },
        navigationIcon = { AppTopBarBackButton(onBack) },
        actions = {
            val incl = state.includeIsolated
            IconButton(onClick = { viewModel.setIncludeIsolated(!incl) }) {
                Icon(
                    imageVector = if (incl) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (incl) stringResource(R.string.pool_graph_hide_unlinked) else stringResource(R.string.pool_graph_show_unlinked)
                )
            }
        }
    )

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val layout = state.layout
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(stringResource(R.string.pool_graph_load_failed, state.error ?: ""), color = MaterialTheme.colorScheme.error)
                }

                layout == null || layout.graph.isEmpty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        stringResource(R.string.pool_graph_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> PoolGraphContent(
                    layout = layout,
                    state = state,
                    onWordClick = onWordClick,
                    onSelectNode = viewModel::selectNode,
                    onClearSelection = viewModel::clearSelection,
                    onSelectEdge = viewModel::selectEdge,
                    onClearEdge = viewModel::clearEdge
                )
            }
        }
    }
}

private const val FAR_SCALE = 0.06f       // 低于此缩放 → 远景 LOD（一簇一聚合点）。刻意设得很低，
                                          // 使「适配全图」通常仍落在中景（看到节点云）而非只剩一个聚合点。
private const val LABEL_SCALE = 0.6f      // 高于此缩放 → 近景 LOD（绘制词标签）
private const val LABEL_MAX = 400         // 单帧标签上限（保护近-中景边界的极端可见量）
private const val EDGE_VISIBLE_BUDGET = 600  // 屏内可见节点 > 此数 → 本帧跳过画边。超大连通分量整片可见时，
                                             // 逐边连线是 O(E) 且必成乱麻；跳过既快又清爽（消除中景缩放时的卡死/ANR）。
private const val NODE_RENDER_CAP = 5000     // 单帧最多绘制的节点数（保护超大图；命中测试走网格不受影响）。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoolGraphContent(
    layout: PoolGraphLayout,
    state: PoolGraphUiState,
    onWordClick: (Long) -> Unit,
    onSelectNode: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onSelectEdge: (Int) -> Unit,
    onClearEdge: () -> Unit
) {
    // ── 视图变换（屏 = 世界 * scale + translation）。仅 Canvas 绘制 / 手势读写，故不触发重组。 ──
    var scale by remember(layout) { mutableStateOf(1f) }
    var translation by remember(layout) { mutableStateOf(Offset.Zero) }
    var minScale by remember(layout) { mutableStateOf(0.02f) }
    var maxScale by remember(layout) { mutableStateOf(6f) }
    var canvasSize by remember(layout) { mutableStateOf(IntSize.Zero) }
    var consumedFocus by remember { mutableStateOf(false) }

    // ── 绘制用复用缓冲（keyed on layout，零每帧分配） ──
    val n = layout.graph.nodes.size
    val visBuf = remember(layout) { IntArray(n) }
    val visibleGen = remember(layout) { IntArray(n) }
    val genHolder = remember(layout) { intArrayOf(0) }
    val edgePaths = remember(layout) { Array(5) { Path() } }

    // 主题色（合成期读取一次）
    val isolatedColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val clusterColors = remember { edgeClusterLegend.map { clusterColor(it) } }
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val labelTextSize = 30f
    val labelPaint = remember(labelColorArgb) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColorArgb
            textSize = labelTextSize
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    // ── 视图操作（局部函数，可写上面的 state 委托） ──
    fun fitWorld() {
        val w = canvasSize.width; val h = canvasSize.height
        if (w == 0 || h == 0) return
        val s = (min(w / layout.worldWidth, h / layout.worldHeight) * 0.92f).coerceIn(minScale, maxScale)
        val cxw = (layout.minX + layout.maxX) / 2f
        val cyw = (layout.minY + layout.maxY) / 2f
        scale = s
        translation = Offset(w / 2f - cxw * s, h / 2f - cyw * s)
    }

    fun focusCluster(id: Int) {
        val w = canvasSize.width; val h = canvasSize.height
        if (w == 0 || h == 0 || id !in 0 until layout.clusterCount) return
        val r = layout.clusterBoundingR[id].coerceAtLeast(1f)
        val s = (min(w, h) / (2f * r * 1.15f)).coerceIn(minScale, maxScale)
        scale = s
        translation = Offset(w / 2f - layout.clusterCenterX[id] * s, h / 2f - layout.clusterCenterY[id] * s)
    }

    fun zoomBy(factor: Float) {
        val w = canvasSize.width; val h = canvasSize.height
        if (w == 0 || h == 0) return
        val c = Offset(w / 2f, h / 2f)
        val old = scale
        val ns = (old * factor).coerceIn(minScale, maxScale)
        translation = c - (c - translation) * (ns / old)
        scale = ns
    }

    // 测量完成 / 布局切换后重算缩放边界并 fit（首次若有定位簇则聚焦该簇）。
    LaunchedEffect(layout, canvasSize) {
        val w = canvasSize.width; val h = canvasSize.height
        if (w == 0 || h == 0) return@LaunchedEffect
        val fit = min(w / layout.worldWidth, h / layout.worldHeight)
        minScale = (fit * 0.5f).coerceIn(0.004f, 1f)
        maxScale = max(fit * 14f, 5f)
        val focusId = state.initialFocusClusterId
        if (!consumedFocus && focusId in 0 until layout.clusterCount) {
            focusCluster(focusId)
        } else {
            fitWorld()
            // 若整图太大、适配后仍落在远景（只剩聚合点），改为放大到中景并对准最大簇，
            // 保证首屏看到的是可读的节点云而非一个孤点（「回到全图」按钮仍走真实适配）。
            if (scale < FAR_SCALE * 1.2f && layout.clusterCount > 0) {
                val legible = (FAR_SCALE * 1.6f).coerceIn(minScale, maxScale)
                scale = legible
                translation = Offset(
                    w / 2f - layout.clusterCenterX[0] * legible,
                    h / 2f - layout.clusterCenterY[0] * legible
                )
            }
        }
        consumedFocus = true
    }

    val nodeX = layout.nodeX
    val nodeY = layout.nodeY
    val nodes = layout.graph.nodes
    val edges = layout.graph.edges
    val nodeEdges = layout.nodeEdges
    val selectedIndex = state.selectedNode?.nodeIndex

    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(layout) {
                    detectTapGestures { pos ->
                        val sc = scale
                        val wx = (pos.x - translation.x) / sc
                        val wy = (pos.y - translation.y) / sc
                        if (sc < FAR_SCALE) {
                            val c = layout.nearestClusterAt(wx, wy)
                            if (c >= 0) focusCluster(c)
                        } else {
                            val tol = (28f / sc).coerceAtLeast(PoolGraphLayout.nodeRadiusWorld * 1.4f)
                            val hit = layout.nearestNodeAt(wx, wy, tol)
                            if (hit >= 0) {
                                onSelectNode(hit)
                                // 把命中节点移到屏幕上方，避免被底部弹层遮挡
                                val w = canvasSize.width; val h = canvasSize.height
                                if (w > 0 && h > 0) {
                                    translation = Offset(w / 2f - nodeX[hit] * sc, h * 0.3f - nodeY[hit] * sc)
                                }
                            } else {
                                onClearSelection()
                            }
                        }
                    }
                }
                .pointerInput(layout) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val old = scale
                        val ns = (old * zoom).coerceIn(minScale, maxScale)
                        var tr = translation + pan
                        tr = centroid - (centroid - tr) * (ns / old)
                        translation = tr
                        scale = ns
                    }
                }
        ) {
            val sc = scale
            val trx = translation.x
            val tryy = translation.y
            val w = size.width
            val h = size.height

            if (sc < FAR_SCALE) {
                // ── 远景 LOD：一簇一聚合点 ──
                val cc = layout.clusterCount
                for (c in 0 until cc) {
                    val x = layout.clusterCenterX[c] * sc + trx
                    val y = layout.clusterCenterY[c] * sc + tryy
                    if (x < -60f || y < -60f || x > w + 60f || y > h + 60f) continue
                    val r = (layout.clusterDotR[c] * sc).coerceAtLeast(3f)
                    drawCircle(Color(layout.clusterColorArgb[c]), radius = r, center = Offset(x, y))
                }
            } else {
                // ── 中/近景 LOD ──
                val left = -trx / sc
                val top = -tryy / sc
                val right = (w - trx) / sc
                val bottom = (h - tryy) / sc

                // pass 1：收集可见节点（gen 戳标记，免清零）。收集量封顶 NODE_RENDER_CAP；
                // gen 仍对所有可见节点打标（成本 O(可见)、用于画边去重），仅 visBuf 写入封顶。
                val gen = ++genHolder[0]
                var visCount = 0
                layout.forEachNodeInWorldRect(left, top, right, bottom) { i ->
                    visibleGen[i] = gen
                    if (visCount < NODE_RENDER_CAP && visCount < visBuf.size) { visBuf[visCount] = i; visCount++ }
                }

                // pass 2：可见节点的边 → 按 5 大类合批进 Path（每边恰画一次）。
                // 仅在屏内节点不太多时画边：整片可见时逐边连线 O(E) 会卡死，且必成乱麻。
                var k = 0
                if (visCount <= EDGE_VISIBLE_BUDGET) {
                    for (p in edgePaths) p.rewind()
                    while (k < visCount) {
                        val i = visBuf[k]; k++
                        val incident = nodeEdges[i]
                        var j = 0
                        while (j < incident.size) {
                            val eIdx = incident[j]; j++
                            val e = edges[eIdx]
                            val a = e.aIndex; val b = e.bIndex
                            // 去重：edge 由 a 端绘制；i==b 且 a 也可见时跳过（a 会画）
                            if (i == b && visibleGen[a] == gen) continue
                            val ci = e.type.cluster.ordinal
                            val path = edgePaths[ci]
                            path.moveTo(nodeX[a] * sc + trx, nodeY[a] * sc + tryy)
                            path.lineTo(nodeX[b] * sc + trx, nodeY[b] * sc + tryy)
                        }
                    }
                    val edgeStroke = Stroke(width = 2f, cap = StrokeCap.Round)
                    for (ci in 0 until 5) {
                        drawPath(edgePaths[ci], color = clusterColors[ci].copy(alpha = 0.45f), style = edgeStroke)
                    }
                }

                // pass 3：可见节点
                val nodeR = (PoolGraphLayout.nodeRadiusWorld * sc).coerceIn(2f, 20f)
                k = 0
                while (k < visCount) {
                    val i = visBuf[k]; k++
                    val cid = nodes[i].clusterId
                    val col = if (cid >= 0) Color(layout.clusterColorArgb[cid]) else isolatedColor
                    drawCircle(col, radius = nodeR, center = Offset(nodeX[i] * sc + trx, nodeY[i] * sc + tryy))
                }

                // pass 4：近景标签（放大时才画 → 可见量天然有限；再加帧上限兜底）
                if (sc >= LABEL_SCALE && visCount <= LABEL_MAX) {
                    val nc = drawContext.canvas.nativeCanvas
                    val dy = nodeR + labelTextSize
                    k = 0
                    while (k < visCount) {
                        val i = visBuf[k]; k++
                        nc.drawText(nodes[i].spelling, nodeX[i] * sc + trx, nodeY[i] * sc + tryy + dy, labelPaint)
                    }
                }

                // 选中高亮：精确边色加粗 + 邻居描环 + 选中点强调
                if (selectedIndex != null && selectedIndex in nodes.indices) {
                    val sx = nodeX[selectedIndex] * sc + trx
                    val sy = nodeY[selectedIndex] * sc + tryy
                    val incident = nodeEdges[selectedIndex]
                    var j = 0
                    while (j < incident.size) {
                        val e = edges[incident[j]]; j++
                        val col = edgeTypeColor(e.type)
                        val ax = nodeX[e.aIndex] * sc + trx
                        val ay = nodeY[e.aIndex] * sc + tryy
                        val bx = nodeX[e.bIndex] * sc + trx
                        val by = nodeY[e.bIndex] * sc + tryy
                        drawLine(col, Offset(ax, ay), Offset(bx, by), strokeWidth = 4f, cap = StrokeCap.Round)
                        val other = if (e.aIndex == selectedIndex) e.bIndex else e.aIndex
                        drawCircle(
                            col,
                            radius = nodeR + 3f,
                            center = Offset(nodeX[other] * sc + trx, nodeY[other] * sc + tryy),
                            style = Stroke(width = 2.5f)
                        )
                    }
                    drawCircle(onPrimaryColor, radius = nodeR + 4f, center = Offset(sx, sy))
                    drawCircle(primaryColor, radius = nodeR + 1.5f, center = Offset(sx, sy))
                }
            }
        }

        // 图例（左上角悬浮）
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                edgeClusterLegend.forEachIndexed { idx, cluster ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(clusterColors[idx]))
                        Spacer(Modifier.size(6.dp))
                        Text(cluster.label, style = MaterialTheme.typography.labelSmall)
                    }
                    if (idx != edgeClusterLegend.lastIndex) Spacer(Modifier.height(3.dp))
                }
            }
        }

        // 缩放控制（右下角）
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ZoomButton(Icons.Filled.Add, stringResource(R.string.pool_graph_zoom_in)) { zoomBy(1.6f) }
            ZoomButton(Icons.Filled.Remove, stringResource(R.string.pool_graph_zoom_out)) { zoomBy(0.62f) }
            ZoomButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.pool_graph_fit_all), rotate = true) { fitWorld() }
        }
    }

    // ── 选中词：底部弹层（释义懒加载 + 关系列表） ──
    val selected = state.selectedNode
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onClearSelection,
            sheetState = sheetState
        ) {
            NodeSheetContent(
                selected = selected,
                onWordClick = { onWordClick(selected.wordId) },
                onRelationClick = onSelectEdge
            )
        }
    }

    // ── 关系边详情：对话框 ──
    val selectedEdge = state.selectedEdge
    val edgeIdx = selectedEdge?.edgeIndex ?: -1
    if (selectedEdge != null && edgeIdx in edges.indices) {
        EdgeDetailDialog(
            edge = edges[edgeIdx],
            detail = selectedEdge.detail,
            aSpelling = nodes[edges[edgeIdx].aIndex].spelling,
            bSpelling = nodes[edges[edgeIdx].bIndex].spelling,
            isLoading = selectedEdge.isLoading,
            error = selectedEdge.error,
            onDismiss = onClearEdge
        )
    }
}

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    rotate: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 3.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = desc,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = if (rotate) Modifier.size(20.dp) else Modifier
            )
        }
    }
}

@Composable
private fun NodeSheetContent(
    selected: SelectedNodeState,
    onWordClick: () -> Unit,
    onRelationClick: (Int) -> Unit
) {
    val detail = selected.detail
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(selected.spelling, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (detail != null && detail.phonetic.isNotBlank()) {
            Text(detail.phonetic, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))

        // 释义（懒加载）
        when {
            detail == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.pool_graph_loading_def), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            detail.meanings.isEmpty() -> Text(
                stringResource(R.string.pool_graph_no_def),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                detail.meanings.take(5).forEach { m ->
                    Row {
                        if (m.pos.isNotBlank()) {
                            Text(
                                m.pos,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        Text(m.definition, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onWordClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pool_graph_view_detail))
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.pool_graph_relation_count, selected.relations.size),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(selected.relations, key = { it.edgeIndex }) { rel ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onRelationClick(rel.edgeIndex) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(edgeTypeColor(rel.type)))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        rel.otherSpelling,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        rel.type.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = edgeTypeColor(rel.type)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.pool_graph_strength, rel.relationStrength.toString()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EdgeDetailDialog(
    edge: WordGraphEdge,
    detail: WordGraphEdgeDetail?,
    aSpelling: String,
    bSpelling: String,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    val relationStrength = detail?.relationStrength ?: edge.relationStrength
    val confidence = detail?.confidence ?: edge.confidence
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(edgeTypeColor(edge.type)))
                Spacer(Modifier.size(8.dp))
                Text(edge.type.label, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "$aSpelling  ↔  $bSpelling",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                DetailLine(stringResource(R.string.pool_graph_relation_strength), relationStrength.toString())
                DetailLine(stringResource(R.string.pool_graph_confidence), String.format(Locale.US, "%.2f", confidence))
                when {
                    isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.pool_graph_loading_def),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    detail != null -> {
                        detail.difficultyCefr?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.pool_graph_difficulty), it) }
                        detail.register?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.pool_graph_register), it) }
                        detail.reason?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.pool_graph_reason), it) }
                        detail.exampleSentence?.takeIf { it.isNotBlank() }?.let { DetailLine(stringResource(R.string.pool_graph_example), it) }
                        detail.warningNote?.takeIf { it.isNotBlank() }?.let {
                            DetailLine(stringResource(R.string.pool_graph_caution), it, valueColor = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
