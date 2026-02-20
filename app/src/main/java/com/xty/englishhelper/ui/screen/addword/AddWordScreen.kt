package com.xty.englishhelper.ui.screen.addword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.SynonymInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordScreen(
    onBack: () -> Unit,
    viewModel: AddWordViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑单词" else "添加单词") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.isSaving && state.spelling.isNotBlank()
                    ) {
                        Text("保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Spelling + AI button
            item {
                OutlinedTextField(
                    value = state.spelling,
                    onValueChange = viewModel::onSpellingChange,
                    label = { Text("单词拼写") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = viewModel::organizeWithAi,
                    enabled = !state.isAiLoading && state.spelling.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isAiLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("  正在整理…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Text("  AI 自动整理")
                    }
                }
            }

            // Phonetic
            item {
                OutlinedTextField(
                    value = state.phonetic,
                    onValueChange = viewModel::onPhoneticChange,
                    label = { Text("音标") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Meanings
            item {
                SectionHeader(title = "词性与词义", onAdd = viewModel::addMeaning)
            }
            itemsIndexed(state.meanings) { index, meaning ->
                MeaningRow(
                    meaning = meaning,
                    onPosChange = { viewModel.onMeaningChange(index, meaning.copy(pos = it)) },
                    onDefChange = { viewModel.onMeaningChange(index, meaning.copy(definition = it)) },
                    onRemove = { viewModel.removeMeaning(index) },
                    showRemove = state.meanings.size > 1
                )
            }

            // Root explanation
            item {
                OutlinedTextField(
                    value = state.rootExplanation,
                    onValueChange = viewModel::onRootExplanationChange,
                    label = { Text("词根解释") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Synonyms
            item {
                SectionHeader(title = "近义词", onAdd = viewModel::addSynonym)
            }
            itemsIndexed(state.synonyms) { index, syn ->
                SynonymRow(
                    synonym = syn,
                    onWordChange = { viewModel.onSynonymChange(index, syn.copy(word = it)) },
                    onExplanationChange = { viewModel.onSynonymChange(index, syn.copy(explanation = it)) },
                    onRemove = { viewModel.removeSynonym(index) }
                )
            }

            // Similar words
            item {
                SectionHeader(title = "形近词", onAdd = viewModel::addSimilarWord)
            }
            itemsIndexed(state.similarWords) { index, sim ->
                SimilarWordRow(
                    similarWord = sim,
                    onWordChange = { viewModel.onSimilarWordChange(index, sim.copy(word = it)) },
                    onMeaningChange = { viewModel.onSimilarWordChange(index, sim.copy(meaning = it)) },
                    onExplanationChange = { viewModel.onSimilarWordChange(index, sim.copy(explanation = it)) },
                    onRemove = { viewModel.removeSimilarWord(index) }
                )
            }

            // Cognates
            item {
                SectionHeader(title = "同根词", onAdd = viewModel::addCognate)
            }
            itemsIndexed(state.cognates) { index, cog ->
                CognateRow(
                    cognate = cog,
                    onWordChange = { viewModel.onCognateChange(index, cog.copy(word = it)) },
                    onMeaningChange = { viewModel.onCognateChange(index, cog.copy(meaning = it)) },
                    onSharedRootChange = { viewModel.onCognateChange(index, cog.copy(sharedRoot = it)) },
                    onRemove = { viewModel.removeCognate(index) }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" 添加")
        }
    }
}

@Composable
private fun MeaningRow(
    meaning: Meaning,
    onPosChange: (String) -> Unit,
    onDefChange: (String) -> Unit,
    onRemove: () -> Unit,
    showRemove: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = meaning.pos,
            onValueChange = onPosChange,
            label = { Text("词性") },
            singleLine = true,
            modifier = Modifier.weight(0.3f)
        )
        OutlinedTextField(
            value = meaning.definition,
            onValueChange = onDefChange,
            label = { Text("释义") },
            singleLine = true,
            modifier = Modifier.weight(0.6f)
        )
        if (showRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun SynonymRow(
    synonym: SynonymInfo,
    onWordChange: (String) -> Unit,
    onExplanationChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = synonym.word,
            onValueChange = onWordChange,
            label = { Text("近义词") },
            singleLine = true,
            modifier = Modifier.weight(0.35f)
        )
        OutlinedTextField(
            value = synonym.explanation,
            onValueChange = onExplanationChange,
            label = { Text("区分说明") },
            singleLine = true,
            modifier = Modifier.weight(0.55f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Default.Close, contentDescription = "删除")
        }
    }
}

@Composable
private fun SimilarWordRow(
    similarWord: SimilarWordInfo,
    onWordChange: (String) -> Unit,
    onMeaningChange: (String) -> Unit,
    onExplanationChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = similarWord.word,
                onValueChange = onWordChange,
                label = { Text("形近词") },
                singleLine = true,
                modifier = Modifier.weight(0.35f)
            )
            OutlinedTextField(
                value = similarWord.meaning,
                onValueChange = onMeaningChange,
                label = { Text("含义") },
                singleLine = true,
                modifier = Modifier.weight(0.55f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "删除")
            }
        }
        OutlinedTextField(
            value = similarWord.explanation,
            onValueChange = onExplanationChange,
            label = { Text("区分方法") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CognateRow(
    cognate: CognateInfo,
    onWordChange: (String) -> Unit,
    onMeaningChange: (String) -> Unit,
    onSharedRootChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = cognate.word,
                onValueChange = onWordChange,
                label = { Text("同根词") },
                singleLine = true,
                modifier = Modifier.weight(0.35f)
            )
            OutlinedTextField(
                value = cognate.meaning,
                onValueChange = onMeaningChange,
                label = { Text("含义") },
                singleLine = true,
                modifier = Modifier.weight(0.55f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "删除")
            }
        }
        OutlinedTextField(
            value = cognate.sharedRoot,
            onValueChange = onSharedRootChange,
            label = { Text("共同词根") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
