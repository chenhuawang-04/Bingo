package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.CollectedWord
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionNotebookSheet(
    collectedWords: List<CollectedWord>,
    dictionaries: List<Dictionary>,
    onLoadUnits: (suspend (dictionaryId: Long) -> List<StudyUnit>)? = null,
    onRemoveWord: (String) -> Unit,
    onAddToDictionary: (word: String, dictionaryId: Long, unitId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var addToDictWord by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.article_notebook_count, collectedWords.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (collectedWords.isEmpty()) {
                Text(
                    stringResource(R.string.article_notebook_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(collectedWords, key = { it.word }) { entry ->
                        CollectedWordCard(
                            entry = entry,
                            onRemove = { onRemoveWord(entry.word) },
                            onAddToDict = { addToDictWord = entry.word }
                        )
                    }
                }
            }
        }
    }

    // Add to dictionary dialog
    addToDictWord?.let { word ->
        AddToDictionaryDialog(
            word = word,
            dictionaries = dictionaries,
            onLoadUnits = onLoadUnits,
            onConfirm = { dictId, unitId ->
                onAddToDictionary(word, dictId, unitId)
                addToDictWord = null
            },
            onDismiss = { addToDictWord = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectedWordCard(
    entry: CollectedWord,
    onRemove: () -> Unit,
    onAddToDict: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = ArticleShapes.Card
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title row: word + remove button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.word,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_remove),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entry.isAnalyzing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.article_analyzing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (entry.analysis != null) {
                val a = entry.analysis

                // Phonetic + POS
                if (a.phonetic.isNotBlank() || a.partOfSpeech.isNotBlank()) {
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        if (a.phonetic.isNotBlank()) {
                            Text(
                                a.phonetic,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (a.partOfSpeech.isNotBlank()) {
                            Text(
                                a.partOfSpeech,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Context meaning
                if (a.contextMeaning.isNotBlank()) {
                    Text(
                        stringResource(R.string.article_context_meaning, a.contextMeaning),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Common meanings
                if (a.commonMeanings.isNotEmpty()) {
                    Text(
                        stringResource(R.string.article_common_meanings, a.commonMeanings.joinToString("; ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Exam importance badge
                if (a.examImportance.isNotBlank()) {
                    FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = ArticleShapes.Chip,
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                a.examImportance,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAddToDict,
                    enabled = !entry.isAnalyzing
                ) {
                    Text(stringResource(R.string.article_add_to_dict), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
