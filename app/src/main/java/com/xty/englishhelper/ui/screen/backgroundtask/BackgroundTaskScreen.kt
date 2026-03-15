package com.xty.englishhelper.ui.screen.backgroundtask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskPayload
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.QuestionAnswerGeneratePayload
import com.xty.englishhelper.domain.model.QuestionSourceVerifyPayload
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.model.WordOrganizePayload
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundTaskScreen(
    onBack: () -> Unit,
    viewModel: BackgroundTaskViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("后台任务管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        EhMaxWidthContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            maxWidth = 720.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterBar(
                    selected = state.filter,
                    onSelect = viewModel::setFilter
                )

                BatchActions(
                    onPauseAll = viewModel::pauseAll,
                    onCancelAll = viewModel::cancelAll,
                    onResumeAll = viewModel::resumeAll,
                    onRetryFailed = viewModel::retryFailed,
                    onClearFinished = viewModel::clearFinished
                )

                HorizontalDivider()

                val filtered = state.tasks.filter(viewModel::matchesFilter)
                if (filtered.isEmpty()) {
                    Text(
                        "暂无任务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                onCancel = { viewModel.cancelTask(task.id) },
                                onResume = { viewModel.resumeTask(task.id) },
                                onRestart = { viewModel.restartTask(task.id) },
                                onDelete = { viewModel.deleteTask(task.id) }
                            )
                        }
                        item { Spacer(Modifier.height(40.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FilterBar(
    selected: TaskFilter,
    onSelect: (TaskFilter) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TaskFilter.values().forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filterLabel(filter)) }
            )
        }
    }
}

@Composable
private fun BatchActions(
    onPauseAll: () -> Unit,
    onCancelAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onClearFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onPauseAll,
                label = { Text("全部暂停") },
                leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) }
            )
            AssistChip(
                onClick = onCancelAll,
                label = { Text("全部停止") },
                leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) }
            )
            AssistChip(
                onClick = onResumeAll,
                label = { Text("全部继续") },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onRetryFailed,
                label = { Text("重试失败") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
            )
            AssistChip(
                onClick = onClearFinished,
                label = { Text("清除已结束") },
                leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun TaskCard(
    task: BackgroundTask,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(taskTitle(task), style = MaterialTheme.typography.titleSmall)
                    Text(
                        taskSubtitle(task),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusChip(task.status)
            }

            if (task.progressTotal > 0) {
                val progress = task.progressCurrent.coerceAtMost(task.progressTotal)
                Text(
                    "进度 $progress/${task.progressTotal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { progress.toFloat() / task.progressTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (task.status == BackgroundTaskStatus.RUNNING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "处理中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!task.errorMessage.isNullOrBlank()) {
                Text(
                    task.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            ActionRow(task, onCancel, onResume, onRestart, onDelete)
        }
    }
}

@Composable
private fun ActionRow(
    task: BackgroundTask,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (task.status) {
            BackgroundTaskStatus.PENDING, BackgroundTaskStatus.RUNNING -> {
                TextButton(onClick = onCancel) { Text("停止") }
            }
            BackgroundTaskStatus.PAUSED -> {
                TextButton(onClick = onResume) { Text("继续") }
                TextButton(onClick = onDelete) { Text("清除") }
            }
            BackgroundTaskStatus.FAILED -> {
                TextButton(onClick = onRestart) { Text("重启") }
                TextButton(onClick = onDelete) { Text("清除") }
            }
            BackgroundTaskStatus.SUCCESS, BackgroundTaskStatus.CANCELED -> {
                TextButton(onClick = onDelete) { Text("清除") }
            }
        }
    }
}

@Composable
private fun StatusChip(status: BackgroundTaskStatus) {
    val (label, color) = when (status) {
        BackgroundTaskStatus.PENDING -> "等待中" to MaterialTheme.colorScheme.onSurfaceVariant
        BackgroundTaskStatus.RUNNING -> "进行中" to MaterialTheme.colorScheme.primary
        BackgroundTaskStatus.PAUSED -> "已暂停" to MaterialTheme.colorScheme.tertiary
        BackgroundTaskStatus.SUCCESS -> "已完成" to MaterialTheme.colorScheme.primary
        BackgroundTaskStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
        BackgroundTaskStatus.CANCELED -> "已停止" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(label, color = color, style = MaterialTheme.typography.labelSmall) }
    )
}

private fun taskTitle(task: BackgroundTask): String {
    return when (task.type) {
        BackgroundTaskType.WORD_ORGANIZE -> "单词整理"
        BackgroundTaskType.WORD_POOL_REBUILD -> "词池重建"
        BackgroundTaskType.QUESTION_ANSWER_GENERATE -> "题库答案生成"
        BackgroundTaskType.QUESTION_SOURCE_VERIFY -> "题库来源验证"
        BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH -> "作文范文检索"
        BackgroundTaskType.UNKNOWN -> "未知任务"
    }
}

private fun taskSubtitle(task: BackgroundTask): String {
    val payload = task.payload
    return when (payload) {
        is WordOrganizePayload -> payload.spelling
        is WordPoolRebuildPayload -> buildString {
            append("词典 ")
            append(payload.dictionaryId)
            if (payload.strategy.isNotBlank()) {
                append(" · ")
                append(payload.strategy)
            }
        }
        is QuestionAnswerGeneratePayload -> buildString {
            if (payload.paperTitle.isNotBlank()) append(payload.paperTitle)
            if (payload.sectionLabel.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(payload.sectionLabel)
            }
            if (isEmpty()) append("题组 ${payload.groupId}")
        }
        is QuestionSourceVerifyPayload -> buildString {
            if (payload.paperTitle.isNotBlank()) append(payload.paperTitle)
            if (payload.sectionLabel.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(payload.sectionLabel)
            }
            if (!payload.sourceUrlOverride.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(payload.sourceUrlOverride)
            }
            if (isEmpty()) append("题组 ${payload.groupId}")
        }
        else -> "任务 ${task.id}"
    }
}

private fun filterLabel(filter: TaskFilter): String {
    return when (filter) {
        TaskFilter.ALL -> "全部"
        TaskFilter.PENDING -> "等待"
        TaskFilter.RUNNING -> "进行中"
        TaskFilter.PAUSED -> "已暂停"
        TaskFilter.FAILED -> "失败"
        TaskFilter.SUCCESS -> "已完成"
        TaskFilter.CANCELED -> "已停止"
    }
}
