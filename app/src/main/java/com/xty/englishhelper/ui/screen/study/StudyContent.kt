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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.R
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
    onOpenRelatedWord: (Long, Long) -> Unit,
    onWordNoteInputChange: (String) -> Unit,
    onWordNoteSuggestionSelected: (String) -> Unit,
    onWordNoteSuggestionsExpandedChange: (Boolean) -> Unit,
    onSubmitWordNote: () -> Unit,
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
            label = stringResource(R.string.study_again),
            intervalText = state.previewIntervals[Rating.Again]?.let { formatInterval(it) },
            color = semantic.studyAgain
        ),
        RatingOption(
            label = stringResource(R.string.study_hard),
            intervalText = state.previewIntervals[Rating.Hard]?.let { formatInterval(it) },
            color = semantic.studyHard
        ),
        RatingOption(
            label = stringResource(R.string.study_good),
            intervalText = state.previewIntervals[Rating.Good]?.let { formatInterval(it) },
            color = semantic.studyGood
        ),
        RatingOption(
            label = stringResource(R.string.study_easy),
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

                if (!state.showAnswer) {
                    QuestionView(
                        spelling = word.spelling,
                        phonetic = word.phonetic,
                        relatedEdges = state.currentWordEdges,
                        wordNoteEnabled = state.wordNoteEnabled,
                        wordNoteInput = state.wordNoteInput,
                        wordNoteSuggestions = state.wordNoteSuggestions,
                        wordNoteSuggestionsLoading = state.wordNoteSuggestionsLoading,
                        wordNoteSuggestionsExpanded = state.wordNoteSuggestionsExpanded,
                        wordNoteSubmitting = state.wordNoteSubmitting,
                        wordNoteMessage = state.wordNoteMessage,
                        wordNoteError = state.wordNoteError,
                        onWordNoteInputChange = onWordNoteInputChange,
                        onWordNoteSuggestionSelected = onWordNoteSuggestionSelected,
                        onWordNoteSuggestionsExpandedChange = onWordNoteSuggestionsExpandedChange,
                        onSubmitWordNote = onSubmitWordNote,
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
                        item {
                            BrainstormTag(
                                state = state,
                                onOpenRelatedWord = onOpenRelatedWord
                            )
                        }
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

            if (!state.showAnswer) {
                QuestionView(
                    spelling = word.spelling,
                    phonetic = word.phonetic,
                    relatedEdges = state.currentWordEdges,
                    wordNoteEnabled = state.wordNoteEnabled,
                    wordNoteInput = state.wordNoteInput,
                    wordNoteSuggestions = state.wordNoteSuggestions,
                    wordNoteSuggestionsLoading = state.wordNoteSuggestionsLoading,
                    wordNoteSuggestionsExpanded = state.wordNoteSuggestionsExpanded,
                    wordNoteSubmitting = state.wordNoteSubmitting,
                    wordNoteMessage = state.wordNoteMessage,
                    wordNoteError = state.wordNoteError,
                    onWordNoteInputChange = onWordNoteInputChange,
                    onWordNoteSuggestionSelected = onWordNoteSuggestionSelected,
                    onWordNoteSuggestionsExpandedChange = onWordNoteSuggestionsExpandedChange,
                    onSubmitWordNote = onSubmitWordNote,
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
                    item {
                        BrainstormTag(
                            state = state,
                            onOpenRelatedWord = onOpenRelatedWord
                        )
                    }
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

    // 阶段D：当前学习簇的掌握进度——面包屑进度点（仅在多词簇时显示）。
    if (state.studyMode == com.xty.englishhelper.domain.model.StudyMode.BRAINSTORM &&
        state.brainstormClusterTotal > 1
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.study_group_progress, state.brainstormClusterLearned, state.brainstormClusterTotal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            ClusterDots(
                learned = state.brainstormClusterLearned,
                total = state.brainstormClusterTotal
            )
        }
    }
}

@Composable
private fun BrainstormTag(
    state: StudyUiState,
    onOpenRelatedWord: (Long, Long) -> Unit
) {
    val edges = state.currentWordEdges
    val spellings = state.currentWordRelatedSpellings
    val dictionaryId = state.currentWord?.dictionaryId ?: return

    val hasEdges = edges.isNotEmpty()
    val hasSpellings = spellings.isNotEmpty()

    if (!hasEdges && !hasSpellings) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.study_related_words),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (hasEdges) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(edges) { edge ->
                    val color = edgeTypeColor(edge.edgeType)
                    Surface(
                        onClick = { onOpenRelatedWord(edge.wordId, dictionaryId) },
                        shape = MaterialTheme.shapes.small,
                        color = color.copy(alpha = 0.15f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = edge.spelling,
                                style = MaterialTheme.typography.labelMedium,
                                color = color
                            )
                            Text(
                                text = edge.edgeType.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = color.copy(alpha = 0.7f)
                            )
                        }
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
    relatedEdges: List<WordEdgePreview>,
    wordNoteEnabled: Boolean,
    wordNoteInput: String,
    wordNoteSuggestions: List<String>,
    wordNoteSuggestionsLoading: Boolean,
    wordNoteSuggestionsExpanded: Boolean,
    wordNoteSubmitting: Boolean,
    wordNoteMessage: String?,
    wordNoteError: String?,
    onWordNoteInputChange: (String) -> Unit,
    onWordNoteSuggestionSelected: (String) -> Unit,
    onWordNoteSuggestionsExpandedChange: (Boolean) -> Unit,
    onSubmitWordNote: () -> Unit,
    onRevealAnswer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            // 阶段D：中心辐射星图——展示当前词的关联词（仅头脑风暴有边时）。
            if (relatedEdges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                WordConstellation(nodes = relatedEdges)
            }
            if (wordNoteEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.study_word_note_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                WordNoteInputField(
                    value = wordNoteInput,
                    suggestions = wordNoteSuggestions,
                    suggestionsLoading = wordNoteSuggestionsLoading,
                    suggestionsExpanded = wordNoteSuggestionsExpanded,
                    enabled = !wordNoteSubmitting,
                    onValueChange = onWordNoteInputChange,
                    onSuggestionSelected = onWordNoteSuggestionSelected,
                    onSuggestionsExpandedChange = onWordNoteSuggestionsExpandedChange,
                    onSubmit = onSubmitWordNote,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onSubmitWordNote,
                    enabled = wordNoteInput.isNotBlank() && !wordNoteSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (wordNoteSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(stringResource(R.string.study_word_note_submit))
                }
                val feedback = wordNoteError ?: wordNoteMessage
                if (!feedback.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (wordNoteError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
    Button(
        onClick = onRevealAnswer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.study_show_answer))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordNoteInputField(
    value: String,
    suggestions: List<String>,
    suggestionsLoading: Boolean,
    suggestionsExpanded: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onSuggestionsExpandedChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = suggestionsExpanded && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { nextExpanded ->
            if (suggestions.isNotEmpty()) {
                onSuggestionsExpandedChange(nextExpanded)
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.study_word_note_label)) },
            placeholder = { Text(stringResource(R.string.study_word_note_placeholder)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (value.isNotBlank() && enabled) {
                        onSubmit()
                    }
                }
            ),
            trailingIcon = {
                when {
                    suggestionsLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    suggestions.isNotEmpty() -> ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onSuggestionsExpandedChange(false) }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(text = suggestion) },
                    onClick = { onSuggestionSelected(suggestion) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
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
                text = stringResource(R.string.study_progress),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            EhStatTile(
                value = "${state.progress}/${state.total}",
                label = stringResource(R.string.study_current_progress),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.showAnswer) {
            EhCard {
                Text(
                    text = stringResource(R.string.study_next_interval_preview),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.previewIntervals.forEach { (rating, interval) ->
                        val label = when (rating) {
                            Rating.Again -> stringResource(R.string.study_again)
                            Rating.Hard -> stringResource(R.string.study_hard)
                            Rating.Good -> stringResource(R.string.study_good)
                            Rating.Easy -> stringResource(R.string.study_easy)
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
                text = stringResource(R.string.study_learned_stats),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatRow(stringResource(R.string.study_again), state.stats.againCount.toString(), semantic.studyAgain)
                StatRow(stringResource(R.string.study_hard), state.stats.hardCount.toString(), semantic.studyHard)
                StatRow(stringResource(R.string.study_good), state.stats.goodCount.toString(), semantic.studyGood)
                StatRow(stringResource(R.string.study_easy), state.stats.easyCount.toString(), semantic.studyEasy)
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
            text = stringResource(R.string.study_complete),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (stats.totalWords == 0) {
            Text(
                text = stringResource(R.string.study_no_words),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatRow(stringResource(R.string.study_total_words), stats.totalWords.toString())
            StatRow(stringResource(R.string.study_again), stats.againCount.toString(), semantic.studyAgain)
            StatRow(stringResource(R.string.study_hard), stats.hardCount.toString(), semantic.studyHard)
            StatRow(stringResource(R.string.study_good), stats.goodCount.toString(), semantic.studyGood)
            StatRow(stringResource(R.string.study_easy), stats.easyCount.toString(), semantic.studyEasy)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onDone) {
            Text(stringResource(R.string.study_done))
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
            text = stringResource(R.string.study_active_recall),
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
            text = stringResource(R.string.study_select_relation, stringResource(quiz.relationLabel)),
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
                text = if (quiz.isCorrect) stringResource(R.string.study_correct_answer) else stringResource(R.string.study_wrong_answer),
                style = MaterialTheme.typography.bodyMedium,
                color = if (quiz.isCorrect) semantic.studyGood else semantic.studyAgain
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_resume))
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
                text = stringResource(R.string.study_memory_hook, stringResource(hook.relationLabel)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.study_related_word_prefix, hook.relatedSpelling),
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
                    text = stringResource(R.string.study_example_prefix, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}
