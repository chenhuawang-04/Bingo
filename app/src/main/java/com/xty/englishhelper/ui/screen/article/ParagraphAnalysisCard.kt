package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParagraphAnalysisCard(
    analysis: ParagraphAnalysisResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sentence breakdowns
            if (analysis.sentenceBreakdowns.isNotEmpty()) {
                Text("逐句翻译", style = MaterialTheme.typography.labelMedium)
                analysis.sentenceBreakdowns.forEach { breakdown ->
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            breakdown.sentence,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            breakdown.translation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (breakdown.grammarNotes.isNotBlank()) {
                            Text(
                                breakdown.grammarNotes,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            } else if (analysis.meaningZh.isNotBlank()) {
                Text(
                    analysis.meaningZh,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Grammar points
            if (analysis.grammarPoints.isNotEmpty()) {
                HorizontalDivider()
                Text("语法要点", style = MaterialTheme.typography.labelMedium)
                analysis.grammarPoints.forEach { point ->
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("•", style = MaterialTheme.typography.bodySmall)
                        Column {
                            Text(
                                point.title,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (point.explanation.isNotBlank()) {
                                Text(
                                    point.explanation,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Key vocabulary
            if (analysis.keyVocabulary.isNotEmpty()) {
                HorizontalDivider()
                Text("重点词汇", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    analysis.keyVocabulary.forEach { keyword ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    "${keyword.word} ${keyword.meaning}",
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
