package com.xty.englishhelper.ui.screen.dictionary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xty.englishhelper.domain.background.ChunkBuildStatus
import com.xty.englishhelper.domain.background.ChunkProgress
import com.xty.englishhelper.domain.repository.ManualChunkContext
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolBuildDetailScreen(
    onBack: () -> Unit,
    viewModel: PoolBuildDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // 网格来源：优先实时网格；进程重启后实时网格为空但任务 FAILED 时，从持久化进度还原（[0,K) 绿、其余白），
    // 以便手动填块在重启后仍可用。
    val gridChunks = remember(state.liveChunks, state.status, state.chunkTotal, state.chunkCurrent) {
        when {
            state.liveChunks.isNotEmpty() -> state.liveChunks
            state.status == BuildStatus.FAILED && state.chunkTotal > 0 ->
                (0 until state.chunkTotal).map { i ->
                    ChunkProgress(
                        index = i,
                        status = if (i < state.chunkCurrent) ChunkBuildStatus.SUCCESS else ChunkBuildStatus.NOT_STARTED
                    )
                }
            else -> emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pool_build_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status header card
            item {
                StatusHeaderCard(
                    status = state.status,
                    strategy = state.strategy
                )
            }

            // 整理模型（全局 POOL 作用域）：随时可切换，热生效。
            item {
                PoolModelCard(
                    providerName = state.poolProviderName,
                    model = state.poolModel,
                    onSwitch = viewModel::openModelSwitch
                )
            }

            // 模型切换成功提示
            val modelMsg = state.modelSwitchMessage
            if (modelMsg != null) {
                item {
                    InfoMessageCard(message = modelMsg, onDismiss = viewModel::clearModelSwitchMessage)
                }
            }

            // Current word card
            if (state.status == BuildStatus.RUNNING || state.status == BuildStatus.PAUSED) {
                item {
                    CurrentWordCard(
                        currentWord = state.currentWord,
                        isPaused = state.isPaused,
                        chunkCurrent = state.chunkCurrent,
                        chunkTotal = state.chunkTotal,
                        edgesFound = state.edgesFound
                    )
                }
            }

            // Progress card
            if (state.progressTotal > 0) {
                item {
                    ProgressCard(
                        current = state.progressCurrent,
                        total = state.progressTotal,
                        isPaused = state.isPaused,
                        chunkCurrent = state.chunkCurrent,
                        chunkTotal = state.chunkTotal
                    )
                }
            } else if (state.status == BuildStatus.RUNNING) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                Text(
                                stringResource(R.string.pool_build_preparing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 手动填块成功提示
            val fillMsg = state.manualFillMessage
            if (fillMsg != null) {
                item {
                    InfoMessageCard(message = fillMsg, onDismiss = viewModel::clearManualFillMessage)
                }
            }

            // 实时分块网格：当前词每个分块一个彩色方块；点击查看服务器返回；FAILED 时点高亮块可手动填入。
            if (gridChunks.isNotEmpty()) {
                item {
                    ChunkGridCard(
                        word = state.liveChunkWord ?: state.currentWord,
                        chunks = gridChunks,
                        fillableChunkIndex = state.fillableChunkIndex,
                        onManualFillClick = viewModel::openManualFill
                    )
                }
            }

            // Action buttons
            item {
                ActionButtonsRow(
                    status = state.status,
                    isPaused = state.isPaused,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onCancel = viewModel::cancel,
                    onRetry = viewModel::retry
                )
            }

            // Error logs
            if (state.errorLogs.isNotEmpty()) {
                item {
                    ErrorLogsCard(
                        logs = state.errorLogs,
                        onClear = viewModel::clearErrorLogs
                    )
                }
            }

            // Error message from failed task (only show if no error logs, to avoid duplication)
            val errorMsg = state.errorMessage
            if (state.status == BuildStatus.FAILED && errorMsg != null && state.errorLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = stringResource(R.string.common_error),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    stringResource(R.string.pool_build_failed),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.manualFillVisible) {
            ManualFillDialog(
                loading = state.manualFillLoading,
                context = state.manualFillContext,
                error = state.manualFillError,
                submitting = state.manualFillSubmitting,
                onSubmit = viewModel::submitManualFill,
                onDismiss = viewModel::dismissManualFill
            )
        }

        if (state.modelSwitchVisible) {
            ModelSwitchDialog(
                providers = state.poolProviders,
                editingProviderName = state.editingProviderName,
                editingModel = state.editingModel,
                modelOptions = state.modelOptions,
                modelLoading = state.modelLoading,
                modelError = state.modelError,
                onProviderChange = viewModel::onEditingProviderChange,
                onModelChange = viewModel::onEditingModelChange,
                onFetchModels = viewModel::fetchModelsForEditing,
                onConfirm = viewModel::confirmModelSwitch,
                onDismiss = viewModel::dismissModelSwitch
            )
        }
    }
}

@Composable
private fun StatusHeaderCard(
    status: BuildStatus,
    strategy: String?
) {
    val (icon, iconColor, title, subtitle) = when (status) {
        BuildStatus.IDLE -> StatusInfo(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.pool_build_idle),
            stringResource(R.string.pool_build_idle_desc)
        )
        BuildStatus.RUNNING -> StatusInfo(
            Icons.Default.Refresh,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.pool_build_running),
            strategyDisplayName(strategy)
        )
        BuildStatus.PAUSED -> StatusInfo(
            Icons.Default.Pause,
            MaterialTheme.colorScheme.tertiary,
            stringResource(R.string.pool_build_paused),
            strategyDisplayName(strategy)
        )
        BuildStatus.SUCCESS -> StatusInfo(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.tertiary,
            stringResource(R.string.pool_build_success),
            strategyDisplayName(strategy)
        )
        BuildStatus.FAILED -> StatusInfo(
            Icons.Default.Error,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.pool_build_failed),
            strategyDisplayName(strategy)
        )
        BuildStatus.CANCELED -> StatusInfo(
            Icons.Default.Close,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.pool_build_canceled),
            strategyDisplayName(strategy)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PoolModelCard(
    providerName: String,
    model: String,
    onSwitch: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.pool_build_model_global),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${providerName.ifBlank { stringResource(R.string.pool_build_default_provider) }} · ${model.ifBlank { stringResource(R.string.pool_build_default_model) }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    stringResource(R.string.pool_build_switch_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onSwitch) {
                Text(stringResource(R.string.pool_build_switch))
            }
        }
    }
}

