package com.xty.englishhelper.ui.screen.questionbank

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.SourceVerifyStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun QuestionBankListScreen(
    onScan: () -> Unit,
    onGroupClick: (groupId: Long) -> Unit,
    viewModel: QuestionBankListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.question_bank)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScan) {
                Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.question_scan))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.question_no_questions), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.question_scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Group by paper title
            val grouped = state.groups.groupBy { it.examPaperTitle ?: stringResource(R.string.question_unnamed_paper) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (paperTitle, groups) ->
                    item(key = "header_$paperTitle") {
                        Text(
                            paperTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(groups, key = { it.id }) { group ->
                        QuestionGroupCard(
                            group = group,
                            isGeneratingAnswers = group.id in state.generatingGroupIds,
                            onClick = { onGroupClick(group.id) },
                            onLongClick = { viewModel.requestDelete(group.id) }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    state.deleteConfirmGroupId?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.question_confirm_delete)) },
            text = { Text(stringResource(R.string.question_confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun QuestionGroupCard(
    group: QuestionGroup,
    isGeneratingAnswers: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Section label + type badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    group.sectionLabel ?: group.questionType.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        group.questionType.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Stats row: word count + difficulty + item count
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (group.items.isNotEmpty()) {
                    Text(
                        stringResource(R.string.question_count_format, group.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (group.wordCount > 0) {
                    Text(
                        stringResource(R.string.word_count_format, group.wordCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                group.difficultyLevel?.let { level ->
                    Text(
                        level.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Status row: source verification + answer status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Source verification status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (group.sourceVerified) {
                        SourceVerifyStatus.VERIFIED -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.question_source_verified),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(stringResource(R.string.question_source_verified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        SourceVerifyStatus.FAILED -> {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = stringResource(R.string.question_source_verify_failed),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(stringResource(R.string.question_source_verify_failed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        else -> {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(R.string.question_source_unverified),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(stringResource(R.string.question_source_unverified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Answer status
                if (isGeneratingAnswers) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (group.questionType == QuestionType.WRITING) stringResource(R.string.question_generating_essay_sample) else stringResource(R.string.question_generating_answer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    if (group.hasAiAnswer) {
                        val label = if (group.questionType == QuestionType.WRITING) stringResource(R.string.question_label_essay_sample) else stringResource(R.string.question_label_ai_answer)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (group.hasScannedAnswer) {
                        Text(stringResource(R.string.question_scanned_answer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (!group.hasAiAnswer && !group.hasScannedAnswer) {
                        Text(stringResource(R.string.question_no_answer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
