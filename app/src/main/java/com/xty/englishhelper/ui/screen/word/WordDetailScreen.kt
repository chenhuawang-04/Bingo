package com.xty.englishhelper.ui.screen.word

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.ui.components.ConfirmDialog
import com.xty.englishhelper.ui.components.DetailRow
import com.xty.englishhelper.ui.components.LoadingIndicator
import com.xty.englishhelper.ui.components.WordDetailSection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordDetailScreen(
    onBack: () -> Unit,
    onEdit: (dictionaryId: Long, wordId: Long) -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
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

                    // Root explanation (with decomposition)
                    if (word.decomposition.isNotEmpty() || word.rootExplanation.isNotBlank()) {
                        item {
                            WordDetailSection(title = "词根解释") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (word.decomposition.isNotEmpty()) {
                                        DecompositionDisplay(parts = word.decomposition)
                                    }
                                    if (word.rootExplanation.isNotBlank()) {
                                        if (word.decomposition.isNotEmpty()) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                        Text(
                                            text = word.rootExplanation,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Synonyms
                    if (word.synonyms.isNotEmpty()) {
                        item {
                            WordDetailSection(title = "近义词") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    word.synonyms.forEach { syn ->
                                        ClickableWordRow(
                                            word = syn.word,
                                            detail = syn.explanation,
                                            linkedWordIds = state.linkedWordIds,
                                            dictionaryId = word.dictionaryId,
                                            onWordClick = onWordClick
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
                                            ClickableWordRow(
                                                word = sim.word,
                                                detail = sim.meaning,
                                                linkedWordIds = state.linkedWordIds,
                                                dictionaryId = word.dictionaryId,
                                                onWordClick = onWordClick
                                            )
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
                                            ClickableWordRow(
                                                word = cog.word,
                                                detail = cog.meaning,
                                                linkedWordIds = state.linkedWordIds,
                                                dictionaryId = word.dictionaryId,
                                                onWordClick = onWordClick
                                            )
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

                    // Associated words
                    if (state.associatedWords.isNotEmpty()) {
                        item {
                            WordDetailSection(title = "联想词") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.associatedWords.forEach { assoc ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = assoc.spelling,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textDecoration = TextDecoration.Underline
                                                ),
                                                modifier = Modifier
                                                    .clickable {
                                                        onWordClick(assoc.wordId, word.dictionaryId)
                                                    }
                                                    .padding(top = 2.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                assoc.commonSegments.forEach { seg ->
                                                    AssistChip(
                                                        onClick = {},
                                                        label = {
                                                            Text(
                                                                seg,
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecompositionDisplay(parts: List<DecompositionPart>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = roleLabel(part.role),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (part.role == MorphemeRole.ROOT) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = part.segment,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (part.role == MorphemeRole.ROOT) FontWeight.Bold else FontWeight.Normal,
                        color = if (part.role == MorphemeRole.ROOT) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                )
                if (part.meaning.isNotBlank()) {
                    Text(
                        text = part.meaning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ClickableWordRow(
    word: String,
    detail: String,
    linkedWordIds: Map<String, Long>,
    dictionaryId: Long,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit
) {
    val normalized = word.trim().lowercase()
    val linkedId = linkedWordIds[normalized]

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (linkedId != null) {
            Text(
                text = word,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                ),
                modifier = Modifier
                    .clickable { onWordClick(linkedId, dictionaryId) }
                    .padding(top = 2.dp)
            )
        } else {
            Text(
                text = word,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun roleLabel(role: MorphemeRole): String = when (role) {
    MorphemeRole.PREFIX -> "前缀"
    MorphemeRole.ROOT -> "词根"
    MorphemeRole.SUFFIX -> "后缀"
    MorphemeRole.STEM -> "词干"
    MorphemeRole.LINKING -> "连接"
    MorphemeRole.OTHER -> "其他"
}
