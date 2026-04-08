package com.xty.englishhelper.ui.components.dictionary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.QuickDictionaryEntry
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.screen.dictionary.QuickDictionaryViewModel
import com.xty.englishhelper.ui.screen.dictionary.QuickLookupMode
import com.xty.englishhelper.ui.screen.dictionary.QuickLookupSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDictionarySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: QuickDictionaryViewModel
) {
    if (!visible) return

    val state by viewModel.uiState.collectAsState()
    var selectedEntry by remember { mutableStateOf<QuickDictionaryEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val maxHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp - 96.dp).coerceIn(420.dp, 760.dp)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("快捷查词", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "支持中->英候选词 + Cambridge/OED 词条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickLookupMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickLookupSource.entries.forEach { source ->
                    AssistChip(
                        onClick = { viewModel.setSource(source) },
                        label = { Text(source.label) },
                        colors = if (state.source == source) {
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        }
                    )
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = {
                    Text(
                        if (state.mode == QuickLookupMode.ZH_TO_EN) "输入中文含义（如：白色的）"
                        else "输入英语单词（如：white）"
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.submitQuery()
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (state.isLoading || state.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(18.dp)
                                .height(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = viewModel::submitQuery,
                            enabled = state.query.isNotBlank()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    }
                }
            )

            if (state.error != null) {
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.suggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "候选词",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 132.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.suggestions, key = { it }) { suggestion ->
                            Surface(
                                shape = ArticleShapes.Control,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Text(
                                    text = suggestion,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectSuggestion(suggestion) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            if (state.groups.isNotEmpty()) {
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.groups, key = { it.word }) { group ->
                        GroupCard(
                            word = group.word,
                            hint = group.hint,
                            entries = group.entries,
                            expanded = group.expanded,
                            onToggle = { viewModel.toggleGroupExpanded(group.word) },
                            onEntryClick = { selectedEntry = it }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    selectedEntry?.let { entry ->
        EntryDetailDialog(entry = entry, onDismiss = { selectedEntry = null })
    }
}

@Composable
private fun GroupCard(
    word: String,
    hint: String?,
    entries: List<QuickDictionaryEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEntryClick: (QuickDictionaryEntry) -> Unit
) {
    Surface(
        shape = ArticleShapes.Control,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!hint.isNullOrBlank()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${entries.size} 个词条版本",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }

            entries.take(2).forEach { entry ->
                EntrySummaryRow(entry = entry, onClick = { onEntryClick(entry) })
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    entries.drop(2).forEach { entry ->
                        EntrySummaryRow(entry = entry, onClick = { onEntryClick(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EntrySummaryRow(entry: QuickDictionaryEntry, onClick: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Surface(shape = ArticleShapes.Control, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.headword,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (entry.variant.isNotBlank()) {
                        Text(
                            text = entry.variant,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                TextButton(onClick = { uriHandler.openUri(entry.sourceUrl) }) {
                    Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(entry.sourceLabel)
                }
            }

            if (!entry.pronunciation.isNullOrBlank()) {
                Text(
                    text = entry.pronunciation ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val summary = entry.summary.ifBlank { entry.senses.firstOrNull().orEmpty() }
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.senses.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entry.senses.take(4).forEachIndexed { index, sense ->
                        Text(
                            text = "${index + 1}. $sense",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryDetailDialog(
    entry: QuickDictionaryEntry,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = entry.headword,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (entry.variant.isNotBlank()) {
                    Text(
                        text = entry.variant,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "来源：${entry.sourceLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!entry.pronunciation.isNullOrBlank()) {
                    Text(entry.pronunciation ?: "", style = MaterialTheme.typography.bodyMedium)
                }
                if (!entry.timeRange.isNullOrBlank()) {
                    Text("时间范围：${entry.timeRange}", style = MaterialTheme.typography.bodySmall)
                }
                if (entry.tags.isNotEmpty()) {
                    Text("标签：${entry.tags.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall)
                }
                if (!entry.etymologySummary.isNullOrBlank()) {
                    Text("词源：${entry.etymologySummary}", style = MaterialTheme.typography.bodySmall)
                }
                if (entry.summary.isNotBlank()) {
                    Text("摘要：${entry.summary}", style = MaterialTheme.typography.bodyMedium)
                }
                entry.senses.forEachIndexed { index, sense ->
                    Text("${index + 1}. $sense", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { uriHandler.openUri(entry.sourceUrl) }) {
                Text("打开原词典")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
