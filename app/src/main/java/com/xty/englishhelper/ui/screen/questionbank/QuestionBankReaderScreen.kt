package com.xty.englishhelper.ui.screen.questionbank

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.SourceVerifyStatus
import com.xty.englishhelper.ui.components.reading.ParagraphBlock
import com.xty.englishhelper.ui.components.reading.TtsPlaybackBar
import com.xty.englishhelper.ui.screen.article.CollectionNotebookSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionBankReaderScreen(
    onBack: () -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    onViewArticle: ((articleId: Long) -> Unit)? = null,
    viewModel: QuestionBankReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var followTts by rememberSaveable { mutableStateOf(true) }
    var showSourceEditor by remember { mutableStateOf(false) }
    var sourceUrlDraft by remember { mutableStateOf("") }

    val answerImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.scanAnswerImages(uris)
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.ttsState.error) {
        state.ttsState.error?.let {
            snackbarHostState.showSnackbar("TTS: $it")
            viewModel.clearTtsError()
        }
    }

    // TTS scroll follow
    val ttsSessionId = state.ttsState.sessionId
    val ttsActive = state.ttsState.isSpeaking && ttsSessionId?.contains("article:") == true
    LaunchedEffect(ttsActive, state.ttsState.currentIndex, state.paragraphs.size, followTts) {
        if (!ttsActive || !followTts) return@LaunchedEffect
        val speakableParagraphs = state.paragraphs.filter { it.text.isNotBlank() }
        val currentIdx = state.ttsState.currentIndex
        if (currentIdx < 0 || currentIdx >= speakableParagraphs.size) return@LaunchedEffect
        val targetPara = speakableParagraphs[currentIdx]
        val paraIndex = state.paragraphs.indexOf(targetPara)
        if (paraIndex >= 0) {
            // header(1) + paragraphs before this one
            listState.animateScrollToItem(1 + paraIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.group?.sectionLabel ?: "阅读",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // TTS toggle
                    IconButton(onClick = { viewModel.toggleSpeakArticle() }) {
                        Icon(
                            if (ttsActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (ttsActive) "暂停" else "朗读"
                        )
                    }
                    // Translation toggle
                    IconButton(onClick = { viewModel.toggleTranslation() }) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = "翻译",
                            tint = if (state.translationEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Notebook
                    IconButton(onClick = { viewModel.toggleNotebook() }) {
                        Icon(Icons.Outlined.CollectionsBookmark, contentDescription = "收纳本")
                        if (state.collectedWords.isNotEmpty()) {
                            Badge { Text("${state.collectedWords.size}") }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (ttsActive) {
                TtsPlaybackBar(
                    isSpeaking = state.ttsState.isSpeaking,
                    currentIndex = state.ttsState.currentIndex,
                    total = state.ttsState.total,
                    followEnabled = followTts,
                    onToggleFollow = { followTts = !followTts },
                    onPlayPause = { viewModel.toggleSpeakArticle() },
                    onPrev = { viewModel.previousParagraph() },
                    onNext = { viewModel.nextParagraph() },
                    onStop = { viewModel.stopSpeaking() }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.group == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("题组不存在", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            ReaderContent(
                state = state,
                listState = listState,
                showSourceEditor = showSourceEditor,
                sourceUrlDraft = sourceUrlDraft,
                onSourceUrlDraftChange = { sourceUrlDraft = it },
                onShowSourceEditor = {
                    sourceUrlDraft = state.group?.sourceUrl ?: ""
                    showSourceEditor = true
                },
                onSaveSourceUrl = {
                    viewModel.editSourceUrl(sourceUrlDraft)
                    showSourceEditor = false
                },
                onCancelSourceEditor = { showSourceEditor = false },
                onRetryVerification = { viewModel.retryVerification() },
                onResearchSource = { viewModel.researchSource() },
                onViewArticle = { state.linkedArticleId?.let { id -> onViewArticle?.invoke(id) } },
                onAnalyzeParagraph = viewModel::analyzeParagraph,
                onRetryTranslateParagraph = viewModel::retryTranslateParagraph,
                onToggleAnalysisExpanded = viewModel::toggleParagraphAnalysisExpanded,
                onWordClick = onWordClick,
                onCollectWord = viewModel::collectWord,
                onSelectAnswer = viewModel::selectAnswer,
                onSubmitAnswers = { viewModel.submitAnswers() },
                onShowAnswers = { viewModel.showAnswers() },
                onRetryPractice = { viewModel.retryPractice() },
                onScanAnswers = { answerImageLauncher.launch("image/*") },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }

    // Collection notebook bottom sheet
    if (state.showNotebook) {
        CollectionNotebookSheet(
            collectedWords = state.collectedWords,
            dictionaries = state.dictionaries,
            onLoadUnits = { dictionaryId -> viewModel.getUnitsForDictionary(dictionaryId) },
            onRemoveWord = viewModel::removeCollectedWord,
            onAddToDictionary = viewModel::addToDictionary,
            onDismiss = { viewModel.dismissNotebook() }
        )
    }
}

@Composable
private fun ReaderContent(
    state: ReaderUiState,
    listState: LazyListState,
    showSourceEditor: Boolean,
    sourceUrlDraft: String,
    onSourceUrlDraftChange: (String) -> Unit,
    onShowSourceEditor: () -> Unit,
    onSaveSourceUrl: () -> Unit,
    onCancelSourceEditor: () -> Unit,
    onRetryVerification: () -> Unit,
    onResearchSource: () -> Unit,
    onViewArticle: () -> Unit,
    onAnalyzeParagraph: (Long, String) -> Unit,
    onRetryTranslateParagraph: (Long, String) -> Unit,
    onToggleAnalysisExpanded: (Long) -> Unit,
    onWordClick: (Long, Long) -> Unit,
    onCollectWord: (String, String) -> Unit,
    onSelectAnswer: (Long, String) -> Unit,
    onSubmitAnswers: () -> Unit,
    onShowAnswers: () -> Unit,
    onRetryPractice: () -> Unit,
    onScanAnswers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val group = state.group ?: return
    val spokenParagraphId = run {
        val ttsState = state.ttsState
        if (!ttsState.isSpeaking) return@run 0L
        val speakable = state.paragraphs.filter { it.text.isNotBlank() }
        val idx = ttsState.currentIndex
        if (idx in speakable.indices) speakable[idx].id else 0L
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Header: paper title + section + source status ──
        item(key = "header") {
            Column {
                if (state.paperTitle.isNotBlank()) {
                    Text(
                        state.paperTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    group.sectionLabel ?: group.questionType.displayName,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))

                // Source verification status + actions
                SourceStatusRow(
                    group = group,
                    linkedArticleId = state.linkedArticleId,
                    isVerifying = state.isVerifying,
                    showSourceEditor = showSourceEditor,
                    sourceUrlDraft = sourceUrlDraft,
                    onSourceUrlDraftChange = onSourceUrlDraftChange,
                    onShowSourceEditor = onShowSourceEditor,
                    onSaveSourceUrl = onSaveSourceUrl,
                    onCancelSourceEditor = onCancelSourceEditor,
                    onRetryVerification = onRetryVerification,
                    onResearchSource = onResearchSource,
                    onViewArticle = onViewArticle
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }
        }

        // ── Area A: Passage paragraphs ──
        items(state.paragraphs, key = { it.id }) { paragraph ->
            ParagraphBlock(
                paragraph = paragraph,
                wordLinkMap = state.wordLinkMap,
                analysis = state.paragraphAnalysis[paragraph.id],
                isAnalyzing = state.analyzingParagraphId == paragraph.id,
                isSpeaking = spokenParagraphId == paragraph.id,
                translationEnabled = state.translationEnabled,
                translation = state.paragraphTranslations[paragraph.id],
                isTranslating = paragraph.id in state.translatingParagraphIds,
                translationFailed = paragraph.id in state.translationFailedParagraphIds,
                analysisExpanded = paragraph.id in state.expandedParagraphIds,
                onAnalyze = { onAnalyzeParagraph(paragraph.id, paragraph.text) },
                onRetryTranslate = { onRetryTranslateParagraph(paragraph.id, paragraph.text) },
                onToggleAnalysisExpanded = { onToggleAnalysisExpanded(paragraph.id) },
                onWordClick = onWordClick,
                onCollectWord = onCollectWord
            )
        }

        // ── Divider between reading and questions ──
        item(key = "question_divider") {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 2.dp)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("题目", style = MaterialTheme.typography.titleMedium)
                Text(
                    "(${state.items.size} 题)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                group.difficultyLevel?.let {
                    Text(
                        it.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // ── Area B: Questions ──
        items(state.items, key = { it.id }) { item ->
            QuestionCard(
                item = item,
                selectedAnswer = state.selectedAnswers[item.id],
                isSubmitted = state.isSubmitted,
                showingAnswers = state.showingAnswers,
                isCorrect = state.practiceResults[item.id],
                isWrong = item.id in state.wrongItemIds,
                onSelectAnswer = { answer -> onSelectAnswer(item.id, answer) }
            )
        }

        // ── Bottom action bar ──
        item(key = "actions") {
            Spacer(Modifier.height(8.dp))
            PracticeActionBar(
                state = state,
                onSubmit = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetry = onRetryPractice,
                onScanAnswers = onScanAnswers
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SourceStatusRow(
    group: com.xty.englishhelper.domain.model.QuestionGroup,
    linkedArticleId: Long?,
    isVerifying: Boolean,
    showSourceEditor: Boolean,
    sourceUrlDraft: String,
    onSourceUrlDraftChange: (String) -> Unit,
    onShowSourceEditor: () -> Unit,
    onSaveSourceUrl: () -> Unit,
    onCancelSourceEditor: () -> Unit,
    onRetryVerification: () -> Unit,
    onResearchSource: () -> Unit,
    onViewArticle: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (group.sourceVerified) {
                SourceVerifyStatus.VERIFIED -> {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("已验证", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (linkedArticleId != null) {
                        TextButton(onClick = onViewArticle, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("查看原文", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                SourceVerifyStatus.FAILED -> {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text("验证失败", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text("未验证", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (isVerifying) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
        }

        // Error message
        if (group.sourceVerified == SourceVerifyStatus.FAILED && !group.sourceVerifyError.isNullOrBlank()) {
            Text(
                group.sourceVerifyError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Action buttons for non-verified
        if (group.sourceVerified != SourceVerifyStatus.VERIFIED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onShowSourceEditor, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (group.sourceUrl.isNullOrBlank()) "输入来源 URL" else "编辑来源", style = MaterialTheme.typography.labelSmall)
                }
                if (!group.sourceUrl.isNullOrBlank()) {
                    TextButton(onClick = onRetryVerification, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重新验证", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onResearchSource, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重新搜索", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Inline source URL editor
        AnimatedVisibility(visible = showSourceEditor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                OutlinedTextField(
                    value = sourceUrlDraft,
                    onValueChange = onSourceUrlDraftChange,
                    label = { Text("来源 URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSaveSourceUrl) { Text("保存") }
                TextButton(onClick = onCancelSourceEditor) { Text("取消") }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    item: QuestionItem,
    selectedAnswer: String?,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    isCorrect: Boolean?,
    isWrong: Boolean,
    onSelectAnswer: (String) -> Unit
) {
    val wrongCount = item.wrongCount
    val borderColor = when {
        wrongCount >= 2 -> MaterialTheme.colorScheme.error
        wrongCount == 1 -> Color(0xFFFF9800) // orange
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (wrongCount > 0) BorderStroke(2.dp, borderColor) else null
    ) {
        Box {
            // Wrong count badge
            if (wrongCount > 0) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    containerColor = borderColor
                ) {
                    Text("x$wrongCount")
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                // Question number + text
                Row {
                    Text(
                        "${item.questionNumber}.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                    )
                    Text(item.questionText, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(8.dp))

                // Options
                val options = listOfNotNull(
                    item.optionA?.let { "A" to it },
                    item.optionB?.let { "B" to it },
                    item.optionC?.let { "C" to it },
                    item.optionD?.let { "D" to it }
                )

                for ((letter, text) in options) {
                    val isSelected = selectedAnswer == letter
                    val correctAnswer = item.correctAnswer
                    val optionColor = when {
                        !isSubmitted && !showingAnswers -> Color.Transparent
                        correctAnswer != null && letter.equals(correctAnswer, ignoreCase = true) -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        isSelected && isCorrect == false -> MaterialTheme.colorScheme.errorContainer
                        else -> Color.Transparent
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = optionColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .selectable(
                                selected = isSelected,
                                onClick = { if (!isSubmitted) onSelectAnswer(letter) },
                                role = Role.RadioButton
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { if (!isSubmitted) onSelectAnswer(letter) },
                                enabled = !isSubmitted
                            )
                            Text(
                                "[$letter] $text",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                // Result + explanation after submit
                if (isSubmitted || showingAnswers) {
                    val answer = item.correctAnswer
                    val source = item.answerSource
                    if (answer != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCorrect == true) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("正确", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                            } else if (isCorrect == false) {
                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("错误，正确答案: $answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("答案: $answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (source) {
                                    com.xty.englishhelper.domain.model.AnswerSource.AI -> "(AI)"
                                    com.xty.englishhelper.domain.model.AnswerSource.SCANNED -> "(扫描)"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!item.explanation.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(
                                item.explanation!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeActionBar(
    state: ReaderUiState,
    onSubmit: () -> Unit,
    onShowAnswers: () -> Unit,
    onRetry: () -> Unit,
    onScanAnswers: () -> Unit
) {
    val hasAnswers = state.items.any { it.correctAnswer != null }
    val hasSelection = state.selectedAnswers.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.isSubmitted && !state.showingAnswers) {
                // Submit
                Button(
                    onClick = onSubmit,
                    enabled = hasAnswers && hasSelection,
                    modifier = Modifier.weight(1f)
                ) { Text("提交答案") }

                // Show answers
                if (hasAnswers) {
                    OutlinedButton(
                        onClick = onShowAnswers,
                        modifier = Modifier.weight(1f)
                    ) { Text("查看答案") }
                }
            } else {
                // Retry
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) { Text("重新做题") }

                // Stats
                if (state.isSubmitted && state.practiceResults.isNotEmpty()) {
                    val correct = state.practiceResults.values.count { it }
                    val total = state.practiceResults.size
                    Text(
                        "$correct / $total 正确",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (correct == total) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }

        // Scan answers button
        OutlinedButton(
            onClick = onScanAnswers,
            enabled = !state.isScanningAnswers,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isScanningAnswers) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text("扫描答案")
        }
    }
}
