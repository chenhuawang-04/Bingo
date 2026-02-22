package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.SentenceAnalysisResult

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceAnalysisSheet(
    sentenceText: String,
    analysis: SentenceAnalysisResult?,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("句子分析", style = MaterialTheme.typography.headlineSmall)

        Text(sentenceText, style = MaterialTheme.typography.bodyMedium)

        if (isLoading) {
            CircularProgressIndicator()
        } else if (error != null) {
            Text("分析失败: $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else if (analysis != null) {
            if (analysis.meaningZh.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("中文翻译", style = MaterialTheme.typography.titleSmall)
                    Text(analysis.meaningZh, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (analysis.grammarPoints.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("语法要点", style = MaterialTheme.typography.titleSmall)
                    analysis.grammarPoints.forEach { point ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(point.title, style = MaterialTheme.typography.labelMedium)
                            Text(point.explanation, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (analysis.keyVocabulary.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("重点词汇", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        analysis.keyVocabulary.forEach { word ->
                            AssistChip(
                                onClick = {},
                                label = { Text("${word.word}: ${word.meaning}") }
                            )
                        }
                    }
                }
            }
        }
    }
}
