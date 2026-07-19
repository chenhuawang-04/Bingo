package com.xty.englishhelper.ui.screen.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.PoolHealthReport
import com.xty.englishhelper.domain.organize.OrganizeTaskStatus
import com.xty.englishhelper.ui.adaptive.currentWindowWidthClass
import com.xty.englishhelper.ui.adaptive.isExpandedOrMedium
import com.xty.englishhelper.ui.components.ConfirmDialog
import com.xty.englishhelper.ui.components.EmptyState
import com.xty.englishhelper.ui.components.LoadingIndicator
import com.xty.englishhelper.ui.components.SearchBar
import com.xty.englishhelper.ui.components.UnitCard
import com.xty.englishhelper.ui.components.WordDetailContent
import com.xty.englishhelper.ui.components.WordListItem
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect
import com.xty.englishhelper.ui.screen.word.WordDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    onBack: () -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    onAddWord: (dictionaryId: Long) -> Unit,
    onEditWord: (dictionaryId: Long, wordId: Long) -> Unit,
    onUnitClick: (unitId: Long, dictionaryId: Long) -> Unit,
    onStudy: (dictionaryId: Long) -> Unit,
    onBatchImport: (dictionaryId: Long) -> Unit = {},
    onPoolTaskDetail: (BackgroundTaskType) -> Unit = {},
    onViewPools: () -> Unit = {},
    viewModel: DictionaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val windowWidthClass = currentWindowWidthClass()
    val isWide = windowWidthClass.isExpandedOrMedium()

    var selectedWordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPoolMenu by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.rebuildError) {
        state.rebuildError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearRebuildError()
        }
    }

    LaunchedEffect(state.reviewError) {
        state.reviewError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearReviewError()
        }
    }

    LaunchedEffect(state.phraseOrganizeError) {
        state.phraseOrganizeError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearPhraseOrganizeError()
        }
    }

    val dictionaryId = state.dictionary?.id

    val handleWordClick: (Long, Long) -> Unit = { wordId, _ ->
        if (isWide) {
            selectedWordId = wordId
        } else {
            dictionaryId?.let { onWordClick(wordId, it) }
        }
    }

    AppTopBarEffect(
        title = { Text(state.dictionary?.name ?: stringResource(R.string.nav_dictionary)) },
        navigationIcon = { AppTopBarBackButton(onBack) },
        actions = {
            state.dictionary?.let { dict ->
                IconButton(onClick = viewModel::showFilterDialog) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.dict_filter),
                        tint = if (state.hasActiveWordFilter) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                TextButton(onClick = viewModel::toggleBatchMode) {
                    Text(if (state.isBatchMode) stringResource(R.string.dict_exit_batch) else stringResource(R.string.dict_batch_mode))
                }
                IconButton(onClick = { onStudy(dict.id) }) {
                    Icon(Icons.Default.School, contentDescription = stringResource(R.string.study_title))
                }
                Box {
                    IconButton(onClick = { showPoolMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                    }
                    DropdownMenu(
                        expanded = showPoolMenu,
                        onDismissRequest = { showPoolMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dict_pool_balanced)) },
                            onClick = {
                                showPoolMenu = false
                                viewModel.requestRebuildPools(PoolStrategy.BALANCED)
                            },
                            enabled = !state.isRebuildingPools
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dict_pool_balanced_ai)) },
                            onClick = {
                                showPoolMenu = false
                                viewModel.requestRebuildPools(PoolStrategy.BALANCED_WITH_AI)
                            },
                            enabled = !state.isRebuildingPools
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dict_pool_quality_first)) },
                            onClick = {
                                showPoolMenu = false
                                viewModel.requestRebuildPools(PoolStrategy.QUALITY_FIRST)
                            },
                            enabled = !state.isRebuildingPools
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dict_pool_review)) },
                            onClick = {
                                showPoolMenu = false
                                viewModel.requestReviewPools()
                            },
                            enabled = state.edgeCount > 0 &&
                                !state.isRebuildingPools &&
                                !state.isReviewingPools
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dict_pool_health)) },
                            onClick = {
                                showPoolMenu = false
                                viewModel.requestPoolHealthAudit()
                            },
                            enabled = (state.edgeCount > 0 || state.poolCount > 0) &&
                                !state.isRebuildingPools &&
                                !state.isReviewingPools
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dict_phrase_organize)) },
                            onClick = {
                                showPoolMenu = false
                                viewModel.requestOrganizePhrases()
                            },
                            enabled = !state.isOrganizingPhrases
                        )
                    }
                }
            }
        }
    )

    Scaffold(
        floatingActionButton = {
            state.dictionary?.let { dict ->
                Box {
                    FloatingActionButton(onClick = { showFabMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dict_add_word))
                    }
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dict_manual_add)) },
                            onClick = {
                                showFabMenu = false
                                onAddWord(dict.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dict_photo_batch_import)) },
                            onClick = {
                                showFabMenu = false
                                onBatchImport(dict.id)
                            }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isWide) {
            // List-detail split layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // List pane
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxSize()
                ) {
                    DictionaryListContent(
                        state = state,
                        viewModel = viewModel,
                        onWordClick = handleWordClick,
                        onUnitClick = onUnitClick,
                        selectedWordId = selectedWordId,
                        onPoolTaskDetail = onPoolTaskDetail,
                        onViewPools = onViewPools
                    )
                }

                VerticalDivider()

                // Detail pane
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxSize()
                ) {
                    val wordId = selectedWordId
                    val dictId = dictionaryId
                    if (wordId != null && dictId != null) {
                        DetailPane(
                            wordId = wordId,
                            dictionaryId = dictId,
                            onWordClick = handleWordClick,
                            onEdit = onEditWord,
                            onDeleted = { selectedWordId = null }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.dict_select_word_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // Compact: single-column list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                DictionaryListContent(
                    state = state,
                    viewModel = viewModel,
                    onWordClick = handleWordClick,
                    onUnitClick = onUnitClick,
                    selectedWordId = null,
                    onPoolTaskDetail = onPoolTaskDetail,
                    onViewPools = onViewPools
                )
            }
        }
    }

    state.deleteTarget?.let { word ->
        ConfirmDialog(
            title = stringResource(R.string.dict_delete_word),
            message = stringResource(R.string.dict_delete_word_confirm, word.spelling),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }

    if (state.showCreateUnitDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCreateUnitDialog,
            title = { Text(stringResource(R.string.dict_new_unit)) },
            text = {
                OutlinedTextField(
                    value = state.newUnitName,
                    onValueChange = viewModel::onNewUnitNameChange,
                    label = { Text(stringResource(R.string.dict_unit_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmCreateUnit,
                    enabled = state.newUnitName.isNotBlank()
                ) {
                    Text(stringResource(R.string.common_create))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCreateUnitDialog) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (state.showFilterDialog) {
        DictionaryFilterDialog(
            current = state.wordFilter,
            onDismiss = viewModel::dismissFilterDialog,
            onApply = {
                viewModel.updateWordFilter(it)
                viewModel.dismissFilterDialog()
            },
            onReset = {
                viewModel.resetWordFilter()
                viewModel.dismissFilterDialog()
            }
        )
    }

    if (state.showBatchDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.dict_batch_delete_title),
            message = stringResource(R.string.dict_batch_delete_confirm, state.selectedWordIds.size),
            onConfirm = viewModel::confirmDeleteSelectedWords,
            onDismiss = viewModel::dismissBatchDeleteConfirm
        )
    }

    // Quality-First confirm dialog
    if (state.showQfConfirmDialog) {
        val estimatedTokens = state.qfWordCount * 600
        AlertDialog(
            onDismissRequest = viewModel::dismissQfConfirmDialog,
            title = { Text(stringResource(R.string.dict_qf_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dict_qf_confirm_detail, state.qfWordCount))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.dict_qf_confirm_tokens, state.qfWordCount, estimatedTokens))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.dict_qf_incremental),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.dict_qf_full_rebuild),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::confirmQfFullRebuild) {
                        Text(stringResource(R.string.dict_qf_full_rebuild_btn))
                    }
                    TextButton(onClick = viewModel::confirmQfRebuild) {
                        Text(stringResource(R.string.dict_qf_incremental_btn))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQfConfirmDialog) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (state.showPoolHealthDialog) {
        PoolHealthDialog(
            report = state.poolHealthReport,
            isLoading = state.isAuditingPoolHealth,
            isRepairing = state.isRepairingPools,
            error = state.poolHealthError,
            onRepair = viewModel::repairQualityFirstPools,
            onDismiss = viewModel::dismissPoolHealthDialog
        )
    }

    if (state.showPhraseOrganizeConfirmDialog) {
        val wordCount = state.words.size
        val estimatedTokens = wordCount * 450
        AlertDialog(
            onDismissRequest = viewModel::dismissPhraseOrganizeConfirmDialog,
            title = { Text(stringResource(R.string.dict_phrase_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.dict_phrase_confirm_detail, wordCount))
                    Text(
                        stringResource(R.string.dict_phrase_confirm_tokens, wordCount, estimatedTokens),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.dict_phrase_confirm_incremental),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOrganizePhrases) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPhraseOrganizeConfirmDialog) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Background organize detail dialog
    if (state.showOrganizeDetailDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOrganizeDetailDialog,
            title = { Text(stringResource(R.string.dict_organize_detail_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.organizeTasks.isEmpty()) {
                        Text(stringResource(R.string.dict_organize_no_tasks))
                    } else {
                        state.organizeTasks.values.forEach { task ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = task.spelling,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    when (task.status) {
                                        OrganizeTaskStatus.ORGANIZING -> Text(
                                            stringResource(R.string.dict_organize_organizing),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        OrganizeTaskStatus.SUCCESS -> Text(
                                            stringResource(R.string.dict_organize_done),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        OrganizeTaskStatus.FAILED -> Text(
                                            task.error ?: stringResource(R.string.common_failed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        OrganizeTaskStatus.PAUSED -> Text(
                                            stringResource(R.string.dict_organize_paused),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (task.status != OrganizeTaskStatus.ORGANIZING) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (task.status == OrganizeTaskStatus.PAUSED) {
                                            TextButton(onClick = { viewModel.resumeOrganizeTask(task.wordId) }) {
                                                Text(stringResource(R.string.common_resume))
                                            }
                                        }
                                        TextButton(onClick = { viewModel.dismissOrganizeTask(task.wordId) }) {
                                            Text(stringResource(R.string.common_clear))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissOrganizeDetailDialog) {
                    Text(stringResource(R.string.common_close))
                }
            },
            dismissButton = {
                Row {
                    if (state.organizeTasks.any { it.value.status == OrganizeTaskStatus.FAILED }) {
                        TextButton(onClick = viewModel::retryAllFailedOrganizeTasks) {
                            Text(stringResource(R.string.dict_retry_all_failed))
                        }
                    }
                    if (state.organizeTasks.any { it.value.status == OrganizeTaskStatus.PAUSED }) {
                        TextButton(onClick = viewModel::resumeAllPausedOrganizeTasks) {
                            Text(stringResource(R.string.dict_resume_all_paused))
                        }
                    }
                    if (state.organizeTasks.any { it.value.status != OrganizeTaskStatus.ORGANIZING }) {
                        TextButton(onClick = viewModel::dismissAllOrganizeTasks) {
                            Text(stringResource(R.string.dict_clear_finished))
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun PoolHealthDialog(
    report: PoolHealthReport?,
    isLoading: Boolean,
    isRepairing: Boolean,
    error: String?,
    onRepair: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pool_health_title)) },
        text = {
            when {
                isLoading -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.pool_health_auditing))
                }

                report != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (report.isHealthy) {
                            stringResource(R.string.pool_health_healthy)
                        } else {
                            stringResource(R.string.pool_health_needs_repair)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (report.isHealthy) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Text(stringResource(R.string.pool_health_pool_count, report.existingPoolCount, report.plannedPoolCount))
                    Text(stringResource(R.string.pool_health_coverage, report.existingCoveredWordCount, report.expectedCoveredWordCount))
                    Text(stringResource(R.string.pool_health_edges_components, report.validEdgeCount, report.connectedComponentCount))
                    Text(stringResource(R.string.pool_health_oversized, report.oversizedComponentCount))
                    Text(stringResource(R.string.pool_health_invalid, report.invalidSizePoolCount, report.disconnectedPoolCount))
                    Text(
                        stringResource(
                            R.string.pool_health_invalid_focus,
                            report.invalidFocusPoolCount,
                            report.duplicatePoolCount
                        )
                    )
                    Text(stringResource(R.string.pool_health_uncovered, report.uncoveredWordCount, report.extraneousMemberCount))
                    if (report.missingSupportingEdgePoolCount > 0) {
                        Text(
                            stringResource(
                                R.string.pool_health_missing_supporting_edges,
                                report.missingSupportingEdgePoolCount
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (report.layoutMismatch) {
                        Text(
                            stringResource(R.string.pool_health_layout_mismatch),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (report.orphanEdgeCount + report.selfLoopEdgeCount + report.unknownTypeEdgeCount > 0) {
                        Text(
                            stringResource(
                                R.string.pool_health_edge_warnings,
                                report.orphanEdgeCount,
                                report.selfLoopEdgeCount,
                                report.unknownTypeEdgeCount
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!error.isNullOrBlank()) {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (isRepairing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.pool_health_repairing),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                else -> Text(error ?: stringResource(R.string.pool_health_failed))
            }
        },
        confirmButton = {
            if (report != null && !report.isHealthy) {
                TextButton(
                    onClick = onRepair,
                    enabled = report.canRepairFromExistingEdges && !isRepairing
                ) {
                    Text(stringResource(R.string.pool_health_repair))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading && !isRepairing) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
private fun DictionaryListContent(
    state: DictionaryUiState,
    viewModel: DictionaryViewModel,
    onWordClick: (Long, Long) -> Unit,
    onUnitClick: (Long, Long) -> Unit,
    selectedWordId: Long?,
    onPoolTaskDetail: (BackgroundTaskType) -> Unit = {},
    onViewPools: () -> Unit = {}
) {
    SearchBar(
        query = state.searchQuery,
        onQueryChange = viewModel::onSearchQueryChange,
        placeholder = stringResource(R.string.dict_search_words),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (state.hasActiveWordFilter) {
                stringResource(R.string.dict_words_filtered, state.filteredWords.size, state.words.size)
            } else {
                stringResource(R.string.dict_words_total, state.words.size)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.hasActiveWordFilter) {
                TextButton(onClick = viewModel::resetWordFilter) { Text(stringResource(R.string.dict_clear_filter)) }
            }
            if (state.isBatchMode) {
                TextButton(onClick = viewModel::selectAllFilteredWords) { Text(stringResource(R.string.dict_select_all_filtered)) }
            }
        }
    }

    if (state.isBatchMode) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.dict_selected_count, state.selectedWordIds.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = viewModel::clearBatchSelection) { Text(stringResource(R.string.dict_clear_selection)) }
            TextButton(
                onClick = viewModel::deleteSelectedWords,
                enabled = state.selectedWordIds.isNotEmpty()
            ) { Text(stringResource(R.string.common_delete)) }
            TextButton(
                onClick = viewModel::reorganizeSelectedWords,
                enabled = state.selectedWordIds.isNotEmpty()
            ) { Text(stringResource(R.string.dict_reorganize)) }
        }
    }

    when {
        state.isLoading -> LoadingIndicator()
        state.searchQuery.isNotEmpty() -> {
            if (state.filteredWords.isEmpty()) {
                EmptyState(message = stringResource(R.string.dict_no_match))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(state.pagedWords, key = { it.id }) { word ->
                            SelectableWordRow(
                                word = word,
                                state = state,
                                selectedWordId = selectedWordId,
                                onWordClick = onWordClick,
                                onToggleWordSelection = viewModel::toggleWordSelection
                            )
                            HorizontalDivider()
                        }
                    }
                    if (state.totalPages > 1) {
                        PaginationBar(
                            currentPage = state.currentPage,
                            totalPages = state.totalPages,
                            onPrevious = viewModel::previousPage,
                            onNext = viewModel::nextPage,
                            sectionLabel = stringResource(R.string.dict_word_section)
                        )
                    }
                }
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (state.units.isNotEmpty() || state.words.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.dict_unit_count, state.units.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = viewModel::showCreateUnitDialog) {
                                    Text(stringResource(R.string.dict_new_unit))
                                }
                            }
                        }
                    }

                    if (state.units.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.dict_no_units),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(state.pagedUnits, key = { "unit_${it.id}" }) { unit ->
                            UnitCard(
                                unit = unit,
                                onClick = {
                                    state.dictionary?.let {
                                        onUnitClick(unit.id, it.id)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        if (state.totalUnitPages > 1) {
                            item {
                                PaginationBar(
                                    currentPage = state.unitCurrentPage,
                                    totalPages = state.totalUnitPages,
                                    onPrevious = viewModel::previousUnitPage,
                                    onNext = viewModel::nextUnitPage,
                                    sectionLabel = stringResource(R.string.dict_unit_section)
                                )
                            }
                        }
                    }

                    item {
                        val activePoolTaskType = when {
                            state.isReviewingPools -> BackgroundTaskType.WORD_POOL_REVIEW
                            state.isRebuildingPools -> BackgroundTaskType.WORD_POOL_REBUILD
                            else -> null
                        }
                        val canViewPools = activePoolTaskType == null && state.poolCount > 0
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    when {
                                        activePoolTaskType != null -> Modifier.clickable { onPoolTaskDetail(activePoolTaskType) }
                                        canViewPools -> Modifier.clickable { onViewPools() }
                                        else -> Modifier
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.dict_pool_count, state.poolCount),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (state.outdatedStrategies.isNotEmpty()) {
                                        Text(
                                            text = stringResource(R.string.dict_algorithm_updated),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (state.isRebuildingPools) {
                                        val progress = state.rebuildProgress
                                        if (progress != null && progress.second > 0) {
                                            Text(
                                                text = "${progress.first}/${progress.second}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (state.currentBuildWord != null) {
                                            Text(
                                                text = state.currentBuildWord,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    } else if (state.isReviewingPools && state.currentReviewMessage != null) {
                                        Text(
                                            text = state.currentReviewMessage,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                                if (activePoolTaskType != null) {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = stringResource(R.string.common_view_detail),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (canViewPools) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.dict_view_graph),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = stringResource(R.string.dict_view_pool_graph),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (state.isRebuildingPools) {
                                val progress = state.rebuildProgress
                                if (progress != null && progress.second > 0) {
                                    LinearProgressIndicator(
                                        progress = { progress.first.toFloat() / progress.second },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                    )
                                }
                                // Quick pause/cancel row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            if (state.isBuildPaused) viewModel.resumeRebuild() else viewModel.pauseRebuild()
                                        }
                                    ) {
                                        Text(if (state.isBuildPaused) stringResource(R.string.common_resume) else stringResource(R.string.common_pause))
                                    }
                                    TextButton(onClick = viewModel::cancelRebuild) {
                                        Text(stringResource(R.string.common_cancel))
                                    }
                                    TextButton(onClick = { onPoolTaskDetail(BackgroundTaskType.WORD_POOL_REBUILD) }) {
                                        Text(stringResource(R.string.common_details))
                                    }
                                }
                            }

                            // 词池提纯（独立任务）状态行
                            if (state.isReviewingPools) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (state.isReviewPaused) stringResource(R.string.dict_review_paused) else stringResource(R.string.dict_reviewing),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    val rp = state.reviewProgress
                                    if (rp != null && rp.second > 0) {
                                        Text(
                                            text = "${rp.first}/${rp.second}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                                val rp = state.reviewProgress
                                if (rp != null && rp.second > 0) {
                                    LinearProgressIndicator(
                                        progress = { rp.first.toFloat() / rp.second },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            if (state.isReviewPaused) viewModel.resumeReview() else viewModel.pauseReview()
                                        }
                                    ) {
                                        Text(if (state.isReviewPaused) stringResource(R.string.common_resume) else stringResource(R.string.common_pause))
                                    }
                                    TextButton(onClick = viewModel::cancelReview) {
                                        Text(stringResource(R.string.common_cancel))
                                    }
                                    TextButton(onClick = { onPoolTaskDetail(BackgroundTaskType.WORD_POOL_REVIEW) }) {
                                        Text(stringResource(R.string.common_details))
                                    }
                                }
                            }
                        }
                    }

                    if (state.words.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(
                                                R.string.dict_phrase_stats,
                                                state.phraseCount,
                                                state.phraseTagCount,
                                                state.phraseOrganizedWordCount,
                                                state.phraseTotalWordCount
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        state.currentPhraseOrganizeMessage?.let { message ->
                                            Text(
                                                text = message,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                    if (state.isOrganizingPhrases) {
                                        Text(
                                            text = if (state.isPhraseOrganizePaused) stringResource(R.string.dict_phrase_paused) else stringResource(R.string.dict_phrase_organizing),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                                if (state.isOrganizingPhrases) {
                                    val progress = state.phraseOrganizeProgress
                                    if (progress != null && progress.second > 0) {
                                        LinearProgressIndicator(
                                            progress = { progress.first.toFloat() / progress.second },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp)
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (state.isPhraseOrganizePaused) {
                                                    viewModel.resumePhraseOrganize()
                                                } else {
                                                    viewModel.pausePhraseOrganize()
                                                }
                                            }
                                        ) {
                                            Text(if (state.isPhraseOrganizePaused) stringResource(R.string.common_resume) else stringResource(R.string.common_pause))
                                        }
                                        TextButton(onClick = viewModel::cancelPhraseOrganize) {
                                            Text(stringResource(R.string.common_cancel))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.organizeTasks.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val organizing = state.organizingCount
                                val failed = state.organizeTasks.count { it.value.status == OrganizeTaskStatus.FAILED }
                                val success = state.organizeTasks.count { it.value.status == OrganizeTaskStatus.SUCCESS }
                                val paused = state.organizeTasks.count { it.value.status == OrganizeTaskStatus.PAUSED }
                                val label = buildString {
                                    if (organizing > 0) append(stringResource(R.string.dict_organizing_count, organizing))
                                    if (success > 0) {
                                        if (isNotEmpty()) append("  ")
                                        append(stringResource(R.string.dict_organized_count, success))
                                    }
                                    if (failed > 0) {
                                        if (isNotEmpty()) append("  ")
                                        append(stringResource(R.string.dict_failed_count, failed))
                                    }
                                    if (paused > 0) {
                                        if (isNotEmpty()) append("  ")
                                        append(stringResource(R.string.dict_paused_count, paused))
                                    }
                                }
                                if (label.isNotEmpty()) {
                                    TextButton(onClick = viewModel::showOrganizeDetailDialog) {
                                        Text(label)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.dict_all_words, state.filteredWords.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (state.filteredWords.isEmpty()) {
                        item {
                            EmptyState(
                                message = if (state.hasActiveWordFilter || state.searchQuery.isNotBlank()) {
                                    stringResource(R.string.dict_no_words_in_filter)
                                } else {
                                    stringResource(R.string.dict_empty_words)
                                }
                            )
                        }
                    } else {
                        items(state.pagedWords, key = { "word_${it.id}" }) { word ->
                            SelectableWordRow(
                                word = word,
                                state = state,
                                selectedWordId = selectedWordId,
                                onWordClick = onWordClick,
                                onToggleWordSelection = viewModel::toggleWordSelection
                            )
                            HorizontalDivider()
                        }
                    }
                }
                if (state.filteredWords.isNotEmpty() && state.totalPages > 1) {
                    PaginationBar(
                        currentPage = state.currentPage,
                        totalPages = state.totalPages,
                        onPrevious = viewModel::previousPage,
                        onNext = viewModel::nextPage,
                        sectionLabel = stringResource(R.string.dict_word_section)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableWordRow(
    word: com.xty.englishhelper.domain.model.WordDetails,
    state: DictionaryUiState,
    selectedWordId: Long?,
    onWordClick: (Long, Long) -> Unit,
    onToggleWordSelection: (Long) -> Unit
) {
    if (state.isBatchMode) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = word.id in state.selectedWordIds,
                onCheckedChange = { onToggleWordSelection(word.id) }
            )
            WordListItem(
                word = word,
                onClick = { onToggleWordSelection(word.id) },
                modifier = Modifier.weight(1f),
                isSelected = word.id in state.selectedWordIds,
                isOrganizing = word.id in state.organizingWordIds
            )
        }
    } else {
        WordListItem(
            word = word,
            onClick = {
                state.dictionary?.let {
                    onWordClick(word.id, it.id)
                }
            },
            isSelected = word.id == selectedWordId,
            isOrganizing = word.id in state.organizingWordIds
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictionaryFilterDialog(
    current: DictionaryWordFilter,
    onDismiss: () -> Unit,
    onApply: (DictionaryWordFilter) -> Unit,
    onReset: () -> Unit
) {
    var minLen by remember(current.minLength) { mutableStateOf(current.minLength?.toString().orEmpty()) }
    var maxLen by remember(current.maxLength) { mutableStateOf(current.maxLength?.toString().orEmpty()) }
    var startsWith by remember(current.startsWith) { mutableStateOf(current.startsWith) }
    var phonetic by remember(current.phonetic) { mutableStateOf(current.phonetic) }
    var meanings by remember(current.meanings) { mutableStateOf(current.meanings) }
    var rootExplanation by remember(current.rootExplanation) { mutableStateOf(current.rootExplanation) }
    var decomposition by remember(current.decomposition) { mutableStateOf(current.decomposition) }
    var synonyms by remember(current.synonyms) { mutableStateOf(current.synonyms) }
    var similarWords by remember(current.similarWords) { mutableStateOf(current.similarWords) }
    var cognates by remember(current.cognates) { mutableStateOf(current.cognates) }
    var inflections by remember(current.inflections) { mutableStateOf(current.inflections) }

    fun parseIntOrNull(raw: String): Int? = raw.trim().toIntOrNull()?.coerceAtLeast(1)
    val parsedMin = parseIntOrNull(minLen)
    val parsedMax = parseIntOrNull(maxLen)
    val lengthValid = parsedMin == null || parsedMax == null || parsedMin <= parsedMax

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dict_filter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startsWith,
                    onValueChange = { startsWith = it.take(1) },
                    label = { Text(stringResource(R.string.dict_filter_first_letter)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minLen,
                        onValueChange = { minLen = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.dict_filter_min_length)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxLen,
                        onValueChange = { maxLen = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.dict_filter_max_length)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!lengthValid) {
                    Text(
                        text = stringResource(R.string.dict_filter_length_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(stringResource(R.string.dict_filter_entry_presence), style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresenceChip(stringResource(R.string.dict_filter_phonetic), phonetic) { phonetic = it }
                    PresenceChip(stringResource(R.string.dict_filter_meanings), meanings) { meanings = it }
                    PresenceChip(stringResource(R.string.dict_filter_root_explanation), rootExplanation) { rootExplanation = it }
                    PresenceChip(stringResource(R.string.dict_filter_decomposition), decomposition) { decomposition = it }
                    PresenceChip(stringResource(R.string.dict_filter_synonyms), synonyms) { synonyms = it }
                    PresenceChip(stringResource(R.string.dict_filter_similar_words), similarWords) { similarWords = it }
                    PresenceChip(stringResource(R.string.dict_filter_cognates), cognates) { cognates = it }
                    PresenceChip(stringResource(R.string.dict_filter_inflections), inflections) { inflections = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        DictionaryWordFilter(
                            phonetic = phonetic,
                            meanings = meanings,
                            rootExplanation = rootExplanation,
                            decomposition = decomposition,
                            synonyms = synonyms,
                            similarWords = similarWords,
                            cognates = cognates,
                            inflections = inflections,
                            minLength = parseIntOrNull(minLen),
                            maxLength = parseIntOrNull(maxLen),
                            startsWith = startsWith.trim()
                        )
                    )
                },
                enabled = lengthValid
            ) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.common_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    )
}

@Composable
private fun PresenceChip(
    label: String,
    value: EntryPresenceFilter,
    onChange: (EntryPresenceFilter) -> Unit
) {
    val text = when (value) {
        EntryPresenceFilter.ANY -> "$label: ${stringResource(R.string.dict_filter_any)}"
        EntryPresenceFilter.PRESENT -> "$label: ${stringResource(R.string.dict_filter_present)}"
        EntryPresenceFilter.MISSING -> "$label: ${stringResource(R.string.dict_filter_missing)}"
    }
    FilterChip(
        selected = value != EntryPresenceFilter.ANY,
        onClick = {
            val next = when (value) {
                EntryPresenceFilter.ANY -> EntryPresenceFilter.PRESENT
                EntryPresenceFilter.PRESENT -> EntryPresenceFilter.MISSING
                EntryPresenceFilter.MISSING -> EntryPresenceFilter.ANY
            }
            onChange(next)
        },
        label = { Text(text) }
    )
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    sectionLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = currentPage > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.common_previous_page))
        }
        Text(
            text = stringResource(R.string.common_page_format, sectionLabel, currentPage + 1, totalPages),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        IconButton(onClick = onNext, enabled = currentPage < totalPages - 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.common_next_page))
        }
    }
}

@Composable
private fun DetailPane(
    wordId: Long,
    dictionaryId: Long,
    onWordClick: (Long, Long) -> Unit,
    onEdit: (dictionaryId: Long, wordId: Long) -> Unit,
    onDeleted: () -> Unit
) {
    // Single-instance VM — reused across word selections, no accumulation
    val detailViewModel: WordDetailViewModel = hiltViewModel(key = "dict_detail_pane")
    val detailState by detailViewModel.uiState.collectAsState()

    // Reload whenever wordId changes
    LaunchedEffect(wordId, dictionaryId) {
        detailViewModel.loadWord(wordId, dictionaryId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Action bar for edit/delete
        val word = detailState.word
        if (word != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { onEdit(word.dictionaryId, word.id) }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit))
                }
                IconButton(onClick = detailViewModel::showDeleteDialog) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            HorizontalDivider()
        }

        when {
            detailState.isLoading -> LoadingIndicator()
            word == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.word_not_exists),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                WordDetailContent(
                    word = word,
                    associatedWords = detailState.associatedWords,
                    linkedWordIds = detailState.linkedWordIds,
                    onWordClick = onWordClick,
                    pools = detailState.pools,
                    clusters = detailState.clusters,
                    clusterReviews = detailState.clusterReviews,
                    edgePreviews = detailState.edgePreviews,
                    phrases = detailState.phrases,
                    examples = detailState.examples,
                    cloudExamples = detailState.cloudExamples,
                    cloudExamplesLoading = detailState.cloudExamplesLoading,
                    cloudExamplesError = detailState.cloudExamplesError,
                    cloudExampleSource = detailState.cloudExampleSource,
                    onCloudExampleSourceSelected = detailViewModel::selectCloudExampleSource,
                    detailsLoading = detailState.detailsLoading,
                    detailsError = detailState.detailsError,
                    onRetryDetails = detailViewModel::retryDetails,
                    showSpellingHeader = true
                )
            }
        }
    }

    if (detailState.showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.word_delete_title),
            message = stringResource(R.string.dict_delete_word_confirm, detailState.word?.spelling ?: ""),
            onConfirm = { detailViewModel.confirmDelete(onDeleted) },
            onDismiss = detailViewModel::dismissDeleteDialog
        )
    }
}
