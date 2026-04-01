package com.xty.englishhelper.ui.screen.dictionary

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.PoolStrategy
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

    val dictionaryId = state.dictionary?.id

    val handleWordClick: (Long, Long) -> Unit = { wordId, _ ->
        if (isWide) {
            selectedWordId = wordId
        } else {
            dictionaryId?.let { onWordClick(wordId, it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.dictionary?.name ?: "辞书") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    state.dictionary?.let { dict ->
                        IconButton(onClick = viewModel::showFilterDialog) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "筛选",
                                tint = if (state.hasActiveWordFilter) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        TextButton(onClick = viewModel::toggleBatchMode) {
                            Text(if (state.isBatchMode) "退出批量" else "批量")
                        }
                        IconButton(onClick = { onStudy(dict.id) }) {
                            Icon(Icons.Default.School, contentDescription = "背单词")
                        }
                        Box {
                            IconButton(onClick = { showPoolMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = showPoolMenu,
                                onDismissRequest = { showPoolMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("生成词池（均衡·本地）") },
                                    onClick = {
                                        showPoolMenu = false
                                        viewModel.requestRebuildPools(PoolStrategy.BALANCED)
                                    },
                                    enabled = !state.isRebuildingPools
                                )
                                DropdownMenuItem(
                                    text = { Text("生成词池（均衡+AI）") },
                                    onClick = {
                                        showPoolMenu = false
                                        viewModel.requestRebuildPools(PoolStrategy.BALANCED_WITH_AI)
                                    },
                                    enabled = !state.isRebuildingPools
                                )
                                DropdownMenuItem(
                                    text = { Text("生成词池（质量优先·高消耗）") },
                                    onClick = {
                                        showPoolMenu = false
                                        viewModel.requestRebuildPools(PoolStrategy.QUALITY_FIRST)
                                    },
                                    enabled = !state.isRebuildingPools
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            state.dictionary?.let { dict ->
                Box {
                    FloatingActionButton(onClick = { showFabMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加单词")
                    }
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("手动添加") },
                            onClick = {
                                showFabMenu = false
                                onAddWord(dict.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("拍照批量导入") },
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
                        selectedWordId = selectedWordId
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
                                text = "选择一个单词查看详情",
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
                    selectedWordId = null
                )
            }
        }
    }

    state.deleteTarget?.let { word ->
        ConfirmDialog(
            title = "删除单词",
            message = "确定要删除单词「${word.spelling}」吗？",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }

    if (state.showCreateUnitDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCreateUnitDialog,
            title = { Text("新建单元") },
            text = {
                OutlinedTextField(
                    value = state.newUnitName,
                    onValueChange = viewModel::onNewUnitNameChange,
                    label = { Text("单元名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmCreateUnit,
                    enabled = state.newUnitName.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCreateUnitDialog) {
                    Text("取消")
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
            title = "批量删除单词",
            message = "确定要删除已选的 ${state.selectedWordIds.size} 个单词吗？此操作不可撤销。",
            onConfirm = viewModel::confirmDeleteSelectedWords,
            onDismiss = viewModel::dismissBatchDeleteConfirm
        )
    }

    // Quality-First confirm dialog
    if (state.showQfConfirmDialog) {
        val estimatedTokens = state.qfWordCount * 600
        AlertDialog(
            onDismissRequest = viewModel::dismissQfConfirmDialog,
            title = { Text("质量优先策略确认") },
            text = {
                Column {
                    Text("将对词典中 ${state.qfWordCount} 个词各发起一次 AI 请求")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("预计消耗约 ${state.qfWordCount} x 600 = $estimatedTokens tokens")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "此操作费用较高，建议先尝试均衡策略",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmQfRebuild) {
                    Text("确认并开始")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQfConfirmDialog) {
                    Text("取消")
                }
            }
        )
    }

    // Background organize detail dialog
    if (state.showOrganizeDetailDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOrganizeDetailDialog,
            title = { Text("后台整理任务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.organizeTasks.isEmpty()) {
                        Text("暂无任务")
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
                                            "整理中…",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        OrganizeTaskStatus.SUCCESS -> Text(
                                            "完成",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        OrganizeTaskStatus.FAILED -> Text(
                                            task.error ?: "失败",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        OrganizeTaskStatus.PAUSED -> Text(
                                            "已暂停",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (task.status != OrganizeTaskStatus.ORGANIZING) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (task.status == OrganizeTaskStatus.PAUSED) {
                                            TextButton(onClick = { viewModel.resumeOrganizeTask(task.wordId) }) {
                                                Text("继续")
                                            }
                                        }
                                        TextButton(onClick = { viewModel.dismissOrganizeTask(task.wordId) }) {
                                            Text("清除")
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
                    Text("关闭")
                }
            },
            dismissButton = {
                Row {
                    if (state.organizeTasks.any { it.value.status == OrganizeTaskStatus.FAILED }) {
                        TextButton(onClick = viewModel::retryAllFailedOrganizeTasks) {
                            Text("重试全部失败")
                        }
                    }
                    if (state.organizeTasks.any { it.value.status == OrganizeTaskStatus.PAUSED }) {
                        TextButton(onClick = viewModel::resumeAllPausedOrganizeTasks) {
                            Text("继续全部暂停")
                        }
                    }
                    if (state.organizeTasks.any { it.value.status != OrganizeTaskStatus.ORGANIZING }) {
                        TextButton(onClick = viewModel::dismissAllOrganizeTasks) {
                            Text("清除已完成")
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DictionaryListContent(
    state: DictionaryUiState,
    viewModel: DictionaryViewModel,
    onWordClick: (Long, Long) -> Unit,
    onUnitClick: (Long, Long) -> Unit,
    selectedWordId: Long?
) {
    SearchBar(
        query = state.searchQuery,
        onQueryChange = viewModel::onSearchQueryChange,
        placeholder = "搜索单词…",
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
                "筛选后 ${state.filteredWords.size}/${state.words.size} 词"
            } else {
                "共 ${state.words.size} 词"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.hasActiveWordFilter) {
                TextButton(onClick = viewModel::resetWordFilter) { Text("清除筛选") }
            }
            if (state.isBatchMode) {
                TextButton(onClick = viewModel::selectAllFilteredWords) { Text("全选筛选结果") }
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
                "已选 ${state.selectedWordIds.size} 个",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = viewModel::clearBatchSelection) { Text("清空") }
            TextButton(
                onClick = viewModel::deleteSelectedWords,
                enabled = state.selectedWordIds.isNotEmpty()
            ) { Text("删除") }
            TextButton(
                onClick = viewModel::reorganizeSelectedWords,
                enabled = state.selectedWordIds.isNotEmpty()
            ) { Text("重新整理") }
        }
    }

    when {
        state.isLoading -> LoadingIndicator()
        state.searchQuery.isNotEmpty() -> {
            if (state.filteredWords.isEmpty()) {
                EmptyState(message = "未找到匹配的单词")
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
                            sectionLabel = "单词"
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
                                    text = "单元 (${state.units.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = viewModel::showCreateUnitDialog) {
                                    Text("新建单元")
                                }
                            }
                        }
                    }

                    if (state.units.isEmpty()) {
                        item {
                            Text(
                                text = "还没有单元，点击「新建单元」创建",
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
                                    sectionLabel = "单元"
                                )
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "词池 ${state.poolCount} 个",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (state.outdatedStrategies.isNotEmpty()) {
                                    Text(
                                        text = "算法已更新，建议重建",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (state.isRebuildingPools) {
                                val progress = state.rebuildProgress
                                if (progress != null && progress.second > 0) {
                                    LinearProgressIndicator(
                                        progress = { progress.first.toFloat() / progress.second },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${progress.first}/${progress.second}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(onClick = viewModel::cancelRebuild) {
                                            Text("取消")
                                        }
                                    }
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    )
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
                                    if (organizing > 0) append("整理中: ${organizing}个")
                                    if (success > 0) {
                                        if (isNotEmpty()) append("  ")
                                        append("已完成: ${success}个")
                                    }
                                    if (failed > 0) {
                                        if (isNotEmpty()) append("  ")
                                        append("失败: ${failed}个")
                                    }
                                    if (paused > 0) {
                                        if (isNotEmpty()) append("  ")
                                        append("已暂停: ${paused}个")
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
                            text = "全部单词 (${state.filteredWords.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (state.filteredWords.isEmpty()) {
                        item {
                            EmptyState(
                                message = if (state.hasActiveWordFilter || state.searchQuery.isNotBlank()) {
                                    "当前筛选下没有单词"
                                } else {
                                    "辞书中还没有单词\n点击 + 添加一个吧"
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
                        sectionLabel = "单词"
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
        title = { Text("筛选单词") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startsWith,
                    onValueChange = { startsWith = it.take(1) },
                    label = { Text("首字母") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minLen,
                        onValueChange = { minLen = it.filter { ch -> ch.isDigit() } },
                        label = { Text("最短长度") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxLen,
                        onValueChange = { maxLen = it.filter { ch -> ch.isDigit() } },
                        label = { Text("最长长度") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!lengthValid) {
                    Text(
                        text = "长度区间无效：最短长度不能大于最长长度",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text("词条存在情况", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresenceChip("音标", phonetic) { phonetic = it }
                    PresenceChip("释义", meanings) { meanings = it }
                    PresenceChip("词根解释", rootExplanation) { rootExplanation = it }
                    PresenceChip("构词拆解", decomposition) { decomposition = it }
                    PresenceChip("近义词", synonyms) { synonyms = it }
                    PresenceChip("形近词", similarWords) { similarWords = it }
                    PresenceChip("同根词", cognates) { cognates = it }
                    PresenceChip("词形变化", inflections) { inflections = it }
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
            ) { Text("应用") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReset) { Text("重置") }
                TextButton(onClick = onDismiss) { Text("取消") }
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
        EntryPresenceFilter.ANY -> "$label: 不限"
        EntryPresenceFilter.PRESENT -> "$label: 有"
        EntryPresenceFilter.MISSING -> "$label: 无"
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
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一页")
        }
        Text(
            text = "$sectionLabel 第 ${currentPage + 1}/$totalPages 页",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        IconButton(onClick = onNext, enabled = currentPage < totalPages - 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一页")
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
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = detailViewModel::showDeleteDialog) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
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
                        text = "单词不存在",
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
                    pools = detailState.pools
                )
            }
        }
    }

    if (detailState.showDeleteDialog) {
        ConfirmDialog(
            title = "删除单词",
            message = "确定要删除单词「${detailState.word?.spelling}」吗？",
            onConfirm = { detailViewModel.confirmDelete(onDeleted) },
            onDismiss = detailViewModel::dismissDeleteDialog
        )
    }
}
