package com.xty.englishhelper.ui.screen.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.ui.components.DetailRow
import com.xty.englishhelper.ui.components.WordDetailSection

@Composable
internal fun StatRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
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
            color = valueColor
        )
    }
}

internal fun LazyListScope.wordDetailItems(word: WordDetails) {
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
