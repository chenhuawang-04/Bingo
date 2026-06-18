package com.xty.englishhelper.ui.screen.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.ui.components.pool.edgeTypeColor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 阶段D：当前词的迷你「中心辐射星图」（hub-and-spokes）。
 * 中心是一个主色辐射枢纽（当前词的大字标题在其正上方，故枢纽不再重复文字），
 * 周围按关系类型上色的节点辐射排布（复用词池可视化的 [edgeTypeColor]）。
 * 每次换词以 0→1 动画「辐射展开」，作为记忆视觉钩子。
 */
@Composable
fun WordConstellation(
    nodes: List<WordEdgePreview>,
    modifier: Modifier = Modifier
) {
    if (nodes.isEmpty()) return
    val display = remember(nodes) { nodes.take(8) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val labelPx = with(density) { 12.dp.toPx() }

    // 以首个节点拼写作为「换词」信号触发辐射动画（每词的节点集合不同）。
    val animKey = display.firstOrNull()?.spelling ?: ""
    val anim = remember(animKey) { Animatable(0f) }
    LaunchedEffect(animKey) {
        anim.snapTo(0f)
        anim.animateTo(1f, animationSpec = tween(durationMillis = 420))
    }

    val nodePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val progress = anim.value
        val maxR = min(size.width, size.height) / 2f * 0.70f
        val r = maxR * progress
        val n = display.size

        display.forEachIndexed { i, edge ->
            val angle = (-90.0 + i * 360.0 / n) * Math.PI / 180.0
            val nx = cx + (r * cos(angle)).toFloat()
            val ny = cy + (r * sin(angle)).toFloat()
            val color = edgeTypeColor(edge.edgeType)
            drawLine(
                color = color.copy(alpha = 0.45f * progress),
                start = Offset(cx, cy),
                end = Offset(nx, ny),
                strokeWidth = 3f
            )
            drawCircle(color = color.copy(alpha = progress), radius = 9f, center = Offset(nx, ny))
            nodePaint.color = onSurface.toArgb()
            nodePaint.alpha = (progress * 255).toInt().coerceIn(0, 255)
            nodePaint.textSize = labelPx
            drawContext.canvas.nativeCanvas.drawText(edge.spelling, nx, ny - 16f, nodePaint)
        }

        // 中心辐射枢纽（随动画从小到大）。
        drawCircle(color = primary, radius = 13f * (0.4f + 0.6f * progress), center = Offset(cx, cy))
    }
}

/**
 * 阶段D：学习簇进度点——已掌握的点填充为主色，其余为浅色。点数超过上限时截断。
 */
@Composable
fun ClusterDots(
    learned: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val shown = total.coerceIn(0, 14)
    if (shown <= 1) return
    val filledColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(shown) { i ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (i < learned) filledColor else emptyColor)
            )
        }
    }
}
