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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskPayload
import com.xty.englishhelper.domain.model.AppUpdateCheckPayload
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.OnlineArticleScanScorePayload
import com.xty.englishhelper.domain.model.QuestionAnswerGeneratePayload
import com.xty.englishhelper.domain.model.QuestionGeneratePayload
import com.xty.englishhelper.domain.model.QuestionSourceVerifyPayload
import com.xty.englishhelper.domain.model.QuestionWritingSamplePayload
import com.xty.englishhelper.domain.model.WordNoteOrganizePayload
import com.xty.englishhelper.domain.model.WordPhraseOrganizePayload
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.model.WordPoolReviewPayload
import com.xty.englishhelper.domain.model.WordOrganizePayload
import com.xty.englishhelper.domain.model.isHiddenByDefault
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundTaskScreen(
    onBack: () -> Unit,
    viewModel: BackgroundTaskViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    AppTopBarEffect(
        title = { Text(stringResource(R.string.task_management)) },
        navigationIcon = { AppTopBarBackButton(onBack) }
    )

    Scaffold { padding ->
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

                val hiddenCount = state.tasks.count { it.isHiddenByDefault }
                if (hiddenCount > 0) {
                    AssistChip(
                        onClick = viewModel::toggleHiddenTasks,
                        label = {
                            Text(
                                if (state.showHiddenTasks) {
                                    stringResource(R.string.task_hide_hidden)
                                } else {
                                    stringResource(R.string.task_show_hidden, hiddenCount)
                                }
                            )
                        }
                    )
                }

                HorizontalDivider()

                val filtered = state.tasks
                    .filter { state.showHiddenTasks || !it.isHiddenByDefault }
                    .filter(viewModel::matchesFilter)
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.task_no_tasks),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchActions(
    onPauseAll: () -> Unit,
    onCancelAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onClearFinished: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = onPauseAll,
            label = { Text(stringResource(R.string.task_pause_all)) },
            leadingIcon = { Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.common_pause)) }
        )
        AssistChip(
            onClick = onCancelAll,
            label = { Text(stringResource(R.string.task_stop_all)) },
            leadingIcon = { Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.common_stop)) }
        )
        AssistChip(
            onClick = onResumeAll,
            label = { Text(stringResource(R.string.task_resume_all)) },
            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.common_resume)) }
        )
        AssistChip(
            onClick = onRetryFailed,
            label = { Text(stringResource(R.string.task_retry_failed)) },
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_retry)) }
        )
        AssistChip(
            onClick = onClearFinished,
            label = { Text(stringResource(R.string.task_clear_finished)) },
            leadingIcon = { Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.common_clear)) }
        )
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
                    text = stringResource(R.string.task_progress_format, progress, task.progressTotal),
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
                        stringResource(R.string.task_processing),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(
    task: BackgroundTask,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (task.status) {
            BackgroundTaskStatus.PENDING, BackgroundTaskStatus.RUNNING -> {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_stop)) }
            }
            BackgroundTaskStatus.PAUSED -> {
                TextButton(onClick = onResume) { Text(stringResource(R.string.common_resume)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.common_clear)) }
            }
            BackgroundTaskStatus.FAILED -> {
                TextButton(onClick = onRestart) { Text(stringResource(R.string.common_restart)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.common_clear)) }
            }
            BackgroundTaskStatus.SUCCESS, BackgroundTaskStatus.CANCELED -> {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.common_clear)) }
            }
        }
    }
}

