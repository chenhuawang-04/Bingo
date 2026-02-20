package com.xty.englishhelper.ui.screen.dictionary

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.ui.components.ConfirmDialog
import com.xty.englishhelper.ui.components.EmptyState
import com.xty.englishhelper.ui.components.LoadingIndicator
import com.xty.englishhelper.ui.components.SearchBar
import com.xty.englishhelper.ui.components.UnitCard
import com.xty.englishhelper.ui.components.WordListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    onBack: () -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    onAddWord: (dictionaryId: Long) -> Unit,
    onUnitClick: (unitId: Long, dictionaryId: Long) -> Unit,
    onStudy: (dictionaryId: Long) -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                    // Search mode: show matching words
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
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
                else -> {
                    // Default mode: show units + all words
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Units section
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

                        // Words section
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
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
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
