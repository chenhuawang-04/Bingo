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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.study.Rating
import com.xty.englishhelper.domain.study.formatInterval
import com.xty.englishhelper.ui.adaptive.currentWindowWidthClass
import com.xty.englishhelper.ui.adaptive.isExpandedOrMedium
import com.xty.englishhelper.ui.components.pool.edgeTypeColor
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
    onCloudExampleSourceSelected: (CloudExampleSource) -> Unit,
    onQuizAnswer: (Long) -> Unit,
    onQuizContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val word = state.currentWord ?: return

    // 阶段C：关联主动回忆选择题——开启且当前词符合条件时，替代翻卡流程。
    state.brainstormQuiz?.let { quiz ->
        BrainstormQuizContent(
            quiz = quiz,
            onAnswer = onQuizAnswer,
            onContinue = onQuizContinue,
            modifier = modifier
        )
        return
    }
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
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            ) {
                ProgressBar(state)
                BrainstormTag(state)

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
                        state.currentWordHook?.let { hook -> item { HookCard(hook) } }
                        wordDetailItems(
                            word = word,
                            cloudExampleSource = state.cloudExampleSource,
                            cloudExamples = state.cloudExamples,
                            cloudExamplesLoading = state.cloudExamplesLoading,
                            cloudExamplesError = state.cloudExamplesError,
                            onCloudExampleSourceSelected = onCloudExampleSourceSelected
                        )
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

            StudySidePanel(
                state = state,
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            ProgressBar(state)
            BrainstormTag(state)

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
                    state.currentWordHook?.let { hook -> item { HookCard(hook) } }
                    wordDetailItems(
                        word = word,
                        cloudExampleSource = state.cloudExampleSource,
                        cloudExamples = state.cloudExamples,
                        cloudExamplesLoading = state.cloudExamplesLoading,
                        cloudExamplesError = state.cloudExamplesError,
                        onCloudExampleSourceSelected = onCloudExampleSourceSelected
                    )
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
    val progress = if (state.studyMode == com.xty.englishhelper.domain.model.StudyMode.BRAINSTORM
        && state.brainstormTargetCount > 0) {
        state.brainstormLearnedCount.toFloat() / state.brainstormTargetCount
    } else {
        if (state.total > 0) state.progress.toFloat() / state.total else 0f
    }

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )

    // 阶段C：当前学习簇的掌握进度（仅在多词簇时显示）。
    if (state.studyMode == com.xty.englishhelper.domain.model.StudyMode.BRAINSTORM &&
        state.brainstormClusterTotal > 1
    ) {
        Text(
            text = "本组 ${state.brainstormClusterLearned}/${state.brainstormClusterTotal} 已掌握",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun BrainstormTag(state: StudyUiState) {
    val edges = state.currentWordEdges
    val spellings = state.currentWordRelatedSpellings

    val hasEdges = edges.isNotEmpty()
    val hasSpellings = spellings.isNotEmpty()

    if (!hasEdges && !hasSpellings) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = "关联词：",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (hasEdges) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(edges) { edge ->
                    val color = edgeTypeColor(edge.edgeType)
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = edge.spelling,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                spellings.forEach { spelling ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = spelling,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
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

/** 阶段C：关联主动回忆选择题——从同组词中选出当前词的正确关联词。 */
@Composable
private fun BrainstormQuizContent(
    quiz: BrainstormQuiz,
    onAnswer: (Long) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = EhTheme.semanticColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "主动回忆",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = quiz.targetSpelling,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "选出它的「${quiz.relationLabel}」",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            quiz.options.forEach { option ->
                val isCorrect = option.wordId == quiz.correctWordId
                val isSelected = option.wordId == quiz.selectedWordId
                val bg = when {
                    !quiz.answered -> MaterialTheme.colorScheme.surfaceVariant
                    isCorrect -> semantic.studyGood.copy(alpha = 0.20f)
                    isSelected -> semantic.studyAgain.copy(alpha = 0.20f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val fg = when {
                    !quiz.answered -> MaterialTheme.colorScheme.onSurface
                    isCorrect -> semantic.studyGood
                    isSelected -> semantic.studyAgain
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    onClick = { if (!quiz.answered) onAnswer(option.wordId) },
                    enabled = !quiz.answered,
                    shape = MaterialTheme.shapes.medium,
                    color = bg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = option.spelling,
                        style = MaterialTheme.typography.titleMedium,
                        color = fg,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (quiz.answered) {
            Text(
                text = if (quiz.isCorrect) "答对了，记为「良好」" else "答错了，记为「重来」，稍后再练",
                style = MaterialTheme.typography.bodyMedium,
                color = if (quiz.isCorrect) semantic.studyGood else semantic.studyAgain
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("继续")
            }
        }
    }
}

/** 阶段C：揭示答案时的记忆联想卡——展示最强关联词及其关系依据 / 例句。 */
@Composable
private fun HookCard(hook: BrainstormHook) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "记忆联想 · ${hook.relationLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "关联词：${hook.relatedSpelling}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            hook.reason?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            hook.example?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "例：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}
