package com.xty.englishhelper.ui.components.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.CambridgeEntry
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.screen.dictionary.QuickDictionaryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDictionarySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: QuickDictionaryViewModel
) {
    if (!visible) return

    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val maxHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp - 120.dp).coerceIn(360.dp, 600.dp)
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
                Text("快捷词典", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("输入单词") },
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
                    if (state.isSearching || state.isLoadingEntry) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = { viewModel.submitQuery() },
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
                    Text("候选词", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 180.dp),
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
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            state.entry?.let { entry ->
                HorizontalDivider()
                EntryView(entry = entry)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EntryView(entry: CambridgeEntry) {
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.headword, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (entry.partOfSpeech.isNotBlank()) {
                Text(
                    entry.partOfSpeech,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        entry.pronunciation?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (entry.senses.isEmpty()) {
            Text("未找到释义", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.senses.forEachIndexed { index, sense ->
                    SenseRow(index = index + 1, sense = sense)
                }
            }
        }

        TextButton(onClick = { uriHandler.openUri(entry.sourceUrl) }) {
            Text("打开 Cambridge 页面")
        }
    }
}

@Composable
private fun SenseRow(index: Int, sense: com.xty.englishhelper.domain.model.CambridgeSense) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$index. ${sense.definition}", style = MaterialTheme.typography.bodyMedium)
        if (!sense.translation.isNullOrBlank()) {
            Text(
                sense.translation ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
