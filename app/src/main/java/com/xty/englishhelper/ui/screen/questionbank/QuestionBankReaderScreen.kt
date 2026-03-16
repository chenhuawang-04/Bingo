package com.xty.englishhelper.ui.screen.questionbank

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.SourceVerifyStatus
import com.xty.englishhelper.domain.repository.TranslationScore
import com.xty.englishhelper.domain.repository.WritingScore
import com.xty.englishhelper.ui.components.reading.ParagraphBlock
import com.xty.englishhelper.ui.components.reading.TtsPlaybackBar
import com.xty.englishhelper.ui.components.reading.extractContextSentence
import com.xty.englishhelper.ui.components.reading.extractWordAtOffset
import com.xty.englishhelper.ui.screen.article.CollectionNotebookSheet
import kotlinx.coroutines.launch

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
    val writingImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.scanWritingImages(uris)
        } else {
            viewModel.cancelWritingOcrSubmit()
        }
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
    val ttsCurrentSessionId = "article:${com.xty.englishhelper.domain.usecase.questionbank.questionBankContentId(state.group?.id ?: 0L)}"
    val ttsCurrent = ttsSessionId == ttsCurrentSessionId
    val ttsActive = state.ttsState.isSpeaking && ttsCurrent
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
                        state.group?.sectionLabel
                            ?: state.group?.questionType?.displayName
                            ?: "阅读",
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
            if (ttsCurrent) {
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
            val isCloze = state.group?.questionType == QuestionType.CLOZE
            val isParagraphOrder = state.group?.questionType == QuestionType.PARAGRAPH_ORDER
            val isSentenceInsertion = state.group?.questionType == QuestionType.SENTENCE_INSERTION
            val isCommentOpinionMatch = state.group?.questionType == QuestionType.COMMENT_OPINION_MATCH
            val isSubheadingMatch = state.group?.questionType == QuestionType.SUBHEADING_MATCH
            val isTranslation = state.group?.questionType == QuestionType.TRANSLATION
            val isWriting = state.group?.questionType == QuestionType.WRITING
            when {
                isCloze -> ClozeReaderContent(
                    state = state,
                    onSelectAnswer = viewModel::selectAnswer,
                    onSubmitAnswers = { viewModel.submitAnswers() },
                    onShowAnswers = { viewModel.showAnswers() },
                    onRetryPractice = { viewModel.retryPractice() },
                    onScanAnswers = { answerImageLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                isParagraphOrder -> ParagraphOrderReaderContent(
                    state = state,
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
                isSentenceInsertion -> SentenceInsertionReaderContent(
                    state = state,
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
                    onEditSentenceOptions = { viewModel.openSentenceOptionsEditor() },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                isCommentOpinionMatch -> CommentOpinionMatchContent(
                    state = state,
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
                    onEditMatchOptions = { viewModel.openSentenceOptionsEditor() },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                isSubheadingMatch -> SubheadingMatchContent(
                    state = state,
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
                    onEditMatchOptions = { viewModel.openSentenceOptionsEditor() },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                isTranslation -> TranslationReaderContent(
                    state = state,
                    onSelectAnswer = viewModel::selectAnswer,
                    onSubmitAnswers = { viewModel.submitAnswers() },
                    onShowAnswers = { viewModel.showAnswers() },
                    onRetryPractice = { viewModel.retryPractice() },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                isWriting -> WritingReaderContent(
                    state = state,
                    onSelectAnswer = viewModel::selectAnswer,
                    onSubmitAnswers = { viewModel.submitAnswers() },
                    onRetryPractice = { viewModel.retryPractice() },
                    onScanWriting = { writingImageLauncher.launch("image/*") },
                    onSearchSample = { viewModel.searchWritingSample(true) },
                    onSearchPromptSource = { viewModel.searchWritingPromptSource() },
                    onPrepareOcrSubmit = { viewModel.prepareWritingOcrSubmit() },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                else -> ReaderContent(
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
                    onVerifySource = { viewModel.verifySource() },
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

    if (state.showSentenceOptionsEditor) {
        val isCommentOpinionMatch = state.group?.questionType == QuestionType.COMMENT_OPINION_MATCH
        val isSubheadingMatch = state.group?.questionType == QuestionType.SUBHEADING_MATCH
        val dialogTitle = when {
            isCommentOpinionMatch -> "补录观点选项"
            isSubheadingMatch -> "补录小标题"
            else -> "补录选项句子"
        }
        val dialogHint = when {
            isCommentOpinionMatch -> "支持两种格式：每行一个观点，或一行粘贴 A. B. C. 格式（共 7 个）"
            isSubheadingMatch -> "支持两种格式：每行一个标题，或一行粘贴 A. B. C. 格式（共 7 个）"
            else -> "支持两种格式：每行一个句子，或一行粘贴 A. B. C. 格式（共 7 个）"
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissSentenceOptionsEditor() },
            title = { Text(dialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        dialogHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.sentenceOptionsDraft,
                        onValueChange = { viewModel.updateSentenceOptionsDraft(it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveSentenceOptions() }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSentenceOptionsEditor() }) { Text("取消") }
            }
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
    onVerifySource: () -> Unit,
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
                    onVerifySource = onVerifySource,
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
    onVerifySource: () -> Unit,
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
                TextButton(onClick = onVerifySource, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("验证来源", style = MaterialTheme.typography.labelSmall)
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
                                    com.xty.englishhelper.domain.model.AnswerSource.WEB -> "(范文)"
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

        AnimatedVisibility(visible = state.isCompressingAnswers) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    "正在压缩图片…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

// ══════════════════════════════════════════════════════════════
// ── CLOZE Reader ──
// ══════════════════════════════════════════════════════════════

private val BlankRegex = Regex("__(\\d+)__")
private val WordSplitRegex = Regex("(?<=\\s)|(?=\\s)|(?<=[,.:;!?\"'()\\[\\]{}])|(?=[,.:;!?\"'()\\[\\]{}])")
private val CommentNumberRegex = Regex("^\\s*[\\(（]?\\s*(\\d{1,3})\\s*[\\)）]?\\s*[\\.、]?\\s*")

@Composable
private fun ClozeReaderContent(
    state: ReaderUiState,
    onSelectAnswer: (Long, String) -> Unit,
    onSubmitAnswers: () -> Unit,
    onShowAnswers: () -> Unit,
    onRetryPractice: () -> Unit,
    onScanAnswers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val group = state.group ?: return
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val optionsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var focusedQuestionNumber by remember { mutableStateOf(-1) }

    val onBlankClick: (Int) -> Unit = { questionNumber ->
        focusedQuestionNumber = questionNumber
        val index = state.items.indexOfFirst { it.questionNumber == questionNumber }
        if (index >= 0) {
            scope.launch { optionsListState.animateScrollToItem(index + 1) }
        }
    }

    if (isLandscape) {
        Row(modifier = modifier.height(IntrinsicSize.Min)) {
            // Left: passage
            ClozePassagePanel(
                state = state,
                onBlankClick = onBlankClick,
                modifier = Modifier.weight(0.6f).fillMaxHeight()
            )
            VerticalDivider()
            // Right: options + actions
            ClozeOptionsPanel(
                state = state,
                listState = optionsListState,
                focusedQuestionNumber = focusedQuestionNumber,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetryPractice = onRetryPractice,
                onScanAnswers = onScanAnswers,
                modifier = Modifier.weight(0.4f).fillMaxHeight()
            )
        }
    } else {
        Column(modifier = modifier) {
            // Top: passage
            ClozePassagePanel(
                state = state,
                onBlankClick = onBlankClick,
                modifier = Modifier.weight(0.55f)
            )
            HorizontalDivider()
            // Bottom: options + actions
            ClozeOptionsPanel(
                state = state,
                listState = optionsListState,
                focusedQuestionNumber = focusedQuestionNumber,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetryPractice = onRetryPractice,
                onScanAnswers = onScanAnswers,
                modifier = Modifier.weight(0.45f)
            )
        }
    }
}

@Composable
private fun ClozePassagePanel(
    state: ReaderUiState,
    onBlankClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val group = state.group ?: return
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title
        item(key = "cloze_header") {
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
                    style = MaterialTheme.typography.titleMedium
                )
                if (!group.directions.isNullOrBlank()) {
                    Text(
                        group.directions!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }
        }

        // Paragraphs with inline blanks
        items(state.paragraphs, key = { "cloze_p_${it.id}" }) { paragraph ->
            ClozePassageText(
                text = paragraph.text,
                items = state.items,
                selectedAnswers = state.selectedAnswers,
                isSubmitted = state.isSubmitted,
                showingAnswers = state.showingAnswers,
                practiceResults = state.practiceResults,
                onBlankClick = onBlankClick
            )
        }
    }
}

@Composable
private fun ClozeOptionsPanel(
    state: ReaderUiState,
    listState: LazyListState,
    focusedQuestionNumber: Int,
    onSelectAnswer: (Long, String) -> Unit,
    onSubmitAnswers: () -> Unit,
    onShowAnswers: () -> Unit,
    onRetryPractice: () -> Unit,
    onScanAnswers: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(state.items, key = { _, item -> "cloze_q_${item.id}" }) { _, item ->
            ClozeOptionRow(
                item = item,
                selectedAnswer = state.selectedAnswers[item.id],
                isSubmitted = state.isSubmitted,
                showingAnswers = state.showingAnswers,
                isCorrect = state.practiceResults[item.id],
                isFocused = item.questionNumber == focusedQuestionNumber,
                onSelectAnswer = { answer -> onSelectAnswer(item.id, answer) }
            )
        }

        // Actions
        item(key = "cloze_actions") {
            Spacer(Modifier.height(8.dp))
            PracticeActionBar(
                state = state,
                onSubmit = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetry = onRetryPractice,
                onScanAnswers = onScanAnswers
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Renders passage text with __N__ blanks as inline clickable chips.
 */
@Composable
private fun ClozePassageText(
    text: String,
    items: List<QuestionItem>,
    selectedAnswers: Map<Long, String>,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    practiceResults: Map<Long, Boolean>,
    onBlankClick: (Int) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val errorColor = MaterialTheme.colorScheme.error
    val greenColor = Color(0xFF4CAF50)
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val bodyStyle = MaterialTheme.typography.bodyMedium

    val annotated = remember(text, selectedAnswers, isSubmitted, showingAnswers, practiceResults) {
        buildAnnotatedString {
            var lastEnd = 0
            val matches = BlankRegex.findAll(text).toList()
            for (match in matches) {
                // Text before the blank
                append(text.substring(lastEnd, match.range.first))
                val number = match.groupValues[1].toIntOrNull() ?: 0
                val item = items.find { it.questionNumber == number }
                val itemId = item?.id ?: 0L
                val selected = selectedAnswers[itemId]
                val result = practiceResults[itemId]
                val correctAnswer = item?.correctAnswer

                // Determine blank display text
                val blankText = when {
                    // After submit/show: display correct answer if available
                    (isSubmitted || showingAnswers) && correctAnswer != null -> " $correctAnswer "
                    selected != null -> " $selected "
                    else -> " $number "
                }
                // Determine color
                val (bg, fg) = when {
                    (isSubmitted || showingAnswers) && result == true -> greenColor to onPrimary
                    (isSubmitted || showingAnswers) && result == false -> errorColor to onPrimary
                    (isSubmitted || showingAnswers) && correctAnswer != null -> greenColor to onPrimary
                    selected != null -> primary to onPrimary
                    else -> surfaceVariant to primary
                }

                pushStringAnnotation(tag = "blank", annotation = number.toString())
                withStyle(
                    SpanStyle(
                        color = fg,
                        background = bg,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(blankText)
                }
                pop()
                lastEnd = match.range.last + 1
            }
            if (lastEnd < text.length) {
                append(text.substring(lastEnd))
            }
        }
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = bodyStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations("blank", offset, offset)
                .firstOrNull()?.let { annotation ->
                    annotation.item.toIntOrNull()?.let { onBlankClick(it) }
                }
        }
    )
}

/**
 * Compact single-line option row: `1. ○A word  ○B word  ○C word  ○D word`
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClozeOptionRow(
    item: QuestionItem,
    selectedAnswer: String?,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    isCorrect: Boolean?,
    isFocused: Boolean,
    onSelectAnswer: (String) -> Unit
) {
    val options = listOfNotNull(
        item.optionA?.let { "A" to it },
        item.optionB?.let { "B" to it },
        item.optionC?.let { "C" to it },
        item.optionD?.let { "D" to it }
    )

    val wrongCount = item.wrongCount
    val focusBg = if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(focusBg, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Question number
        Text(
            "${item.questionNumber}.",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )

        // Options in a FlowRow for wrapping
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            for ((letter, text) in options) {
                val isSelected = selectedAnswer == letter
                val correctAnswer = item.correctAnswer
                val chipColor = when {
                    !isSubmitted && !showingAnswers && isSelected -> MaterialTheme.colorScheme.primaryContainer
                    (isSubmitted || showingAnswers) && correctAnswer != null && letter.equals(correctAnswer, ignoreCase = true) -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    (isSubmitted || showingAnswers) && isSelected && isCorrect == false -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                }
                val textColor = when {
                    (isSubmitted || showingAnswers) && correctAnswer != null && letter.equals(correctAnswer, ignoreCase = true) -> Color(0xFF2E7D32)
                    (isSubmitted || showingAnswers) && isSelected && isCorrect == false -> MaterialTheme.colorScheme.error
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Text(
                    text = "$letter $text",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                    modifier = Modifier
                        .background(chipColor, RoundedCornerShape(4.dp))
                        .clickable(enabled = !isSubmitted) { onSelectAnswer(letter) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Wrong count badge
        if (wrongCount > 0) {
            val badgeColor = if (wrongCount >= 2) MaterialTheme.colorScheme.error else Color(0xFFFF9800)
            Badge(containerColor = badgeColor) { Text("x$wrongCount") }
        }
    }
}

// ══════════════════════════════════════════════════════════════

// ?????????????????????????????????????????????????????????????
// SENTENCE INSERTION Reader
// ?????????????????????????????????????????????????????????????

@Composable
private fun SentenceInsertionReaderContent(
    state: ReaderUiState,
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
    onEditSentenceOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val optionsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var focusedQuestionNumber by remember { mutableStateOf(-1) }

    val onBlankClick: (Int) -> Unit = { questionNumber ->
        focusedQuestionNumber = questionNumber
        val index = state.items.indexOfFirst { it.questionNumber == questionNumber }
        if (index >= 0) {
            scope.launch { optionsListState.animateScrollToItem(index) }
        }
    }

    val spokenParagraphId = run {
        val ttsState = state.ttsState
        if (!ttsState.isSpeaking) return@run 0L
        val speakable = state.paragraphs.filter { it.text.isNotBlank() }
        val idx = ttsState.currentIndex
        if (idx in speakable.indices) speakable[idx].id else 0L
    }

    if (isLandscape) {
        Row(modifier = modifier.height(IntrinsicSize.Min)) {
            SentenceInsertionPassagePanel(
                state = state,
                spokenParagraphId = spokenParagraphId,
                onAnalyzeParagraph = onAnalyzeParagraph,
                onRetryTranslateParagraph = onRetryTranslateParagraph,
                onToggleAnalysisExpanded = onToggleAnalysisExpanded,
                onWordClick = onWordClick,
                onCollectWord = onCollectWord,
                onBlankClick = onBlankClick,
                modifier = Modifier.weight(0.6f).fillMaxHeight()
            )
            VerticalDivider()
            SentenceInsertionAnswerPanel(
                state = state,
                listState = optionsListState,
                focusedQuestionNumber = focusedQuestionNumber,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetryPractice = onRetryPractice,
                onScanAnswers = onScanAnswers,
                onEditSentenceOptions = onEditSentenceOptions,
                modifier = Modifier.weight(0.4f).fillMaxHeight()
            )
        }
    } else {
        Column(modifier = modifier) {
            SentenceInsertionPassagePanel(
                state = state,
                spokenParagraphId = spokenParagraphId,
                onAnalyzeParagraph = onAnalyzeParagraph,
                onRetryTranslateParagraph = onRetryTranslateParagraph,
                onToggleAnalysisExpanded = onToggleAnalysisExpanded,
                onWordClick = onWordClick,
                onCollectWord = onCollectWord,
                onBlankClick = onBlankClick,
                modifier = Modifier.weight(0.55f)
            )
            HorizontalDivider()
            SentenceInsertionAnswerPanel(
                state = state,
                listState = optionsListState,
                focusedQuestionNumber = focusedQuestionNumber,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetryPractice = onRetryPractice,
                onScanAnswers = onScanAnswers,
                onEditSentenceOptions = onEditSentenceOptions,
                modifier = Modifier.weight(0.45f)
            )
        }
    }
}

@Composable
private fun SentenceInsertionPassagePanel(
    state: ReaderUiState,
    spokenParagraphId: Long,
    onAnalyzeParagraph: (Long, String) -> Unit,
    onRetryTranslateParagraph: (Long, String) -> Unit,
    onToggleAnalysisExpanded: (Long) -> Unit,
    onWordClick: (Long, Long) -> Unit,
    onCollectWord: (String, String) -> Unit,
    onBlankClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val group = state.group ?: return
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "sentence_insert_header") {
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
                    style = MaterialTheme.typography.titleMedium
                )
                if (!group.directions.isNullOrBlank()) {
                    Text(
                        group.directions!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Text(
                    "段落工具在每段末尾，可用于翻译或整理",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        items(state.paragraphs, key = { "sentence_insert_p_${it.id}" }) { paragraph ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SentenceInsertionPassageText(
                    text = paragraph.text,
                    items = state.items,
                    wordLinkMap = state.wordLinkMap,
                    selectedAnswers = state.selectedAnswers,
                    isSubmitted = state.isSubmitted,
                    showingAnswers = state.showingAnswers,
                    practiceResults = state.practiceResults,
                    onBlankClick = onBlankClick,
                    onWordClick = onWordClick,
                    onCollectWord = onCollectWord
                )
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
                    onCollectWord = onCollectWord,
                    showContent = false
                )
            }
        }
    }
}

@Composable
private fun SentenceInsertionPassageText(
    text: String,
    items: List<QuestionItem>,
    wordLinkMap: Map<String, List<ArticleWordLink>>,
    selectedAnswers: Map<Long, String>,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    practiceResults: Map<Long, Boolean>,
    onBlankClick: (Int) -> Unit,
    onWordClick: (Long, Long) -> Unit,
    onCollectWord: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val errorColor = MaterialTheme.colorScheme.error
    val greenColor = Color(0xFF4CAF50)
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val underlineStyle = SpanStyle(
        textDecoration = TextDecoration.Underline,
        color = primary
    )

    val annotated = remember(text, wordLinkMap, selectedAnswers, isSubmitted, showingAnswers, practiceResults) {
        buildAnnotatedString {
            fun appendSegment(segment: String) {
                if (segment.isEmpty()) return
                val words = segment.split(WordSplitRegex)
                for (word in words) {
                    val cleaned = word.trim().lowercase()
                        .trimEnd(',', '.', ':', ';', '!', '?', '"', '\'', ')', ']', '}')
                        .trimStart('"', '\'', '(', '[', '{')
                    val links = wordLinkMap[cleaned]
                    if (links != null && links.isNotEmpty() && cleaned.isNotEmpty()) {
                        pushStringAnnotation(tag = "word", annotation = "${links.first().wordId}:${links.first().dictionaryId}")
                        withStyle(underlineStyle) {
                            append(word)
                        }
                        pop()
                    } else {
                        append(word)
                    }
                }
            }

            var lastEnd = 0
            val matches = BlankRegex.findAll(text).toList()
            for (match in matches) {
                appendSegment(text.substring(lastEnd, match.range.first))
                val number = match.groupValues[1].toIntOrNull() ?: 0
                val item = items.find { it.questionNumber == number }
                val itemId = item?.id ?: 0L
                val selected = selectedAnswers[itemId]
                val result = practiceResults[itemId]
                val correctAnswer = item?.correctAnswer

                val blankText = when {
                    (isSubmitted || showingAnswers) && correctAnswer != null -> " $correctAnswer "
                    selected != null -> " $selected "
                    else -> " $number "
                }
                val (bg, fg) = when {
                    (isSubmitted || showingAnswers) && result == true -> greenColor to onPrimary
                    (isSubmitted || showingAnswers) && result == false -> errorColor to onPrimary
                    (isSubmitted || showingAnswers) && correctAnswer != null -> greenColor to onPrimary
                    selected != null -> primary to onPrimary
                    else -> surfaceVariant to primary
                }

                pushStringAnnotation(tag = "blank", annotation = number.toString())
                withStyle(
                    SpanStyle(
                        color = fg,
                        background = bg,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(blankText)
                }
                pop()
                lastEnd = match.range.last + 1
            }
            if (lastEnd < text.length) {
                appendSegment(text.substring(lastEnd))
            }
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        annotated,
        style = bodyStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        onTextLayout = { textLayoutResult = it },
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(wordLinkMap, annotated) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        textLayoutResult?.let { layout ->
                            val charOffset = layout.getOffsetForPosition(tapOffset)
                            val blankAnnotations = annotated.getStringAnnotations("blank", charOffset, charOffset)
                            if (blankAnnotations.isNotEmpty()) {
                                blankAnnotations.first().item.toIntOrNull()?.let { onBlankClick(it) }
                                return@detectTapGestures
                            }
                            val annotations = annotated.getStringAnnotations("word", charOffset, charOffset)
                            if (annotations.isNotEmpty()) {
                                val (wId, dId) = annotations.first().item.split(":")
                                onWordClick(wId.toLong(), dId.toLong())
                            } else {
                                val visibleText = annotated.text
                                val tappedWord = extractWordAtOffset(visibleText, charOffset)
                                if (tappedWord != null) {
                                    val context = extractContextSentence(visibleText, charOffset)
                                    onCollectWord(tappedWord, context)
                                }
                            }
                        }
                    }
                )
            }
    )
}

@Composable
private fun SentenceInsertionAnswerPanel(
    state: ReaderUiState,
    listState: LazyListState,
    focusedQuestionNumber: Int,
    onSelectAnswer: (Long, String) -> Unit,
    onSubmitAnswers: () -> Unit,
    onShowAnswers: () -> Unit,
    onRetryPractice: () -> Unit,
    onScanAnswers: () -> Unit,
    onEditSentenceOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val options = state.sentenceInsertionOptions
    val optionCount = 7
    val optionLetters = remember(optionCount) {
        (0 until optionCount).map { index -> ('A'.code + index).toChar().toString() }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "sentence_insert_options") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("可选句子", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onEditSentenceOptions) { Text("补录/编辑") }
                    }
                    if (options.isEmpty()) {
                        Text(
                            "未识别到选项句子",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "可先选择字母作答，之后再补录选项句子",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        options.forEach { option ->
                            Text(option, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        items(state.items, key = { "sentence_insert_q_${it.id}" }) { item ->
            SentenceInsertionQuestionCard(
                item = item,
                optionLetters = optionLetters,
                selectedAnswer = state.selectedAnswers[item.id],
                isSubmitted = state.isSubmitted,
                showingAnswers = state.showingAnswers,
                isCorrect = state.practiceResults[item.id],
                isFocused = item.questionNumber == focusedQuestionNumber,
                onSelectAnswer = { answer -> onSelectAnswer(item.id, answer) }
            )
        }

        item(key = "sentence_insert_actions") {
            PracticeActionBar(
                state = state,
                onSubmit = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetry = onRetryPractice,
                onScanAnswers = onScanAnswers
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentenceInsertionQuestionCard(
    item: QuestionItem,
    optionLetters: List<String>,
    selectedAnswer: String?,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    isCorrect: Boolean?,
    isFocused: Boolean,
    onSelectAnswer: (String) -> Unit
) {
    val wrongCount = item.wrongCount
    val borderColor = when {
        wrongCount >= 2 -> MaterialTheme.colorScheme.error
        wrongCount == 1 -> Color(0xFFFF9800)
        else -> Color.Transparent
    }
    val focusColor = if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (wrongCount > 0) BorderStroke(2.dp, borderColor) else null,
        colors = CardDefaults.cardColors(containerColor = focusColor)
    ) {
        Box {
            if (wrongCount > 0) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    containerColor = borderColor
                ) {
                    Text("x$wrongCount")
                }
            }

            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${item.questionNumber}.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        item.questionText.ifBlank { "填空" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    optionLetters.forEach { letter ->
                        val isSelected = selectedAnswer == letter
                        val correctAnswer = item.correctAnswer
                        val optionColor = when {
                            !isSubmitted && !showingAnswers && isSelected -> MaterialTheme.colorScheme.primaryContainer
                            (isSubmitted || showingAnswers) && correctAnswer != null && letter.equals(correctAnswer, ignoreCase = true) ->
                                Color(0xFF4CAF50).copy(alpha = 0.2f)
                            (isSubmitted || showingAnswers) && isSelected && isCorrect == false ->
                                MaterialTheme.colorScheme.errorContainer
                            else -> Color.Transparent
                        }
                        val textColor = when {
                            (isSubmitted || showingAnswers) && correctAnswer != null && letter.equals(correctAnswer, ignoreCase = true) ->
                                Color(0xFF2E7D32)
                            (isSubmitted || showingAnswers) && isSelected && isCorrect == false ->
                                MaterialTheme.colorScheme.error
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = optionColor),
                            modifier = Modifier
                                .selectable(
                                    selected = isSelected,
                                    onClick = { if (!isSubmitted) onSelectAnswer(letter) },
                                    role = Role.RadioButton
                                )
                        ) {
                            Text(
                                letter,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (isSubmitted || showingAnswers) {
                    val answer = item.correctAnswer
                    if (answer != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCorrect == true) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("正确", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                            } else if (isCorrect == false) {
                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "错误，正确答案：$answer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("答案：$answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (!item.explanation.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
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

// ── COMMENT OPINION MATCH ──

@Composable
private fun CommentOpinionMatchContent(
    state: ReaderUiState,
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
    onEditMatchOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val group = state.group ?: return
    val listState = rememberLazyListState()
    val spokenParagraphId = run {
        val ttsState = state.ttsState
        if (!ttsState.isSpeaking) return@run 0L
        val speakable = state.paragraphs.filter { it.text.isNotBlank() }
        val idx = ttsState.currentIndex
        if (idx in speakable.indices) speakable[idx].id else 0L
    }

    val options = state.sentenceInsertionOptions
    val optionLetters = remember {
        (0 until 7).map { index -> ('A'.code + index).toChar().toString() }
    }
    val commentParagraphs = remember(state.paragraphs) {
        state.paragraphs.filter { paragraph ->
            val trimmed = paragraph.text.trim()
            if (trimmed.isBlank()) return@filter false
            if (trimmed.startsWith("Directions", ignoreCase = true)) return@filter false
            if (trimmed.startsWith("Read the following", ignoreCase = true)) return@filter false
            if (trimmed.startsWith("Part ", ignoreCase = true)) return@filter false
            val urlCount = Regex("https?://\\S+").findAll(trimmed).count()
            val looksLikePureUrl = urlCount > 0 && trimmed.replace(Regex("https?://\\S+"), "").trim().length < 5
            if (looksLikePureUrl) return@filter false
            trimmed.length > 15
        }
    }
    val paragraphByNumber = remember(commentParagraphs) {
        val map = LinkedHashMap<Int, com.xty.englishhelper.domain.model.ArticleParagraph>()
        commentParagraphs.forEach { paragraph ->
            val match = CommentNumberRegex.find(paragraph.text)
            val number = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (number != null && !map.containsKey(number)) {
                map[number] = paragraph
            }
        }
        map
    }
    val paragraphByName = remember(commentParagraphs) {
        val map = LinkedHashMap<String, com.xty.englishhelper.domain.model.ArticleParagraph>()
        commentParagraphs.forEach { paragraph ->
            val text = paragraph.text.trim()
            val numberMatch = CommentNumberRegex.find(text)
            val afterNumber = if (numberMatch != null) text.substring(numberMatch.range.last + 1).trim() else text
            val nameToken = afterNumber.split(Regex("\\s+")).firstOrNull().orEmpty()
            val cleaned = nameToken.trim().trimEnd(':', ',', '.', ';')
            if (cleaned.length >= 2 && cleaned.all { it.isLetter() }) {
                map.putIfAbsent(cleaned.lowercase(), paragraph)
            }
        }
        map
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "comment_match_header") {
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
                    style = MaterialTheme.typography.titleMedium
                )
                if (!group.directions.isNullOrBlank()) {
                    Text(
                        group.directions!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }
        }

        item(key = "comment_match_options") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("可选观点", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onEditMatchOptions) { Text("补录/编辑") }
                    }
                    if (options.isEmpty()) {
                        Text(
                            "未识别到观点选项",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "可先选择字母作答，之后再补录选项",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        options.take(7).forEach { option ->
                            Text(option, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        itemsIndexed(state.items, key = { _, item -> "comment_match_q_${item.id}" }) { index, item ->
            val nameKey = extractCommentName(item.questionText)?.lowercase()
            val paragraph = paragraphByNumber[item.questionNumber]
                ?: nameKey?.let { paragraphByName[it] }
                ?: commentParagraphs.getOrNull(index)
            CommentOpinionQuestionCard(
                item = item,
                paragraph = paragraph,
                spokenParagraphId = spokenParagraphId,
                wordLinkMap = state.wordLinkMap,
                analysis = paragraph?.let { state.paragraphAnalysis[it.id] },
                isAnalyzing = paragraph?.id == state.analyzingParagraphId,
                translationEnabled = state.translationEnabled,
                translation = paragraph?.let { state.paragraphTranslations[it.id] },
                isTranslating = paragraph?.let { it.id in state.translatingParagraphIds } ?: false,
                translationFailed = paragraph?.let { it.id in state.translationFailedParagraphIds } ?: false,
                analysisExpanded = paragraph?.let { it.id in state.expandedParagraphIds } ?: false,
                onAnalyzeParagraph = onAnalyzeParagraph,
                onRetryTranslateParagraph = onRetryTranslateParagraph,
                onToggleAnalysisExpanded = onToggleAnalysisExpanded,
                onWordClick = onWordClick,
                onCollectWord = onCollectWord,
                optionLetters = optionLetters,
                selectedAnswer = state.selectedAnswers[item.id],
                isSubmitted = state.isSubmitted,
                showingAnswers = state.showingAnswers,
                isCorrect = state.practiceResults[item.id],
                onSelectAnswer = { answer -> onSelectAnswer(item.id, answer) }
            )
        }

        item(key = "comment_match_actions") {
            PracticeActionBar(
                state = state,
                onSubmit = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetry = onRetryPractice,
                onScanAnswers = onScanAnswers
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SubheadingMatchContent(
    state: ReaderUiState,
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
    onEditMatchOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val group = state.group ?: return
    val listState = rememberLazyListState()
    val spokenParagraphId = run {
        val ttsState = state.ttsState
        if (!ttsState.isSpeaking) return@run 0L
        val speakable = state.paragraphs.filter { it.text.isNotBlank() }
        val idx = ttsState.currentIndex
        if (idx in speakable.indices) speakable[idx].id else 0L
    }

    val options = state.sentenceInsertionOptions
    val optionLetters = remember {
        (0 until 7).map { index -> ('A'.code + index).toChar().toString() }
    }
    val subheadingParagraphs = remember(state.paragraphs) {
        state.paragraphs.filter { paragraph ->
            val trimmed = paragraph.text.trim()
            if (trimmed.isBlank()) return@filter false
            if (trimmed.startsWith("Directions", ignoreCase = true)) return@filter false
            if (trimmed.startsWith("Read the following", ignoreCase = true)) return@filter false
            if (trimmed.startsWith("Part ", ignoreCase = true)) return@filter false
            val urlCount = Regex("https?://\\S+").findAll(trimmed).count()
            val looksLikePureUrl = urlCount > 0 && trimmed.replace(Regex("https?://\\S+"), "").trim().length < 5
            if (looksLikePureUrl) return@filter false
            trimmed.length > 20
        }
    }
    val paragraphByNumber = remember(subheadingParagraphs) {
        val map = LinkedHashMap<Int, com.xty.englishhelper.domain.model.ArticleParagraph>()
        subheadingParagraphs.forEach { paragraph ->
            val match = CommentNumberRegex.find(paragraph.text)
            val number = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (number != null && !map.containsKey(number)) {
                map[number] = paragraph
            }
        }
        map
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "subheading_match_header") {
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
                    style = MaterialTheme.typography.titleMedium
                )
                if (!group.directions.isNullOrBlank()) {
                    Text(
                        group.directions!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }
        }

        item(key = "subheading_match_options") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("可选小标题", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onEditMatchOptions) { Text("补录/编辑") }
                    }
                    if (options.isEmpty()) {
                        Text(
                            "未识别到小标题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "可先选择字母作答，之后再补录标题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        options.take(7).forEach { option ->
                            Text(option, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        itemsIndexed(state.items, key = { _, item -> "subheading_match_q_${item.id}" }) { index, item ->
            val paragraph = paragraphByNumber[item.questionNumber] ?: subheadingParagraphs.getOrNull(index)
            CommentOpinionQuestionCard(
                item = item,
                paragraph = paragraph,
                spokenParagraphId = spokenParagraphId,
                wordLinkMap = state.wordLinkMap,
                analysis = paragraph?.let { state.paragraphAnalysis[it.id] },
                isAnalyzing = paragraph?.id == state.analyzingParagraphId,
                translationEnabled = state.translationEnabled,
                translation = paragraph?.let { state.paragraphTranslations[it.id] },
                isTranslating = paragraph?.let { it.id in state.translatingParagraphIds } ?: false,
                translationFailed = paragraph?.let { it.id in state.translationFailedParagraphIds } ?: false,
                analysisExpanded = paragraph?.let { it.id in state.expandedParagraphIds } ?: false,
                onAnalyzeParagraph = onAnalyzeParagraph,
                onRetryTranslateParagraph = onRetryTranslateParagraph,
                onToggleAnalysisExpanded = onToggleAnalysisExpanded,
                onWordClick = onWordClick,
                onCollectWord = onCollectWord,
                optionLetters = optionLetters,
                selectedAnswer = state.selectedAnswers[item.id],
                isSubmitted = state.isSubmitted,
                showingAnswers = state.showingAnswers,
                isCorrect = state.practiceResults[item.id],
                onSelectAnswer = { answer -> onSelectAnswer(item.id, answer) },
                fallbackTitle = "段落"
            )
        }

        item(key = "subheading_match_actions") {
            PracticeActionBar(
                state = state,
                onSubmit = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetry = onRetryPractice,
                onScanAnswers = onScanAnswers
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentOpinionQuestionCard(
    item: QuestionItem,
    paragraph: com.xty.englishhelper.domain.model.ArticleParagraph?,
    spokenParagraphId: Long,
    wordLinkMap: Map<String, List<ArticleWordLink>>,
    analysis: com.xty.englishhelper.domain.model.ParagraphAnalysisResult?,
    isAnalyzing: Boolean,
    translationEnabled: Boolean,
    translation: String?,
    isTranslating: Boolean,
    translationFailed: Boolean,
    analysisExpanded: Boolean,
    onAnalyzeParagraph: (Long, String) -> Unit,
    onRetryTranslateParagraph: (Long, String) -> Unit,
    onToggleAnalysisExpanded: (Long) -> Unit,
    onWordClick: (Long, Long) -> Unit,
    onCollectWord: (String, String) -> Unit,
    optionLetters: List<String>,
    selectedAnswer: String?,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    isCorrect: Boolean?,
    onSelectAnswer: (String) -> Unit,
    fallbackTitle: String = "评论"
) {
    val wrongCount = item.wrongCount
    val borderColor = when {
        wrongCount >= 2 -> MaterialTheme.colorScheme.error
        wrongCount == 1 -> Color(0xFFFF9800)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (wrongCount > 0) BorderStroke(2.dp, borderColor) else null
    ) {
        Box {
            if (wrongCount > 0) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    containerColor = borderColor
                ) {
                    Text("x$wrongCount")
                }
            }

            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val title = item.questionText.takeIf { it.isNotBlank() } ?: fallbackTitle
                Text(
                    "${item.questionNumber}. $title",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (paragraph != null) {
                    ParagraphBlock(
                        paragraph = paragraph,
                        wordLinkMap = wordLinkMap,
                        analysis = analysis,
                        isAnalyzing = isAnalyzing,
                        isSpeaking = spokenParagraphId == paragraph.id,
                        translationEnabled = translationEnabled,
                        translation = translation,
                        isTranslating = isTranslating,
                        translationFailed = translationFailed,
                        analysisExpanded = analysisExpanded,
                        onAnalyze = { onAnalyzeParagraph(paragraph.id, paragraph.text) },
                        onRetryTranslate = { onRetryTranslateParagraph(paragraph.id, paragraph.text) },
                        onToggleAnalysisExpanded = { onToggleAnalysisExpanded(paragraph.id) },
                        onWordClick = onWordClick,
                        onCollectWord = onCollectWord
                    )
                } else {
                    Text(
                        item.questionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    optionLetters.forEach { letter ->
                        val isSelected = selectedAnswer == letter
                        val correctAnswer = item.correctAnswer
                        val optionColor = when {
                            !isSubmitted && !showingAnswers && isSelected -> MaterialTheme.colorScheme.primaryContainer
                            (isSubmitted || showingAnswers) && correctAnswer != null && letter.equals(correctAnswer, ignoreCase = true) ->
                                Color(0xFF4CAF50).copy(alpha = 0.2f)
                            (isSubmitted || showingAnswers) && isSelected && isCorrect == false ->
                                MaterialTheme.colorScheme.errorContainer
                            else -> Color.Transparent
                        }
                        val textColor = when {
                            (isSubmitted || showingAnswers) && correctAnswer != null && letter.equals(correctAnswer, ignoreCase = true) ->
                                Color(0xFF2E7D32)
                            (isSubmitted || showingAnswers) && isSelected && isCorrect == false ->
                                MaterialTheme.colorScheme.error
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = optionColor),
                            modifier = Modifier
                                .selectable(
                                    selected = isSelected,
                                    onClick = { if (!isSubmitted) onSelectAnswer(letter) },
                                    role = Role.RadioButton
                                )
                        ) {
                            Text(
                                letter,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (isSubmitted || showingAnswers) {
                    val answer = item.correctAnswer
                    if (answer != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCorrect == true) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("正确", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                            } else if (isCorrect == false) {
                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "错误，正确答案：$answer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("答案：$answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (!item.explanation.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
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

private fun extractCommentName(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null
    val withoutLabel = trimmed.replace(Regex("(?i)comment\\s*\\d+"), "").trim()
    val withoutNumber = withoutLabel.replace(Regex("[\\(（]?\\d+[\\)）]?"), "").trim()
    if (withoutNumber.isBlank()) return null
    val name = withoutNumber.split(Regex("\\s+")).firstOrNull()?.trim()?.trimEnd(':', ',', '.', ';')
    return name?.takeIf { it.length >= 2 && it.all { ch -> ch.isLetter() || ch == '-' || ch == '.' } }
}

// ── TRANSLATION Reader ──
// ══════════════════════════════════════════════════════════════

private val TranslationMarkerRegex = Regex("""\(\((\d+)\)\)(.*?)\(\(/\1\)\)""", RegexOption.DOT_MATCHES_ALL)

@Composable
private fun TranslationReaderContent(
    state: ReaderUiState,
    onSelectAnswer: (Long, String) -> Unit,
    onSubmitAnswers: () -> Unit,
    onShowAnswers: () -> Unit,
    onRetryPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = modifier.height(IntrinsicSize.Min)) {
            TranslationPassagePanel(
                state = state,
                modifier = Modifier.weight(0.5f).fillMaxHeight()
            )
            VerticalDivider()
            TranslationAnswerPanel(
                state = state,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetryPractice = onRetryPractice,
                modifier = Modifier.weight(0.5f).fillMaxHeight()
            )
        }
    } else {
        Column(modifier = modifier) {
            TranslationPassagePanel(
                state = state,
                modifier = Modifier.weight(0.45f)
            )
            HorizontalDivider()
            TranslationAnswerPanel(
                state = state,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetryPractice = onRetryPractice,
                modifier = Modifier.weight(0.55f)
            )
        }
    }
}

@Composable
private fun TranslationPassagePanel(
    state: ReaderUiState,
    modifier: Modifier = Modifier
) {
    val group = state.group ?: return
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "trans_header") {
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
                    style = MaterialTheme.typography.titleMedium
                )
                if (!group.directions.isNullOrBlank()) {
                    Text(
                        group.directions!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }
        }

        items(state.paragraphs, key = { "trans_p_${it.id}" }) { paragraph ->
            TranslationPassageText(
                text = paragraph.text,
                items = state.items
            )
        }
    }
}

@Composable
private fun TranslationPassageText(
    text: String,
    items: List<QuestionItem>
) {
    val primary = MaterialTheme.colorScheme.primary
    val bodyStyle = MaterialTheme.typography.bodyMedium

    val matches = remember(text) { TranslationMarkerRegex.findAll(text).toList() }
    val hasMarkers = matches.isNotEmpty()

    if (!hasMarkers) {
        Text(text, style = bodyStyle)
        return
    }

    val annotated = remember(text, items) {
        buildAnnotatedString {
            var lastEnd = 0
            for (match in TranslationMarkerRegex.findAll(text)) {
                append(text.substring(lastEnd, match.range.first))
                val number = match.groupValues[1].toIntOrNull() ?: 0
                val sentenceText = match.groupValues[2]

                withStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        color = primary
                    )
                ) {
                    append(sentenceText)
                }
                withStyle(
                    SpanStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                        baselineShift = BaselineShift.Superscript
                    )
                ) {
                    append(" ($number)")
                }

                lastEnd = match.range.last + 1
            }
            if (lastEnd < text.length) {
                append(text.substring(lastEnd))
            }
        }
    }

    Text(
        text = annotated,
        style = bodyStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    )
}

@Composable
private fun TranslationAnswerPanel(
    state: ReaderUiState,
    onSelectAnswer: (Long, String) -> Unit,
    onSubmitAnswers: () -> Unit,
    onShowAnswers: () -> Unit,
    onRetryPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "trans_q_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("翻译", style = MaterialTheme.typography.titleMedium)
                Text(
                    "(${state.items.size} 题)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(state.items, key = { "trans_q_${it.id}" }) { item ->
            TranslationQuestionCard(
                item = item,
                userAnswer = state.selectedAnswers[item.id] ?: "",
                isSubmitted = state.isSubmitted,
                showingAnswers = state.showingAnswers,
                score = state.translationScores[item.id],
                isScoringTranslation = state.isScoringTranslation,
                onAnswerChange = { answer -> onSelectAnswer(item.id, answer) }
            )
        }

        item(key = "trans_actions") {
            Spacer(Modifier.height(8.dp))
            TranslationActionBar(
                state = state,
                onSubmit = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetry = onRetryPractice
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TranslationQuestionCard(
    item: QuestionItem,
    userAnswer: String,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    score: TranslationScore?,
    isScoringTranslation: Boolean,
    onAnswerChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Question number + original text snippet
            Row {
                Text(
                    "${item.questionNumber}.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                )
                Text(
                    item.questionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Translation input
            OutlinedTextField(
                value = userAnswer,
                onValueChange = onAnswerChange,
                label = { Text("输入中文翻译") },
                enabled = !isSubmitted && !showingAnswers,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                colors = if (isSubmitted || showingAnswers) OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) else OutlinedTextFieldDefaults.colors()
            )

            // Post-submission content
            if (isSubmitted || showingAnswers) {
                // AI scoring
                if (isScoringTranslation && score == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "AI 评分中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (score != null) {
                    // Score badge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (score.score >= 1.5f) Color(0xFF4CAF50).copy(alpha = 0.15f)
                            else if (score.score >= 1f) Color(0xFFFF9800).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "AI 评分: ",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${score.score}/${score.maxScore}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (score.score >= 1.5f) Color(0xFF4CAF50)
                                    else if (score.score >= 1f) Color(0xFFFF9800)
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                            if (score.feedback.isNotBlank()) {
                                Text(
                                    score.feedback,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Reference translation
                val answer = item.correctAnswer
                if (answer != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "参考译文",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                answer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else if (!showingAnswers) {
                    Text(
                        "参考译文生成中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Explanation (translation notes)
                if (!item.explanation.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "翻译要点",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                item.explanation!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationActionBar(
    state: ReaderUiState,
    onSubmit: () -> Unit,
    onShowAnswers: () -> Unit,
    onRetry: () -> Unit
) {
    val hasSelection = state.selectedAnswers.isNotEmpty()
    val hasAnswers = state.items.any { it.correctAnswer != null }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.isSubmitted && !state.showingAnswers) {
                Button(
                    onClick = onSubmit,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f)
                ) { Text("提交翻译") }

                if (hasAnswers) {
                    OutlinedButton(
                        onClick = onShowAnswers,
                        modifier = Modifier.weight(1f)
                    ) { Text("查看参考译文") }
                }
            } else {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) { Text("重新翻译") }
            }
        }

        Text(
            if (!state.isSubmitted && !state.showingAnswers)
                "提交后将显示参考译文"
            else
                "翻译练习不计入错题统计",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─────────────────────────────────────────────────────────────
// PARAGRAPH ORDER Reader
// ─────────────────────────────────────────────────────────────

@Composable
private fun ParagraphOrderReaderContent(
    state: ReaderUiState,
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val spokenParagraphId = run {
        val ttsState = state.ttsState
        if (!ttsState.isSpeaking) return@run 0L
        val speakable = state.paragraphs.filter { it.text.isNotBlank() }
        val idx = ttsState.currentIndex
        if (idx in speakable.indices) speakable[idx].id else 0L
    }

    if (isLandscape) {
        Row(modifier = modifier.height(IntrinsicSize.Min)) {
            ParagraphOrderPassagePanel(
                state = state,
                spokenParagraphId = spokenParagraphId,
                onAnalyzeParagraph = onAnalyzeParagraph,
                onRetryTranslateParagraph = onRetryTranslateParagraph,
                onToggleAnalysisExpanded = onToggleAnalysisExpanded,
                onWordClick = onWordClick,
                onCollectWord = onCollectWord,
                modifier = Modifier.weight(0.6f).fillMaxHeight()
            )
            VerticalDivider()
            ParagraphOrderAnswerPanel(
                state = state,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetryPractice = onRetryPractice,
                onScanAnswers = onScanAnswers,
                modifier = Modifier.weight(0.4f).fillMaxHeight()
            )
        }
    } else {
        Column(modifier = modifier) {
            ParagraphOrderPassagePanel(
                state = state,
                spokenParagraphId = spokenParagraphId,
                onAnalyzeParagraph = onAnalyzeParagraph,
                onRetryTranslateParagraph = onRetryTranslateParagraph,
                onToggleAnalysisExpanded = onToggleAnalysisExpanded,
                onWordClick = onWordClick,
                onCollectWord = onCollectWord,
                modifier = Modifier.weight(0.55f)
            )
            HorizontalDivider()
            ParagraphOrderAnswerPanel(
                state = state,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetryPractice = onRetryPractice,
                onScanAnswers = onScanAnswers,
                modifier = Modifier.weight(0.45f)
            )
        }
    }
}

@Composable
private fun ParagraphOrderPassagePanel(
    state: ReaderUiState,
    spokenParagraphId: Long,
    onAnalyzeParagraph: (Long, String) -> Unit,
    onRetryTranslateParagraph: (Long, String) -> Unit,
    onToggleAnalysisExpanded: (Long) -> Unit,
    onWordClick: (Long, Long) -> Unit,
    onCollectWord: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val group = state.group ?: return
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "para_order_header") {
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
                    style = MaterialTheme.typography.titleMedium
                )
                if (!group.directions.isNullOrBlank()) {
                    Text(
                        group.directions!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }
        }

        items(state.paragraphs, key = { "para_order_p_${it.id}" }) { paragraph ->
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
    }
}

@Composable
private fun ParagraphOrderAnswerPanel(
    state: ReaderUiState,
    onSelectAnswer: (Long, String) -> Unit,
    onSubmitAnswers: () -> Unit,
    onShowAnswers: () -> Unit,
    onRetryPractice: () -> Unit,
    onScanAnswers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val optionLetters = remember(state.paragraphs) {
        state.paragraphs.mapIndexed { index, _ ->
            (('A'.code + index).toChar()).toString()
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "para_order_answer_header") {
            Text("作答", style = MaterialTheme.typography.titleSmall)
            if (optionLetters.isEmpty()) {
                Text(
                    "未识别到段落选项，无法作答",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        items(state.items, key = { "para_order_q_${it.id}" }) { item ->
            ParagraphOrderQuestionCard(
                item = item,
                optionLetters = optionLetters,
                selectedAnswer = state.selectedAnswers[item.id],
                isSubmitted = state.isSubmitted,
                showingAnswers = state.showingAnswers,
                isCorrect = state.practiceResults[item.id],
                onSelectAnswer = { answer -> onSelectAnswer(item.id, answer) }
            )
        }

        item(key = "para_order_actions") {
            Spacer(Modifier.height(4.dp))
            PracticeActionBar(
                state = state,
                onSubmit = onSubmitAnswers,
                onShowAnswers = onShowAnswers,
                onRetry = onRetryPractice,
                onScanAnswers = onScanAnswers
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParagraphOrderQuestionCard(
    item: QuestionItem,
    optionLetters: List<String>,
    selectedAnswer: String?,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    isCorrect: Boolean?,
    onSelectAnswer: (String) -> Unit
) {
    val wrongCount = item.wrongCount
    val borderColor = when {
        wrongCount >= 2 -> MaterialTheme.colorScheme.error
        wrongCount == 1 -> Color(0xFFFF9800)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (wrongCount > 0) BorderStroke(2.dp, borderColor) else null
    ) {
        Box {
            if (wrongCount > 0) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    containerColor = borderColor
                ) {
                    Text("x$wrongCount")
                }
            }

            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${item.questionNumber}.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        item.questionText.ifBlank { "填空" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    optionLetters.forEach { letter ->
                        val isSelected = selectedAnswer == letter
                        val correctAnswer = item.correctAnswer
                        val optionColor = when {
                            !isSubmitted && !showingAnswers && isSelected -> MaterialTheme.colorScheme.primaryContainer
                            (isSubmitted || showingAnswers) && correctAnswer != null && letter.equals(correctAnswer, ignoreCase = true) ->
                                Color(0xFF4CAF50).copy(alpha = 0.2f)
                            (isSubmitted || showingAnswers) && isSelected && isCorrect == false ->
                                MaterialTheme.colorScheme.errorContainer
                            else -> Color.Transparent
                        }
                        val textColor = when {
                            (isSubmitted || showingAnswers) && correctAnswer != null && letter.equals(correctAnswer, ignoreCase = true) ->
                                Color(0xFF2E7D32)
                            (isSubmitted || showingAnswers) && isSelected && isCorrect == false ->
                                MaterialTheme.colorScheme.error
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = optionColor),
                            modifier = Modifier
                                .selectable(
                                    selected = isSelected,
                                    onClick = { if (!isSubmitted) onSelectAnswer(letter) },
                                    role = Role.RadioButton
                                )
                        ) {
                            Text(
                                letter,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (isSubmitted || showingAnswers) {
                    val answer = item.correctAnswer
                    if (answer != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCorrect == true) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("正确", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                            } else if (isCorrect == false) {
                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "错误，正确答案：$answer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("答案：$answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (!item.explanation.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
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

// ── WRITING Reader ──

@Composable
private fun WritingReaderContent(
    state: ReaderUiState,
    onSelectAnswer: (Long, String) -> Unit,
    onSubmitAnswers: () -> Unit,
    onRetryPractice: () -> Unit,
    onScanWriting: () -> Unit,
    onSearchSample: () -> Unit,
    onSearchPromptSource: () -> Unit,
    onPrepareOcrSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = modifier.height(IntrinsicSize.Min)) {
            WritingPassagePanel(
                state = state,
                onSearchSample = onSearchSample,
                onSearchPromptSource = onSearchPromptSource,
                modifier = Modifier.weight(0.5f).fillMaxHeight()
            )
            VerticalDivider()
            WritingAnswerPanel(
                state = state,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onRetryPractice = onRetryPractice,
                onScanWriting = onScanWriting,
                onPrepareOcrSubmit = onPrepareOcrSubmit,
                modifier = Modifier.weight(0.5f).fillMaxHeight()
            )
        }
    } else {
        Column(modifier = modifier) {
            WritingPassagePanel(
                state = state,
                onSearchSample = onSearchSample,
                onSearchPromptSource = onSearchPromptSource,
                modifier = Modifier.weight(0.45f)
            )
            HorizontalDivider()
            WritingAnswerPanel(
                state = state,
                onSelectAnswer = onSelectAnswer,
                onSubmitAnswers = onSubmitAnswers,
                onRetryPractice = onRetryPractice,
                onScanWriting = onScanWriting,
                onPrepareOcrSubmit = onPrepareOcrSubmit,
                modifier = Modifier.weight(0.55f)
            )
        }
    }
}

@Composable
private fun WritingPassagePanel(
    state: ReaderUiState,
    onSearchSample: () -> Unit,
    onSearchPromptSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val group = state.group ?: return
    val item = state.items.firstOrNull()
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "writing_header") {
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
                    style = MaterialTheme.typography.titleMedium
                )
                if (!group.directions.isNullOrBlank()) {
                    Text(
                        group.directions!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
            }
        }

        item(key = "writing_prompt") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("题干", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        item?.questionText.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!group.sourceUrl.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("题干来源", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            group.sourceUrl!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { uriHandler.openUri(group.sourceUrl!!) }
                        )
                    } else {
                        Text(
                            "未填写题干来源",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.isSearchingWritingSource) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                "检索中…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(onClick = onSearchPromptSource) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重新补录来源")
                            }
                        }
                    }
                }
            }
        }

        if (group.passageText.isNotBlank()) {
            item(key = "writing_passage") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("背景材料", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(group.passageText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item(key = "writing_sample_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("参考范文", style = MaterialTheme.typography.titleSmall)
                if (state.isSearchingWritingSample) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("检索中…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = onSearchSample) { Text("重新检索") }
                }
            }
        }

        item(key = "writing_sample_body") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val sampleTitle = item?.sampleSourceTitle
                    val sampleInfo = item?.sampleSourceInfo
                    val sampleUrl = item?.sampleSourceUrl
                    val sampleText = item?.correctAnswer
                    if (!sampleTitle.isNullOrBlank()) {
                        Text(sampleTitle, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    if (!sampleInfo.isNullOrBlank()) {
                        Text(sampleInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!sampleUrl.isNullOrBlank()) {
                        Text(
                            sampleUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { uriHandler.openUri(sampleUrl) }
                        )
                    }
                    if (!sampleText.isNullOrBlank()) {
                        Text(sampleText, style = MaterialTheme.typography.bodySmall)
                    } else if (!state.writingSampleError.isNullOrBlank()) {
                        Text(
                            "检索失败：${state.writingSampleError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "暂无范文，请检索或稍后再试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingAnswerPanel(
    state: ReaderUiState,
    onSelectAnswer: (Long, String) -> Unit,
    onSubmitAnswers: () -> Unit,
    onRetryPractice: () -> Unit,
    onScanWriting: () -> Unit,
    onPrepareOcrSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = state.items.firstOrNull()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "writing_answer_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("作答", style = MaterialTheme.typography.titleSmall)
                if (state.isOcrWriting) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("OCR 中…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else if (state.isCompressingWriting) {
                    Text("压缩图片中…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item(key = "writing_input") {
            if (item == null) return@item
            OutlinedTextField(
                value = state.selectedAnswers[item.id].orEmpty(),
                onValueChange = { onSelectAnswer(item.id, it) },
                label = { Text("输入作文") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 12,
                enabled = !state.isSubmitted
            )
        }

        item(key = "writing_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onScanWriting,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSubmitted && !state.isOcrWriting
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("扫描作文")
                }
                if (!state.isSubmitted) {
                    Button(
                        onClick = {
                            val current = item?.let { state.selectedAnswers[it.id].orEmpty() }.orEmpty()
                            if (current.isBlank()) {
                                onPrepareOcrSubmit()
                                onScanWriting()
                            } else {
                                onSubmitAnswers()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = item != null && !state.isOcrWriting && !state.isCompressingWriting
                    ) { Text("提交批阅") }
                } else {
                    Button(
                        onClick = onRetryPractice,
                        modifier = Modifier.weight(1f)
                    ) { Text("重做") }
                }
            }
        }

        item(key = "writing_score") {
            val score = item?.let { state.writingScores[it.id] }
            if (state.isScoringWriting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("批阅中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (score != null) {
                WritingScoreCard(score = score)
            }
        }

        item(key = "writing_bottom") {
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun WritingScoreCard(score: WritingScore) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "评分：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${score.totalScore}/${score.maxScore}（${score.band}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "字数：${score.wordCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("内容 ${score.subScores.content}", style = MaterialTheme.typography.bodySmall)
                Text("语言 ${score.subScores.language}", style = MaterialTheme.typography.bodySmall)
                Text("结构 ${score.subScores.structure}", style = MaterialTheme.typography.bodySmall)
                Text("格式 ${score.subScores.format}", style = MaterialTheme.typography.bodySmall)
            }
            if (score.deductions.isNotEmpty()) {
                Text("扣分项：", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                score.deductions.forEach { d ->
                    Text("${d.reason} (${d.score})", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (score.summary.isNotBlank()) {
                Text("总体评价", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(score.summary, style = MaterialTheme.typography.bodySmall)
            }
            if (score.suggestions.isNotEmpty()) {
                Text("改进建议", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                score.suggestions.forEach { s ->
                    Text("• $s", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
