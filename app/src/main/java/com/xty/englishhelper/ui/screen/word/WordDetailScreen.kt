package com.xty.englishhelper.ui.screen.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.ui.components.ConfirmDialog
import com.xty.englishhelper.ui.components.DetailRow
import com.xty.englishhelper.ui.components.LoadingIndicator
import com.xty.englishhelper.ui.components.WordDetailSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    onBack: () -> Unit,
    onEdit: (dictionaryId: Long, wordId: Long) -> Unit,
    viewModel: WordDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val word = state.word

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
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(Modifier.padding(padding))
            word == null -> Text(
                "单词不存在",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Phonetic
                    if (word.phonetic.isNotBlank()) {
                        item {
                            Text(
                                text = word.phonetic,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Meanings
                    if (word.meanings.isNotEmpty()) {
                        item {
                            WordDetailSection(title = "词性与词义") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    word.meanings.forEach { meaning ->
                                        DetailRow(
                                            label = meaning.pos,
                                            value = meaning.definition
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Root explanation
                    if (word.rootExplanation.isNotBlank()) {
                        item {
                            WordDetailSection(title = "词根解释") {
                                Text(
                                    text = word.rootExplanation,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Synonyms
                    if (word.synonyms.isNotEmpty()) {
                        item {
                            WordDetailSection(title = "近义词") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    word.synonyms.forEach { syn ->
                                        DetailRow(
                                            label = syn.word,
                                            value = syn.explanation
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Similar words
                    if (word.similarWords.isNotEmpty()) {
                        item {
                            WordDetailSection(title = "形近词") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    word.similarWords.forEach { sim ->
                                        Column {
                                            DetailRow(label = sim.word, value = sim.meaning)
                                            if (sim.explanation.isNotBlank()) {
                                                Text(
                                                    text = "区分：${sim.explanation}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Cognates
                    if (word.cognates.isNotEmpty()) {
                        item {
                            WordDetailSection(title = "同根词") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    word.cognates.forEach { cog ->
                                        Column {
                                            DetailRow(label = cog.word, value = cog.meaning)
                                            if (cog.sharedRoot.isNotBlank()) {
                                                Text(
                                                    text = "词根：${cog.sharedRoot}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