@Composable
private fun StatusChip(status: BackgroundTaskStatus) {
    val (label, containerColor, contentColor) = when (status) {
        BackgroundTaskStatus.PENDING -> Triple(
            stringResource(R.string.task_status_pending),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        BackgroundTaskStatus.RUNNING -> Triple(
            stringResource(R.string.task_status_running),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        BackgroundTaskStatus.PAUSED -> Triple(
            stringResource(R.string.task_status_paused),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        BackgroundTaskStatus.SUCCESS -> Triple(
            stringResource(R.string.task_status_completed),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        BackgroundTaskStatus.FAILED -> Triple(
            stringResource(R.string.task_status_failed),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        BackgroundTaskStatus.CANCELED -> Triple(
            stringResource(R.string.task_status_canceled),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun taskTitle(task: BackgroundTask): String {
    return when (task.type) {
        BackgroundTaskType.WORD_ORGANIZE -> stringResource(R.string.task_word_organize)
        BackgroundTaskType.WORD_NOTE_ORGANIZE -> stringResource(R.string.task_word_note_organize)
        BackgroundTaskType.WORD_PHRASE_ORGANIZE -> stringResource(R.string.task_word_phrase_organize)
        BackgroundTaskType.WORD_POOL_REBUILD -> stringResource(R.string.task_pool_rebuild)
        BackgroundTaskType.WORD_POOL_REVIEW -> stringResource(R.string.task_pool_review)
        BackgroundTaskType.QUESTION_GENERATE -> stringResource(R.string.task_question_generate)
        BackgroundTaskType.QUESTION_ANSWER_GENERATE -> stringResource(R.string.task_answer_generate)
        BackgroundTaskType.QUESTION_SOURCE_VERIFY -> stringResource(R.string.task_source_verify)
        BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH -> stringResource(R.string.task_writing_sample)
        BackgroundTaskType.ONLINE_ARTICLE_SCAN_SCORE -> stringResource(R.string.task_online_scan)
        BackgroundTaskType.APP_UPDATE_CHECK -> stringResource(R.string.task_app_update_check)
        BackgroundTaskType.CLOUD_SYNC -> stringResource(R.string.task_cloud_sync)
        BackgroundTaskType.UNKNOWN -> stringResource(R.string.task_unknown)
    }
}

@Composable
private fun taskSubtitle(task: BackgroundTask): String {
    val payload = task.payload
    return when (payload) {
        is WordOrganizePayload -> payload.spelling
        is WordNoteOrganizePayload ->
            stringResource(R.string.task_subtitle_word_pair, payload.sourceSpelling, payload.targetSpelling)
        is WordPhraseOrganizePayload -> buildString {
            if (payload.dictionaryName.isNotBlank()) {
                append(payload.dictionaryName)
            } else {
                append(stringResource(R.string.task_subtitle_dict))
                append(payload.dictionaryId)
            }
            if (payload.mode.isNotBlank()) {
                append(" · ")
                append(payload.mode)
            }
        }
        is WordPoolRebuildPayload -> buildString {
            append(stringResource(R.string.task_subtitle_dict))
            append(payload.dictionaryId)
            if (payload.strategy.isNotBlank()) {
                append(" · ")
                append(payload.strategy)
            }
        }
        is WordPoolReviewPayload -> buildString {
            append(stringResource(R.string.task_subtitle_dict))
            append(payload.dictionaryId)
            if (payload.strategy.isNotBlank()) {
                append(" · ")
                append(payload.strategy)
            }
        }
        is QuestionGeneratePayload -> buildString {
            if (payload.paperTitle.isNotBlank()) append(payload.paperTitle)
            if (payload.questionType.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                val typeLabel = runCatching {
                    com.xty.englishhelper.domain.model.QuestionType.valueOf(payload.questionType).displayName
                }.getOrNull() ?: payload.questionType
                append(typeLabel)
            }
            payload.variant?.takeIf { it.isNotBlank() }?.let { variant ->
                if (isNotEmpty()) append(" · ")
                append(variant)
            }
            if (isEmpty()) append(stringResource(R.string.task_subtitle_article) + payload.articleId)
        }
        is QuestionAnswerGeneratePayload -> buildString {
            if (payload.paperTitle.isNotBlank()) append(payload.paperTitle)
            if (payload.sectionLabel.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(payload.sectionLabel)
            }
            if (isEmpty()) append(stringResource(R.string.task_subtitle_group) + payload.groupId)
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
            if (isEmpty()) append(stringResource(R.string.task_subtitle_group) + payload.groupId)
        }
        is QuestionWritingSamplePayload -> buildString {
            if (payload.paperTitle.isNotBlank()) append(payload.paperTitle)
            payload.questionSnippet.takeIf { it.isNotBlank() }?.let { snippet ->
                if (isNotEmpty()) append(" · ")
                append(snippet)
            }
            if (isEmpty()) append(stringResource(R.string.task_subtitle_group) + payload.groupId)
        }
        is OnlineArticleScanScorePayload -> {
            stringResource(R.string.task_subtitle_scan_format, payload.rescoreAfterHours)
        }
        is AppUpdateCheckPayload -> when {
            payload.latestVersion == null -> stringResource(R.string.task_subtitle_update_pending)
            payload.updateAvailable -> stringResource(
                R.string.task_subtitle_update_available,
                payload.latestVersion
            )
            else -> stringResource(
                R.string.task_subtitle_update_current,
                payload.currentVersion
            )
        }
        else -> stringResource(R.string.task_subtitle_task_id, task.id)
    }
}

@Composable
private fun filterLabel(filter: TaskFilter): String {
    return when (filter) {
        TaskFilter.ALL -> stringResource(R.string.task_filter_all)
        TaskFilter.PENDING -> stringResource(R.string.task_filter_pending)
        TaskFilter.RUNNING -> stringResource(R.string.task_filter_running)
        TaskFilter.PAUSED -> stringResource(R.string.task_filter_paused)
        TaskFilter.FAILED -> stringResource(R.string.task_filter_failed)
        TaskFilter.SUCCESS -> stringResource(R.string.task_filter_success)
        TaskFilter.CANCELED -> stringResource(R.string.task_filter_canceled)
    }
}
