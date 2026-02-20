package com.xty.englishhelper.ui.screen.study

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.ui.components.DetailRow
import com.xty.englishhelper.ui.components.LoadingIndicator
import com.xty.englishhelper.ui.components.WordDetailSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onBack: () -> Unit,
    viewModel: StudyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.phase == StudyPhase.Studying) {
                        Text("${state.progress}/${state.total}")
                    } else {
                        Text("学习完成")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            )
        }
    ) { padding ->
        when (state.phase) {
            StudyPhase.Loading -> {
                LoadingIndicator(Modifier.padding(padding))
            }

            StudyPhase.Studying -> {
                StudyingContent(
                    state = state,
                    onKnown = viewModel::onKnown,
                    onUnknown = viewModel::onUnknown,
                    onNext = viewModel::onNext,
                    modifier = Modifier.padding(padding)
                )
            }

            StudyPhase.Finished -> {
                FinishedContent(
                    stats = state.stats,
                    onDone = onBack,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun StudyingContent(
    state: StudyUiState,
    onKnown: () -> Unit,
    onUnknown: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val word = state.currentWord ?: return

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Progress bar
        LinearProgressIndicator(
            progress = {
                if (state.total > 0) state.progress.toFloat() / state.total else 0f
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        if (!state.showAnswer) {
            // Question mode: show word only
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = word.spelling,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center
                    )
                    if (word.phonetic.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = word.phonetic,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onUnknown,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("不认识")
                }
                Button(
                    onClick = onKnown,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("认识")
                }
            }
        } else {
            // Answer mode: show word details
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = word.spelling,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                if (word.phonetic.isNotBlank()) {
                    item {
                        Text(
                            text = word.phonetic,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (word.meanings.isNotEmpty()) {
                    item {
                        WordDetailSection(title = "词性与词义") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                word.meanings.forEach { meaning ->
                                    DetailRow(label = meaning.pos, value = meaning.definition)
                                }
                            }
                        }
                    }
                }

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

                if (word.synonyms.isNotEmpty()) {
                    item {
                        WordDetailSection(title = "近义词") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                word.synonyms.forEach { syn ->
                                    DetailRow(label = syn.word, value = syn.explanation)
                                }
                            }
                        }
                    }
                }

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

            // Next button
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("下一个")
            }
        }
    }
}

@Composable
private fun FinishedContent(
    stats: StudyStats,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "学习完成",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (stats.totalWords == 0) {
            Text(
                text = "没有需要学习的单词",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatRow("总计单词", stats.totalWords.toString())
            StatRow("认识", stats.knownCount.toString())
            StatRow("不认识", stats.unknownCount.toString())
            StatRow("已掌握", stats.masteredCount.toString())
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDone) {
            Text("完成")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