@Composable
private fun CurrentWordCard(
    currentWord: String?,
    isPaused: Boolean,
    chunkCurrent: Int = 0,
    chunkTotal: Int = 0,
    edgesFound: Int = 0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isPaused) stringResource(R.string.pool_build_paused) else stringResource(R.string.pool_build_processing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = currentWord ?: stringResource(R.string.pool_build_preparing_word),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!isPaused && currentWord != null) {
                Spacer(modifier = Modifier.height(8.dp))
                if (chunkTotal > 0) {
                    // 已完成的候选词对比组数（X / Y）
                    Text(
                        text = stringResource(R.string.pool_build_chunk_progress, chunkCurrent, chunkTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = stringResource(R.string.pool_build_edges_found, edgesFound),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(
    current: Int,
    total: Int,
    isPaused: Boolean,
    chunkCurrent: Int = 0,
    chunkTotal: Int = 0
) {
    val progress = remember(current, total) {
        if (total > 0) current.toFloat() / total else 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "progress"
    )

    val remaining = (total - current).coerceAtLeast(0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.pool_build_progress_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "$current / $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = stringResource(R.string.pool_build_done),
                    value = "$current"
                )
                StatItem(
                    label = stringResource(R.string.pool_build_remaining),
                    value = "$remaining"
                )
                StatItem(
                    label = stringResource(R.string.pool_build_progress),
                    value = "${(animatedProgress * 100).toInt()}%"
                )
            }

            // 当前词的 chunk 进度
            if (chunkTotal > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.pool_build_current_chunk),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.pool_build_chunk_group, chunkCurrent, chunkTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionButtonsRow(
    status: BuildStatus,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (status) {
            BuildStatus.RUNNING -> {
                FilledTonalButton(
                    onClick = onPause,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.common_pause), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_pause))
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_cancel))
                }
            }
            BuildStatus.PAUSED -> {
                FilledTonalButton(
                    onClick = onResume,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.common_resume), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_resume))
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_cancel))
                }
            }
            BuildStatus.FAILED, BuildStatus.CANCELED -> {
                FilledTonalButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_retry), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.common_retry))
                }
            }
            BuildStatus.SUCCESS, BuildStatus.IDLE -> {
                // No action buttons
            }
        }
    }
}

@Composable
private fun ErrorLogsCard(
    logs: List<String>,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = stringResource(R.string.pool_build_warning),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.pool_build_error_log, logs.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.pool_build_clear_log), color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs.reversed()) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private data class StatusInfo(
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val subtitle: String
)

@Composable
private fun strategyDisplayName(strategy: String?): String {
    return when (strategy) {
        "BALANCED" -> stringResource(R.string.pool_build_strategy_balanced)
        "BALANCED_WITH_AI" -> stringResource(R.string.pool_build_strategy_balanced_ai)
        "QUALITY_FIRST" -> stringResource(R.string.pool_build_strategy_quality_first)
        else -> strategy ?: ""
    }
}

