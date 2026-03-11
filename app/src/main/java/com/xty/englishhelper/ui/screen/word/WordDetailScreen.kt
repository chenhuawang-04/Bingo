package com.xty.englishhelper.ui.screen.word

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.xty.englishhelper.ui.components.LoadingIndicator
import com.xty.englishhelper.ui.components.WordDetailContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    onBack: () -> Unit,
    onEdit: (dictionaryId: Long, wordId: Long) -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    onArticleClick: (articleId: Long, sentenceId: Long) -> Unit = { _, _ -> },
    viewModel: WordDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val word = state.word
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.ttsState.error) {
        state.ttsState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTtsError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(word?.spelling ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (word != null) {
                        val ttsSessionId = "word:${word.id}"
                        val isSpeaking = state.ttsState.isSpeaking && state.ttsState.sessionId == ttsSessionId
                        val canSpeak = state.ttsState.isReady

                        IconButton(
                            onClick = viewModel::toggleSpeakWord,
                            enabled = canSpeak
                        ) {
                            Icon(
                                if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isSpeaking) "暂停朗读" else "朗读"
                            )
                        }
                        TextButton(
                            onClick = viewModel::speakWordUs,
                            enabled = canSpeak
                        ) {
                            Text("US")
                        }
                        TextButton(
                            onClick = viewModel::speakWordUk,
                            enabled = canSpeak
                        ) {
                            Text("UK")
                        }
                        IconButton(onClick = { onEdit(word.dictionaryId, word.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = viewModel::showDeleteDialog) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(Modifier.padding(padding))
            word == null -> Text(
                "单词不存在",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            else -> {
                WordDetailContent(
                    word = word,
                    associatedWords = state.associatedWords,
                    linkedWordIds = state.linkedWordIds,
                    onWordClick = onWordClick,
                    modifier = Modifier.padding(padding),
                    examples = state.examples,
                    onArticleClick = onArticleClick,
                    pools = state.pools
                )
            }
        }
    }

    if (state.showDeleteDialog) {
        ConfirmDialog(
            title = "删除单词",
            message = "确定要删除单词「${word?.spelling}」吗？",
            onConfirm = { viewModel.confirmDelete(onBack) },
            onDismiss = viewModel::dismissDeleteDialog
        )
    }
}
