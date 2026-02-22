package com.xty.englishhelper.ui.screen.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.study.Rating
import com.xty.englishhelper.domain.study.formatInterval
import com.xty.englishhelper.ui.adaptive.currentWindowWidthClass
import com.xty.englishhelper.ui.adaptive.isExpandedOrMedium
import com.xty.englishhelper.ui.designsystem.components.EhCard
import com.xty.englishhelper.ui.designsystem.components.EhStatTile
import com.xty.englishhelper.ui.designsystem.components.EhStudyRatingBar
import com.xty.englishhelper.ui.designsystem.components.RatingOption
import com.xty.englishhelper.ui.theme.EhTheme

@Composable
internal fun StudyingContent(
    state: StudyUiState,
    onRevealAnswer: () -> Unit,
    onRate: (Rating) -> Unit,
    modifier: Modifier = Modifier
) {
    val word = state.currentWord ?: return
    val windowWidthClass = currentWindowWidthClass()
    val isWide = windowWidthClass.isExpandedOrMedium()
    val semantic = EhTheme.semanticColors

    val ratingOptions = listOf(
        RatingOption(
            label = "重来",
            intervalText = state.previewIntervals[Rating.Again]?.let { formatInterval(it) },
            color = semantic.studyAgain
        ),
        RatingOption(
            label = "困难",
            intervalText = state.previewIntervals[Rating.Hard]?.let { formatInterval(it) },
            color = semantic.studyHard
        ),
        RatingOption(
            label = "良好",
            intervalText = state.previewIntervals[Rating.Good]?.let { formatInterval(it) },
            color = semantic.studyGood
        ),
        RatingOption(
            label = "简单",
            intervalText = state.previewIntervals[Rating.Easy]?.let { formatInterval(it) },
            color = semantic.studyEasy
        )
    )

    val ratings = listOf(Rating.Again, Rating.Hard, Rating.Good, Rating.Easy)

    if (isWide) {
        Row(modifier = modifier.fillMaxSize()) {
            // Main pane
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                ProgressBar(state)

                if (!state.showAnswer) {
                    QuestionView(
                        spelling = word.spelling,
                        phonetic = word.phonetic,
                        onRevealAnswer = onRevealAnswer,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        wordDetailItems(word)
                    }

                    EhStudyRatingBar(
                        options = ratingOptions,
                        onRate = { index -> onRate(ratings[index]) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }

            VerticalDivider()

            // Side panel
            StudySidePanel(
                state = state,
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            )
        }
    } else {
        // Compact layout
        Column(modifier = modifier.fillMaxSize()) {
            ProgressBar(state)

            if (!state.showAnswer) {
                QuestionView(
                    spelling = word.spelling,
                    phonetic = word.phonetic,
                    onRevealAnswer = onRevealAnswer,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    wordDetailItems(word)
                }

                EhStudyRatingBar(
                    options = ratingOptions,
                    onRate = { index -> onRate(ratings[index]) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(state: StudyUiState) {
    LinearProgressIndicator(
        progress = {
            if (state.total > 0) state.progress.toFloat() / state.total else 0f
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
}

@Composable
private fun QuestionView(
    spelling: String,
    phonetic: String,
    onRevealAnswer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = spelling,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            if (phonetic.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = phonetic,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Button(
        onClick = onRevealAnswer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("显示答案")
    }
}

@Composable
private fun StudySidePanel(
    state: StudyUiState,
    modifier: Modifier = Modifier
) {
    val semantic = EhTheme.semanticColors

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EhCard {
            Text(
                text = "学习进度",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            EhStatTile(
                value = "${state.progress}/${state.total}",
                label = "当前进度",
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.showAnswer) {
            EhCard {
                Text(
                    text = "下一间隔预览",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.previewIntervals.forEach { (rating, interval) ->
                        val label = when (rating) {
                            Rating.Again -> "重来"
                            Rating.Hard -> "困难"
                            Rating.Good -> "良好"
                            Rating.Easy -> "简单"
                        }
                        val color = when (rating) {
                            Rating.Again -> semantic.studyAgain
                            Rating.Hard -> semantic.studyHard
                            Rating.Good -> semantic.studyGood
                            Rating.Easy -> semantic.studyEasy
                        }
                        EhStatTile(
                            value = formatInterval(interval),
                            label = label,
                            valueColor = color,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        EhCard {
            Text(
                text = "已学统计",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatRow("重来", state.stats.againCount.toString(), semantic.studyAgain)
                StatRow("困难", state.stats.hardCount.toString(), semantic.studyHard)
                StatRow("良好", state.stats.goodCount.toString(), semantic.studyGood)
                StatRow("简单", state.stats.easyCount.toString(), semantic.studyEasy)
            }
        }
    }
}

@Composable
internal fun FinishedContent(
    stats: StudyStats,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = EhTheme.semanticColors
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
            StatRow("重来", stats.againCount.toString(), semantic.studyAgain)
            StatRow("困难", stats.hardCount.toString(), semantic.studyHard)
            StatRow("良好", stats.goodCount.toString(), semantic.studyGood)
            StatRow("简单", stats.easyCount.toString(), semantic.studyEasy)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDone) {
            Text("完成")
        }
    }
}