// ── 实时分块网格 ──

/**
 * 当前正在整理的词的分块网格：每个分块一个彩色小方块。颜色见 [chunkColor]/[ChunkBuildStatus]。
 * 点击方块弹出该组每一次请求（含失败重试）的服务器返回；当 [fillableChunkIndex] 非空时（FAILED 的断点块），
 * 点击该高亮块改为触发 [onManualFillClick] 进入手动填入。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChunkGridCard(
    word: String?,
    chunks: List<ChunkProgress>,
    fillableChunkIndex: Int? = null,
    onManualFillClick: () -> Unit = {}
) {
    // 以 word 作 key：切换到下一个词时自动收起已打开的弹窗，避免索引错位。
    var selectedIndex by remember(word) { mutableStateOf<Int?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = if (word != null) stringResource(R.string.pool_build_chunk_word) + " · $word" else stringResource(R.string.pool_build_chunk_word),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            val succeeded = chunks.count { it.status == ChunkBuildStatus.SUCCESS }
            Text(
                text = if (fillableChunkIndex != null) {
                    stringResource(R.string.pool_build_chunk_summary, chunks.size, succeeded,
                        stringResource(R.string.pool_build_click_fillable, fillableChunkIndex + 1))
                } else {
                    stringResource(R.string.pool_build_chunk_summary, chunks.size, succeeded,
                        stringResource(R.string.pool_build_click_block))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            ChunkLegend()
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chunks.forEach { chunk ->
                    val isFillable = fillableChunkIndex != null && chunk.index == fillableChunkIndex
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(chunkColor(chunk.status))
                            .border(
                                width = if (isFillable) 2.dp else 1.dp,
                                color = if (isFillable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                if (isFillable) onManualFillClick() else selectedIndex = chunk.index
                            }
                    )
                }
            }
        }
    }

    val idx = selectedIndex
    if (idx != null && idx in chunks.indices) {
        ChunkDetailDialog(
            chunk = chunks[idx],
            onDismiss = { selectedIndex = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChunkLegend() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendItem(ChunkBuildStatus.SUCCESS, stringResource(R.string.pool_build_chunk_success))
        LegendItem(ChunkBuildStatus.FAILED_1, stringResource(R.string.pool_build_chunk_fail_1))
        LegendItem(ChunkBuildStatus.FAILED_2, stringResource(R.string.pool_build_chunk_fail_2))
        LegendItem(ChunkBuildStatus.FAILED_3, stringResource(R.string.pool_build_chunk_fail_3))
        LegendItem(ChunkBuildStatus.FAILED_4, stringResource(R.string.pool_build_chunk_fail_4))
        LegendItem(ChunkBuildStatus.NOT_STARTED, stringResource(R.string.pool_build_chunk_not_started_label))
    }
}

@Composable
private fun LegendItem(status: ChunkBuildStatus, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(chunkColor(status))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChunkDetailDialog(
    chunk: ChunkProgress,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
        title = {
            Text(stringResource(R.string.pool_build_chunk_detail_dialog, chunk.index + 1, chunkStatusLabel(chunk.status)))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (chunk.attempts.isEmpty()) {
                    Text(
                        stringResource(R.string.pool_build_chunk_not_started),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    chunk.attempts.forEach { att ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.pool_build_attempt_format, att.attempt,
                                    if (att.success) stringResource(R.string.pool_build_attempt_success) else stringResource(R.string.pool_build_attempt_fail)),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (att.success) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                            if (att.error != null) {
                                Text(
                                    stringResource(R.string.pool_build_attempt_reason, att.error),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                stringResource(R.string.pool_build_server_response),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = att.response?.ifBlank { stringResource(R.string.pool_build_empty_response) } ?: stringResource(R.string.pool_build_no_response),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp)
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    )
}

/** 六色映射：绿=成功 / 黄=失败1次 / 褐=失败2次 / 红=失败3次 / 黑=失败4次 / 白=未开始。 */
private fun chunkColor(status: ChunkBuildStatus): Color = when (status) {
    ChunkBuildStatus.SUCCESS -> Color(0xFF4CAF50)
    ChunkBuildStatus.FAILED_1 -> Color(0xFFFBC02D)
    ChunkBuildStatus.FAILED_2 -> Color(0xFF8D6E63)
    ChunkBuildStatus.FAILED_3 -> Color(0xFFE53935)
    ChunkBuildStatus.FAILED_4 -> Color(0xFF000000)
    ChunkBuildStatus.NOT_STARTED -> Color(0xFFFFFFFF)
}

