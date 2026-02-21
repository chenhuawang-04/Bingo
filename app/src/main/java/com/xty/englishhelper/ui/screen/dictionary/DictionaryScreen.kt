package com.xty.englishhelper.ui.screen.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    viewModel: DictionaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val windowWidthClass = currentWindowWidthClass()
    val isWide = windowWidthClass.isExpandedOrMedium()

    var selectedWordId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
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
                    }
                }
            )
        },
        floatingActionButton = {
            state.dictionary?.let { dict ->
                FloatingActionButton(onClick = { onAddWord(dict.id) }) {
                    Icon(Icons.Default.Add, contentDescription = "添加单词")
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.words, key = { it.id }) { word ->
                        WordListItem(
                            word = word,
                            onClick = {
                                state.dictionary?.let {
                                    onWordClick(word.id, it.id)
                                }
                            },
                            isSelected = word.id == selectedWordId
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
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
                                text = "单元",
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
                    items(state.units, key = { "unit_${it.id}" }) { unit ->
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
                    items(state.words, key = { "word_${it.id}" }) { word ->
                        WordListItem(
                            word = word,
                            onClick = {
                                state.dictionary?.let {
                                    onWordClick(word.id, it.id)
                                }
                            },
                            isSelected = word.id == selectedWordId
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPane(
    wordId: Long,
    dictionaryId: Long,
    onWordClick: (Long, Long) -> Unit,
    onEdit: (wordId: Long, dictionaryId: Long) -> Unit,
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
                IconButton(onClick = { onEdit(word.id, word.dictionaryId) }) {
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
                    onWordClick = onWordClick
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
