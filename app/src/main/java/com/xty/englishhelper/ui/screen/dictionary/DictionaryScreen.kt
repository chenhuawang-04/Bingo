package com.xty.englishhelper.ui.screen.dictionary

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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

    when {
        state.isLoading -> LoadingIndicator()
        state.searchQuery.isNotEmpty() -> {
            if (state.words.isEmpty()) {
                EmptyState(message = "未找到匹配的单词")
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(state.pagedWords, key = { it.id }) { word ->
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
                            text = "全部单词 (${state.words.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (state.words.isEmpty()) {
                        item {
                            EmptyState(
                                message = "辞书中还没有单词\n点击 + 添加一个吧"
                            )
                        }
                    } else {
                        items(state.pagedWords, key = { "word_${it.id}" }) { word ->
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
                            HorizontalDivider()
                        }
                    }
                }
                if (state.words.isNotEmpty() && state.totalPages > 1) {
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