@Composable
private fun chunkStatusLabel(status: ChunkBuildStatus): String = when (status) {
    ChunkBuildStatus.SUCCESS -> stringResource(R.string.pool_build_chunk_status_success)
    ChunkBuildStatus.FAILED_1 -> stringResource(R.string.pool_build_chunk_status_fail_1)
    ChunkBuildStatus.FAILED_2 -> stringResource(R.string.pool_build_chunk_status_fail_2)
    ChunkBuildStatus.FAILED_3 -> stringResource(R.string.pool_build_chunk_status_fail_3)
    ChunkBuildStatus.FAILED_4 -> stringResource(R.string.pool_build_chunk_status_fail_4)
    ChunkBuildStatus.NOT_STARTED -> stringResource(R.string.pool_build_chunk_status_not_started)
}

// ── 词池整理模型切换 UI ──

/**
 * 切换词池整理模型（全局 POOL 作用域）。提供商以 FilterChip 选择（标注是否已配 Key），
 * 模型可「拉取模型」从接口取列表后选，或手动输入。确认即写入全局配置，构建下一次请求热生效。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelSwitchDialog(
    providers: List<PoolModelProvider>,
    editingProviderName: String,
    editingModel: String,
    modelOptions: List<String>,
    modelLoading: Boolean,
    modelError: String?,
    onProviderChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onFetchModels: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedProvider = providers.firstOrNull { it.name == editingProviderName }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = editingProviderName.isNotBlank() && editingModel.isNotBlank()
            ) { Text(stringResource(R.string.pool_build_confirm_switch)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
        title = { Text(stringResource(R.string.pool_build_switch_model_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.pool_build_provider),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (providers.isEmpty()) {
                    Text(
                        stringResource(R.string.pool_build_no_provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        providers.forEach { p ->
                            FilterChip(
                                selected = p.name == editingProviderName,
                                onClick = { onProviderChange(p.name) },
                                label = { Text(if (p.hasApiKey) p.name else "${p.name}${stringResource(R.string.pool_build_no_key_suffix)}") }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.pool_build_model),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onFetchModels,
                        enabled = !modelLoading && editingProviderName.isNotBlank()
                    ) {
                        Text(if (modelLoading) stringResource(R.string.pool_build_fetching) else stringResource(R.string.pool_build_fetch_models))
                    }
                }
                if (modelOptions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        modelOptions.forEach { m ->
                            FilterChip(
                                selected = m == editingModel,
                                onClick = { onModelChange(m) },
                                label = { Text(m) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = editingModel,
                    onValueChange = onModelChange,
                    label = { Text(stringResource(R.string.pool_build_model_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (modelError != null) {
                    Text(
                        modelError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (selectedProvider != null && !selectedProvider.hasApiKey) {
                    Text(
                        stringResource(R.string.pool_build_no_apikey),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    stringResource(R.string.pool_build_switch_global),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

// ── 手动填块 UI ──

@Composable
private fun InfoMessageCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pool_build_got_it)) }
        }
    }
}

/**
 * 手动填块弹窗：展示该块目标词 + 候选词（带 i 序号）+「复制提示词」，用户粘贴 JSON 数组（同 AI 返回格式）。
 * 空数组 [] 合法（该块判定无边，可直接跳过敏感块）。仅在构建 FAILED 时由断点块触发。
 */
@Composable
private fun ManualFillDialog(
    loading: Boolean,
    context: ManualChunkContext?,
    error: String?,
    submitting: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var json by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(json) },
                enabled = !submitting && !loading && context != null
            ) { Text(if (submitting) stringResource(R.string.pool_build_submitting) else stringResource(R.string.pool_build_fill_and_mark)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) }
        },
        title = {
            Text(
                if (context != null) stringResource(R.string.pool_build_manual_fill_chunk, context.chunkIndex + 1, context.totalChunks)
                else stringResource(R.string.pool_build_manual_fill)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    loading -> Text(stringResource(R.string.pool_build_loading_candidates), style = MaterialTheme.typography.bodyMedium)
                    context == null -> Text(
                        error ?: stringResource(R.string.pool_build_cannot_load_chunk),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> {
                        Text(
                            stringResource(R.string.pool_build_target_word, context.targetSpelling),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.pool_build_candidates_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { clipboard.setText(AnnotatedString(context.promptText)) }) {
                                Text(stringResource(R.string.pool_build_copy_prompt))
                            }
                        }
                        context.candidates.forEach { c ->
                            Text(
                                "${c.index}. ${c.spelling} [${c.pos}]: ${c.definition}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedTextField(
                            value = json,
                            onValueChange = { json = it },
                            label = { Text(stringResource(R.string.pool_build_paste_json)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                        )
                        if (error != null) {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    )
}
