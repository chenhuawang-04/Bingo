package com.xty.englishhelper.ui.screen.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.xty.englishhelper.ui.components.WordListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    onBack: () -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    onAddWord: (dictionaryId: Long) -> Unit,
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
                state.words.isEmpty() -> EmptyState(
                    message = if (state.searchQuery.isNotEmpty()) "未找到匹配的单词"
                    else "辞书中还没有单词\n点击 + 添加一个吧"
                )
                else -> {
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
}
