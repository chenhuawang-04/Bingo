package com.xty.englishhelper.ui.screen.questionbank

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
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
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.AnswerSource
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
import com.xty.englishhelper.ui.components.reading.extractWordRangeAtOffset
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect
import com.xty.englishhelper.ui.designsystem.tokens.ArticleTypography
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSemanticColors
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
    val notebookPulseScale = remember { Animatable(1f) }
    val notebookPulseTint = remember { Animatable(0f) }
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

    LaunchedEffect(Unit) {
        viewModel.notebookMessage.collect { message ->
            launch {
                notebookPulseTint.snapTo(1f)
                notebookPulseTint.animateTo(0f, animationSpec = tween(durationMillis = 520))
            }
            launch {
                notebookPulseScale.snapTo(1f)
                notebookPulseScale.animateTo(1.14f, animationSpec = tween(durationMillis = 120))
                notebookPulseScale.animateTo(
                    1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f)
                )
            }
            snackbarHostState.showSnackbar(message)
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

    AppTopBarEffect(
        title = {
            Text(
                state.group?.sectionLabel
                    ?: state.group?.questionType?.displayName
                    ?: stringResource(R.string.reader_reading),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = { AppTopBarBackButton(onBack) },
        actions = {
            IconButton(onClick = { viewModel.toggleSpeakArticle() }) {
                Icon(
                    if (ttsActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (ttsActive) stringResource(R.string.common_pause) else stringResource(R.string.reader_read_aloud)
                )
            }
            IconButton(onClick = { viewModel.toggleTranslation() }) {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = stringResource(R.string.reader_translate),
                    tint = if (state.translationEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val notebookBaseTint = if (state.collectedWords.isNotEmpty()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val notebookTint = lerp(
                notebookBaseTint,
                MaterialTheme.colorScheme.primary,
                notebookPulseTint.value
            )
            IconButton(onClick = { viewModel.toggleNotebook() }) {
                if (state.collectedWords.isNotEmpty()) {
                    BadgedBox(
                        badge = {
                            Badge { Text("${state.collectedWords.size}") }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.CollectionsBookmark,
                            contentDescription = stringResource(R.string.reader_notebook),
                            tint = notebookTint,
                            modifier = Modifier.graphicsLayer {
                                scaleX = notebookPulseScale.value
                                scaleY = notebookPulseScale.value
                            }
                        )
                    }
                } else {
                    Icon(
                        Icons.Outlined.CollectionsBookmark,
                        contentDescription = stringResource(R.string.reader_notebook),
                        tint = notebookTint,
                        modifier = Modifier.graphicsLayer {
                            scaleX = notebookPulseScale.value
                            scaleY = notebookPulseScale.value
                        }
                    )
                }
            }
        }
    )

    Scaffold(
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
                Text(stringResource(R.string.reader_group_not_exists), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val isCloze = state.group?.questionType == QuestionType.CLOZE
            val isParagraphOrder = state.group?.questionType == QuestionType.PARAGRAPH_ORDER
            val isSentenceInsertion = state.group?.questionType == QuestionType.SENTENCE_INSERTION
            val isCommentOpinionMatch = state.group?.questionType == QuestionType.COMMENT_OPINION_MATCH
            val isSubheadingMatch = state.group?.questionType == QuestionType.SUBHEADING_MATCH
            val isInformationMatch = state.group?.questionType == QuestionType.INFORMATION_MATCH
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
                isInformationMatch -> InformationMatchContent(
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
                    onToggleWritingPractice = viewModel::setWritingPracticeEnabled,
                    onRefreshWritingPractice = viewModel::refreshWritingPracticePhrases,
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
        val isInformationMatch = state.group?.questionType == QuestionType.INFORMATION_MATCH
        val dialogTitle = when {
            isCommentOpinionMatch -> stringResource(R.string.reader_options_title_opinion)
            isSubheadingMatch -> stringResource(R.string.reader_options_title_subheading)
            isInformationMatch -> stringResource(R.string.reader_options_title_info)
            else -> stringResource(R.string.reader_options_title_sentence)
        }
        val dialogHint = when {
            isCommentOpinionMatch -> stringResource(R.string.reader_options_hint_opinion)
            isSubheadingMatch -> stringResource(R.string.reader_options_hint_subheading)
            isInformationMatch -> stringResource(R.string.reader_options_hint_info)
            else -> stringResource(R.string.reader_options_hint_sentence)
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
                TextButton(onClick = { viewModel.saveSentenceOptions() }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSentenceOptionsEditor() }) { Text(stringResource(R.string.common_cancel)) }
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
                Text(stringResource(R.string.reader_questions_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reader_question_count, state.items.size),
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
                    Icon(Icons.Default.CheckCircle, stringResource(R.string.question_source_verified), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.question_source_verified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (linkedArticleId != null) {
                        TextButton(onClick = onViewArticle, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.reader_view_source), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.reader_view_source), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                SourceVerifyStatus.FAILED -> {
                    Icon(Icons.Default.Error, stringResource(R.string.question_source_verify_failed), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.question_source_verify_failed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.question_source_unverified), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.question_source_unverified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Icon(Icons.Default.Edit, stringResource(R.string.reader_edit_source), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (group.sourceUrl.isNullOrBlank()) stringResource(R.string.reader_input_source_url) else stringResource(R.string.reader_edit_source), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onVerifySource, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.Search, stringResource(R.string.reader_verify_source), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.reader_verify_source), style = MaterialTheme.typography.labelSmall)
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
                    label = { Text(stringResource(R.string.scan_source_url)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSaveSourceUrl) { Text(stringResource(R.string.common_save)) }
                TextButton(onClick = onCancelSourceEditor) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

private data class PracticeStatusColors(
    val success: Color,
    val successContainer: Color,
    val successStrongContainer: Color,
    val successSubtleContainer: Color,
    val warning: Color
)

private data class PracticeOptionColors(
    val container: Color,
    val content: Color
)

@Composable
private fun rememberPracticeStatusColors(): PracticeStatusColors {
    val semanticColors = LocalEhSemanticColors.current
    return remember(semanticColors) {
        PracticeStatusColors(
            success = semanticColors.retentionHigh,
            successContainer = semanticColors.retentionHigh.copy(alpha = 0.15f),
            successStrongContainer = semanticColors.retentionHigh.copy(alpha = 0.2f),
            successSubtleContainer = semanticColors.retentionHigh.copy(alpha = 0.1f),
            warning = semanticColors.retentionMid
        )
    }
}

private fun wrongCountHighlightColor(
    wrongCount: Int,
    warningColor: Color,
    errorColor: Color
): Color {
    return when {
        wrongCount >= 2 -> errorColor
        wrongCount == 1 -> warningColor
        else -> Color.Transparent
    }
}

private fun resolveOptionColors(
    isSelected: Boolean,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    isCorrect: Boolean?,
    correctAnswer: String?,
    optionLetter: String,
    colors: PracticeStatusColors,
    primaryColor: Color,
    primaryContainer: Color,
    errorColor: Color,
    errorContainer: Color,
    defaultContentColor: Color
): PracticeOptionColors {
    val container = when {
        !isSubmitted && !showingAnswers && isSelected -> primaryContainer
        (isSubmitted || showingAnswers) && correctAnswer != null && optionLetter.equals(correctAnswer, ignoreCase = true) ->
            colors.successStrongContainer
        (isSubmitted || showingAnswers) && isSelected && isCorrect == false -> errorContainer
        else -> Color.Transparent
    }
    val content = when {
        (isSubmitted || showingAnswers) && correctAnswer != null && optionLetter.equals(correctAnswer, ignoreCase = true) ->
            colors.success
        (isSubmitted || showingAnswers) && isSelected && isCorrect == false -> errorColor
        isSelected -> primaryColor
        else -> defaultContentColor
    }
    return PracticeOptionColors(container = container, content = content)
}

private fun translationScoreColors(
    score: Float,
    colors: PracticeStatusColors,
    errorColor: Color,
    errorContainer: Color
): PracticeOptionColors {
    return when {
        score >= 1.5f -> PracticeOptionColors(
            container = colors.successContainer,
            content = colors.success
        )
        score >= 1f -> PracticeOptionColors(
            container = colors.warning.copy(alpha = 0.15f),
            content = colors.warning
        )
        else -> PracticeOptionColors(
            container = errorContainer,
            content = errorColor
        )
    }
}

private fun answerSourceText(source: AnswerSource?, scannedText: String, essayText: String): String {
    return when (source) {
        AnswerSource.AI -> "(AI)"
        AnswerSource.SCANNED -> scannedText
        AnswerSource.WEB -> essayText
        else -> ""
    }
}

@Composable
private fun PracticeResultRow(
    correctAnswer: String,
    isCorrect: Boolean?,
    answerSource: AnswerSource? = null
) {
    val statusColors = rememberPracticeStatusColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (isCorrect) {
            true -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.reader_correct),
                    tint = statusColors.success,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.reader_correct),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColors.success
                )
            }
            false -> {
                Icon(
                    Icons.Default.Error,
                    contentDescription = stringResource(R.string.reader_wrong),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.reader_wrong_answer, correctAnswer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            null -> {
                Text(
                    stringResource(R.string.reader_answer_label, correctAnswer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        val sourceText = answerSourceText(answerSource, stringResource(R.string.reader_source_scanned), stringResource(R.string.reader_source_essay))
        if (sourceText.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                sourceText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val statusColors = rememberPracticeStatusColors()
    val borderColor = wrongCountHighlightColor(
        wrongCount = wrongCount,
        warningColor = statusColors.warning,
        errorColor = MaterialTheme.colorScheme.error
    )

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
                    Text(item.questionText, style = ArticleTypography.QuestionStem)
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
                    val optionColors = resolveOptionColors(
                        isSelected = isSelected,
                        isSubmitted = isSubmitted,
                        showingAnswers = showingAnswers,
                        isCorrect = isCorrect,
                        correctAnswer = correctAnswer,
                        optionLetter = letter,
                        colors = statusColors,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        primaryContainer = MaterialTheme.colorScheme.primaryContainer,
                        errorColor = MaterialTheme.colorScheme.error,
                        errorContainer = MaterialTheme.colorScheme.errorContainer,
                        defaultContentColor = MaterialTheme.colorScheme.onSurface
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = optionColors.container),
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
                                style = ArticleTypography.QuestionOption,
                                color = optionColors.content,
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
                        PracticeResultRow(
                            correctAnswer = answer,
                            isCorrect = isCorrect,
                            answerSource = source
                        )
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
    val statusColors = rememberPracticeStatusColors()

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
                ) { Text(stringResource(R.string.reader_submit_answers)) }

                // Show answers
                if (hasAnswers) {
                    OutlinedButton(
                        onClick = onShowAnswers,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.reader_show_answers)) }
                }
            } else {
                // Retry
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.reader_retry_practice)) }

                // Stats
                if (state.isSubmitted && state.practiceResults.isNotEmpty()) {
                    val correct = state.practiceResults.values.count { it }
                    val total = state.practiceResults.size
                    Text(
                        stringResource(R.string.reader_correct_count, correct, total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (correct == total) statusColors.success else MaterialTheme.colorScheme.error,
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
                    stringResource(R.string.reader_compressing_answers),
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
                Icon(Icons.Default.CameraAlt, stringResource(R.string.reader_scan_answers), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.reader_scan_answers))
        }
    }
}

// ══════════════════════════════════════════════════════════════
// ── CLOZE Reader ──
// ══════════════════════════════════════════════════════════════

private val BlankRegex = Regex("__(\\d+)__")
private val WordSplitRegex = Regex("(?<=\\s)|(?=\\s)|(?<=[,.:;!?\"'()\\[\\]{}])|(?=[,.:;!?\"'()\\[\\]{}])")
private val CommentNumberRegex = Regex("^\\s*[\\(（]?\\s*(\\d{1,3})\\s*[\\)）]?\\s*[\\.、]?\\s*")
private val InfoMatchOptionPrefixRegex = Regex("^\\s*[A-Ga-g][\\).、.]\\s+")
private val InfoMatchNoisePrefixRegex = Regex("^(Directions|Read the following|Part\\s+|Questions\\s+\\d+)", RegexOption.IGNORE_CASE)
private val PageNumberRegex = Regex("^\\s*\\d+\\s*$")

private fun filterInfoMatchParagraphs(paragraphs: List<com.xty.englishhelper.domain.model.ArticleParagraph>): List<com.xty.englishhelper.domain.model.ArticleParagraph> {
    return paragraphs.filter { paragraph ->
        val trimmed = paragraph.text.trim()
        if (trimmed.isBlank()) return@filter false
        if (InfoMatchNoisePrefixRegex.containsMatchIn(trimmed)) return@filter false
        if (PageNumberRegex.matches(trimmed)) return@filter false
        val hasOptionPrefix = InfoMatchOptionPrefixRegex.containsMatchIn(trimmed)
        val alphaNumericCount = trimmed.count { it.isLetterOrDigit() }
        if (alphaNumericCount < 3) return@filter false
        if (trimmed.length < 8 && !hasOptionPrefix) return@filter false
        true
    }
}

private fun buildOptionLetters(count: Int): List<String> {
    if (count <= 0) return emptyList()
    return (0 until count).map { index ->
        if (index < 26) {
            ('A'.code + index).toChar().toString()
        } else {
            (index + 1).toString()
        }
    }
}

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
        // Avoid intrinsic measurement with LazyColumn (crashes on landscape).
        Row(modifier = modifier.fillMaxSize()) {
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
    val itemByNumber = remember(state.items) { state.items.associateBy { it.questionNumber } }
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
                itemByNumber = itemByNumber,
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
    itemByNumber: Map<Int, QuestionItem>,
    selectedAnswers: Map<Long, String>,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    practiceResults: Map<Long, Boolean>,
    onBlankClick: (Int) -> Unit
) {
    val statusColors = rememberPracticeStatusColors()
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val errorColor = MaterialTheme.colorScheme.error
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val bodyStyle = ArticleTypography.ReaderBody

    val annotated = remember(text, itemByNumber, selectedAnswers, isSubmitted, showingAnswers, practiceResults) {
        buildAnnotatedString {
            var lastEnd = 0
            val matches = BlankRegex.findAll(text).toList()
            for (match in matches) {
                // Text before the blank
                append(text.substring(lastEnd, match.range.first))
                val number = match.groupValues[1].toIntOrNull() ?: 0
                val item = itemByNumber[number]
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
                    (isSubmitted || showingAnswers) && result == true -> statusColors.success to onPrimary
                    (isSubmitted || showingAnswers) && result == false -> errorColor to onPrimary
                    (isSubmitted || showingAnswers) && correctAnswer != null -> statusColors.success to onPrimary
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
    val statusColors = rememberPracticeStatusColors()
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
                val optionColors = resolveOptionColors(
                    isSelected = isSelected,
                    isSubmitted = isSubmitted,
                    showingAnswers = showingAnswers,
                    isCorrect = isCorrect,
                    correctAnswer = correctAnswer,
                    optionLetter = letter,
                    colors = statusColors,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    primaryContainer = MaterialTheme.colorScheme.primaryContainer,
                    errorColor = MaterialTheme.colorScheme.error,
                    errorContainer = MaterialTheme.colorScheme.errorContainer,
                    defaultContentColor = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "$letter $text",
                    style = ArticleTypography.QuestionOption,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = optionColors.content,
                    modifier = Modifier
                        .background(optionColors.container, RoundedCornerShape(4.dp))
                        .clickable(enabled = !isSubmitted) { onSelectAnswer(letter) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Wrong count badge
        if (wrongCount > 0) {
            val badgeColor = wrongCountHighlightColor(
                wrongCount = wrongCount,
                warningColor = statusColors.warning,
                errorColor = MaterialTheme.colorScheme.error
            )
            Badge(containerColor = badgeColor) { Text("x$wrongCount") }
        }
    }
}

// ══════════════════════════════════════════════════════════════

// =============================================================
// SENTENCE INSERTION Reader
// =============================================================

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
        Row(modifier = modifier.fillMaxSize()) {
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
    val itemByNumber = remember(state.items) { state.items.associateBy { it.questionNumber } }
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
                    stringResource(R.string.reader_paragraph_tools_hint),
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
                    itemByNumber = itemByNumber,
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
    itemByNumber: Map<Int, QuestionItem>,
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
    val statusColors = rememberPracticeStatusColors()
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val errorColor = MaterialTheme.colorScheme.error
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val bodyStyle = ArticleTypography.ReaderBody
    val flashColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
    val flashAlpha = remember { Animatable(0f) }
    var flashRange by remember(text) { mutableStateOf<IntRange?>(null) }
    val underlineStyle = SpanStyle(
        textDecoration = TextDecoration.Underline,
        color = primary
    )

    val annotated = remember(
        text,
        itemByNumber,
        wordLinkMap,
        selectedAnswers,
        isSubmitted,
        showingAnswers,
        practiceResults,
        flashRange,
        flashAlpha.value,
        flashColor
    ) {
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
                val item = itemByNumber[number]
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
                    (isSubmitted || showingAnswers) && result == true -> statusColors.success to onPrimary
                    (isSubmitted || showingAnswers) && result == false -> errorColor to onPrimary
                    (isSubmitted || showingAnswers) && correctAnswer != null -> statusColors.success to onPrimary
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
            flashRange?.let { range ->
                val start = range.first.coerceAtLeast(0)
                val endExclusive = (range.last + 1).coerceAtMost(length)
                if (flashAlpha.value > 0f && start < endExclusive) {
                    addStyle(
                        SpanStyle(background = flashColor.copy(alpha = flashAlpha.value)),
                        start = start,
                        end = endExclusive
                    )
                }
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
                                val tappedRange = extractWordRangeAtOffset(visibleText, charOffset)
                                val tappedWord = tappedRange?.let { visibleText.substring(it) }
                                if (tappedWord != null) {
                                    val context = extractContextSentence(visibleText, charOffset)
                                    flashRange = tappedRange
                                    scope.launch {
                                        flashAlpha.snapTo(1f)
                                        flashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 650))
                                        if (flashRange == tappedRange) {
                                            flashRange = null
                                        }
                                    }
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
                        Text(stringResource(R.string.reader_optional_sentences), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onEditSentenceOptions) { Text(stringResource(R.string.reader_add_edit)) }
                    }
                    if (options.isEmpty()) {
                        Text(
                            stringResource(R.string.reader_unrecognized_sentences),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            stringResource(R.string.reader_hint_first_sentences),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        options.forEach { option ->
                            Text(option, style = ArticleTypography.QuestionOption)
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
    val statusColors = rememberPracticeStatusColors()
    val borderColor = wrongCountHighlightColor(
        wrongCount = wrongCount,
        warningColor = statusColors.warning,
        errorColor = MaterialTheme.colorScheme.error
    )
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
                        item.questionText.ifBlank { stringResource(R.string.reader_fill_blank) },
                        style = ArticleTypography.QuestionStem
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    optionLetters.forEach { letter ->
                        val isSelected = selectedAnswer == letter
                        val correctAnswer = item.correctAnswer
                        val optionColors = resolveOptionColors(
                            isSelected = isSelected,
                            isSubmitted = isSubmitted,
                            showingAnswers = showingAnswers,
                            isCorrect = isCorrect,
                            correctAnswer = correctAnswer,
                            optionLetter = letter,
                            colors = statusColors,
                            primaryColor = MaterialTheme.colorScheme.primary,
                            primaryContainer = MaterialTheme.colorScheme.primaryContainer,
                            errorColor = MaterialTheme.colorScheme.error,
                            errorContainer = MaterialTheme.colorScheme.errorContainer,
                            defaultContentColor = MaterialTheme.colorScheme.onSurface
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = optionColors.container),
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
                                color = optionColors.content,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (isSubmitted || showingAnswers) {
                    val answer = item.correctAnswer
                    if (answer != null) {
                        PracticeResultRow(correctAnswer = answer, isCorrect = isCorrect)
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
                        Text(stringResource(R.string.reader_optional_viewpoints), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onEditMatchOptions) { Text(stringResource(R.string.reader_add_edit)) }
                    }
                    if (options.isEmpty()) {
                        Text(
                            stringResource(R.string.reader_unrecognized_opinions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            stringResource(R.string.reader_hint_first_options),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        options.take(7).forEach { option ->
                            Text(option, style = ArticleTypography.QuestionOption)
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
            val maybeTitle = trimmed.length <= 90 &&
                trimmed.split(Regex("\\s+")).size <= 14 &&
                trimmed.firstOrNull()?.isUpperCase() == true &&
                !trimmed.contains(".") &&
                !CommentNumberRegex.containsMatchIn(trimmed)
            if (maybeTitle) return@filter false
            val urlCount = Regex("https?://\\S+").findAll(trimmed).count()
            val looksLikePureUrl = urlCount > 0 && trimmed.replace(Regex("https?://\\S+"), "").trim().length < 5
            if (looksLikePureUrl) return@filter false
            trimmed.length > 20
        }
    }
    val fallbackParagraphs = remember(subheadingParagraphs, state.items) {
        val count = state.items.size
        if (count in 1..subheadingParagraphs.size) {
            subheadingParagraphs.takeLast(count)
        } else {
            subheadingParagraphs
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
                        Text(stringResource(R.string.reader_optional_subtitles), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onEditMatchOptions) { Text(stringResource(R.string.reader_add_edit)) }
                    }
                    if (options.isEmpty()) {
                        Text(
                            stringResource(R.string.reader_unrecognized_subtitles),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            stringResource(R.string.reader_hint_first_titles),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        options.take(7).forEach { option ->
                            Text(option, style = ArticleTypography.QuestionOption)
                        }
                    }
                }
            }
        }

        itemsIndexed(state.items, key = { _, item -> "subheading_match_q_${item.id}" }) { index, item ->
            val paragraph = paragraphByNumber[item.questionNumber] ?: fallbackParagraphs.getOrNull(index)
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
                fallbackTitle = stringResource(R.string.reader_paragraph_label)
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

@Composable
private fun InformationMatchContent(
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
    val optionParagraphs = remember(state.paragraphs) { filterInfoMatchParagraphs(state.paragraphs) }
    val fallbackOptions = state.sentenceInsertionOptions
    val optionCount = when {
        optionParagraphs.isNotEmpty() -> optionParagraphs.size
        fallbackOptions.isNotEmpty() -> fallbackOptions.size
        else -> 0
    }
    val optionLetters = remember(optionCount) { buildOptionLetters(optionCount) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "info_match_header") {
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

        item(key = "info_match_options_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.reader_optional_info), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onEditMatchOptions) { Text(stringResource(R.string.reader_add_edit)) }
            }
        }

        if (optionParagraphs.isNotEmpty()) {
            itemsIndexed(optionParagraphs, key = { _, paragraph -> "info_match_opt_${paragraph.id}" }) { index, paragraph ->
                val label = optionLetters.getOrNull(index) ?: (index + 1).toString()
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
        } else {
            item(key = "info_match_options_fallback") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val options = fallbackOptions
                        if (options.isEmpty()) {
                            Text(
                                stringResource(R.string.reader_unrecognized_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            options.forEachIndexed { index, option ->
                                val label = optionLetters.getOrNull(index) ?: (index + 1).toString()
                                Text("$label. $option", style = ArticleTypography.QuestionOption)
                            }
                        }
                    }
                }
            }
        }

        items(state.items, key = { "info_match_q_${it.id}" }) { item ->
            InfoMatchQuestionCard(
                item = item,
                optionLetters = optionLetters,
                selectedAnswer = state.selectedAnswers[item.id],
                isSubmitted = state.isSubmitted,
                showingAnswers = state.showingAnswers,
                isCorrect = state.practiceResults[item.id],
                onSelectAnswer = { answer -> onSelectAnswer(item.id, answer) }
            )
        }

        item(key = "info_match_actions") {
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
private fun InfoMatchQuestionCard(
    item: QuestionItem,
    optionLetters: List<String>,
    selectedAnswer: String?,
    isSubmitted: Boolean,
    showingAnswers: Boolean,
    isCorrect: Boolean?,
    onSelectAnswer: (String) -> Unit
) {
    val wrongCount = item.wrongCount
    val statusColors = rememberPracticeStatusColors()
    val borderColor = wrongCountHighlightColor(
        wrongCount = wrongCount,
        warningColor = statusColors.warning,
        errorColor = MaterialTheme.colorScheme.error
    )

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
                    Text(
                        item.questionText.ifBlank { stringResource(R.string.reader_fill_blank) },
                        style = ArticleTypography.QuestionStem
                    )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    optionLetters.forEach { letter ->
                        val isSelected = selectedAnswer == letter
                        val correctAnswer = item.correctAnswer
                        val optionColors = resolveOptionColors(
                            isSelected = isSelected,
                            isSubmitted = isSubmitted,
                            showingAnswers = showingAnswers,
                            isCorrect = isCorrect,
                            correctAnswer = correctAnswer,
                            optionLetter = letter,
                            colors = statusColors,
                            primaryColor = MaterialTheme.colorScheme.primary,
                            primaryContainer = MaterialTheme.colorScheme.primaryContainer,
                            errorColor = MaterialTheme.colorScheme.error,
                            errorContainer = MaterialTheme.colorScheme.errorContainer,
                            defaultContentColor = MaterialTheme.colorScheme.onSurface
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = optionColors.container),
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
                                color = optionColors.content,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (isSubmitted || showingAnswers) {
                    val answer = item.correctAnswer
                    if (answer != null) {
                        PracticeResultRow(correctAnswer = answer, isCorrect = isCorrect)
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
    fallbackTitle: String = stringResource(R.string.reader_comment_label)
) {
    val wrongCount = item.wrongCount
    val statusColors = rememberPracticeStatusColors()
    val borderColor = wrongCountHighlightColor(
        wrongCount = wrongCount,
        warningColor = statusColors.warning,
        errorColor = MaterialTheme.colorScheme.error
    )

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
                        val optionColors = resolveOptionColors(
                            isSelected = isSelected,
                            isSubmitted = isSubmitted,
                            showingAnswers = showingAnswers,
                            isCorrect = isCorrect,
                            correctAnswer = correctAnswer,
                            optionLetter = letter,
                            colors = statusColors,
                            primaryColor = MaterialTheme.colorScheme.primary,
                            primaryContainer = MaterialTheme.colorScheme.primaryContainer,
                            errorColor = MaterialTheme.colorScheme.error,
                            errorContainer = MaterialTheme.colorScheme.errorContainer,
                            defaultContentColor = MaterialTheme.colorScheme.onSurface
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = optionColors.container),
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
                                color = optionColors.content,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (isSubmitted || showingAnswers) {
                    val answer = item.correctAnswer
                    if (answer != null) {
                        PracticeResultRow(correctAnswer = answer, isCorrect = isCorrect)
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

private fun isHttpUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val trimmed = url.trim()
    return trimmed.startsWith("http://") || trimmed.startsWith("https://")
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
        Row(modifier = modifier.fillMaxSize()) {
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
    val bodyStyle = ArticleTypography.ReaderBody

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
                Text(stringResource(R.string.reader_translate), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reader_question_count, state.items.size),
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
    val statusColors = rememberPracticeStatusColors()
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
                label = { Text(stringResource(R.string.question_input_translation)) },
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
                            stringResource(R.string.question_ai_scoring),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (score != null) {
                    val scoreColors = translationScoreColors(
                        score = score.score,
                        colors = statusColors,
                        errorColor = MaterialTheme.colorScheme.error,
                        errorContainer = MaterialTheme.colorScheme.errorContainer
                    )
                    // Score badge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = scoreColors.container
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.question_ai_score_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${score.score}/${score.maxScore}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreColors.content
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
                            containerColor = statusColors.successSubtleContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                stringResource(R.string.question_reference_translation),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusColors.success
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
                        stringResource(R.string.question_generating_reference),
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
                                stringResource(R.string.question_translation_notes),
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
                ) { Text(stringResource(R.string.question_submit_translation)) }

                if (hasAnswers) {
                    OutlinedButton(
                        onClick = onShowAnswers,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.question_view_reference)) }
                }
            } else {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.question_retranslate)) }
            }
        }

        Text(
            if (!state.isSubmitted && !state.showingAnswers)
                stringResource(R.string.question_translation_hint_before)
            else
                stringResource(R.string.question_translation_hint_after),
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
        Row(modifier = modifier.fillMaxSize()) {
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
            Text(stringResource(R.string.question_answer_section), style = MaterialTheme.typography.titleSmall)
            if (optionLetters.isEmpty()) {
                Text(
                    stringResource(R.string.reader_no_paragraph_options),
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
    val statusColors = rememberPracticeStatusColors()
    val borderColor = wrongCountHighlightColor(
        wrongCount = wrongCount,
        warningColor = statusColors.warning,
        errorColor = MaterialTheme.colorScheme.error
    )

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
                        item.questionText.ifBlank { stringResource(R.string.reader_fill_blank) },
                        style = ArticleTypography.QuestionStem
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    optionLetters.forEach { letter ->
                        val isSelected = selectedAnswer == letter
                        val correctAnswer = item.correctAnswer
                        val optionColors = resolveOptionColors(
                            isSelected = isSelected,
                            isSubmitted = isSubmitted,
                            showingAnswers = showingAnswers,
                            isCorrect = isCorrect,
                            correctAnswer = correctAnswer,
                            optionLetter = letter,
                            colors = statusColors,
                            primaryColor = MaterialTheme.colorScheme.primary,
                            primaryContainer = MaterialTheme.colorScheme.primaryContainer,
                            errorColor = MaterialTheme.colorScheme.error,
                            errorContainer = MaterialTheme.colorScheme.errorContainer,
                            defaultContentColor = MaterialTheme.colorScheme.onSurface
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = optionColors.container),
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
                                color = optionColors.content,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (isSubmitted || showingAnswers) {
                    val answer = item.correctAnswer
                    if (answer != null) {
                        PracticeResultRow(correctAnswer = answer, isCorrect = isCorrect)
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
    onToggleWritingPractice: (Boolean) -> Unit,
    onRefreshWritingPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
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
                onToggleWritingPractice = onToggleWritingPractice,
                onRefreshWritingPractice = onRefreshWritingPractice,
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
                onToggleWritingPractice = onToggleWritingPractice,
                onRefreshWritingPractice = onRefreshWritingPractice,
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
                    Text(stringResource(R.string.question_stem), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        item?.questionText.orEmpty(),
                        style = ArticleTypography.QuestionStem
                    )
                    if (!group.sourceUrl.isNullOrBlank()) {
                        val sourceUrl = group.sourceUrl!!.trim()
                        val canOpen = isHttpUrl(sourceUrl)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.question_stem_source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            sourceUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (canOpen) Modifier.clickable { uriHandler.openUri(sourceUrl) } else Modifier
                        )
                    } else {
                        Text(
                            stringResource(R.string.question_stem_source_empty),
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
                                stringResource(R.string.question_searching),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(onClick = onSearchPromptSource) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.question_reenter_source))
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
                        Text(stringResource(R.string.question_background_material), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(group.passageText, style = ArticleTypography.QuestionSupport)
                    }
                }
            }
        }

        item(key = "writing_sample_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.question_reference_essay), style = MaterialTheme.typography.titleSmall)
                if (state.isSearchingWritingSample) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.question_searching), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = onSearchSample) { Text(stringResource(R.string.question_research)) }
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
                        val canOpen = isHttpUrl(sampleUrl)
                        Text(
                            sampleUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (canOpen) Modifier.clickable { uriHandler.openUri(sampleUrl) } else Modifier
                        )
                    }
                    if (!sampleText.isNullOrBlank()) {
                        Text(sampleText, style = ArticleTypography.QuestionSupport)
                    } else if (!state.writingSampleError.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.question_research_failed, state.writingSampleError),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            stringResource(R.string.question_no_sample),
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
    onToggleWritingPractice: (Boolean) -> Unit,
    onRefreshWritingPractice: () -> Unit,
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
                Text(stringResource(R.string.question_answer_section), style = MaterialTheme.typography.titleSmall)
                if (state.isOcrWriting) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.question_ocr_processing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else if (state.isCompressingWriting) {
                    Text(stringResource(R.string.question_compressing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item(key = "writing_practice_mode") {
            WritingPracticeModePanel(
                state = state,
                onToggleWritingPractice = onToggleWritingPractice,
                onRefreshWritingPractice = onRefreshWritingPractice
            )
        }

        item(key = "writing_input") {
            if (item == null) return@item
            OutlinedTextField(
                value = state.selectedAnswers[item.id].orEmpty(),
                onValueChange = { onSelectAnswer(item.id, it) },
                label = { Text(stringResource(R.string.question_input_essay)) },
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
                    Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.question_scan_cd), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.question_scan_essay))
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
                    ) { Text(stringResource(R.string.question_submit_review)) }
                } else {
                    Button(
                        onClick = onRetryPractice,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.question_redo)) }
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
                    Text(stringResource(R.string.question_reviewing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (score != null) {
                WritingScoreCard(score = score)
            }
            if (state.writingPracticeEnabled && state.writingPracticeUsage.isNotEmpty()) {
                WritingPracticeUsageCard(state = state)
            }
        }

        item(key = "writing_bottom") {
            Spacer(Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WritingPracticeModePanel(
    state: ReaderUiState,
    onToggleWritingPractice: (Boolean) -> Unit,
    onRefreshWritingPractice: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.question_writing_practice_mode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.question_writing_practice_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.writingPracticeEnabled,
                    onCheckedChange = onToggleWritingPractice,
                    enabled = !state.isSubmitted
                )
            }

            AnimatedVisibility(visible = state.writingPracticeEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isPreparingWritingPractice) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.question_writing_practice_preparing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (state.writingPracticePhrases.isNotEmpty()) {
                        Text(
                            stringResource(R.string.question_writing_practice_required),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.writingPracticePhrases.forEach { item ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(
                                            item.phrase,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (item.reason.isNotBlank()) {
                                            Text(
                                                item.reason,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!state.writingPracticeError.isNullOrBlank()) {
                        Text(
                            state.writingPracticeError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (!state.isSubmitted && !state.isPreparingWritingPractice) {
                        OutlinedButton(onClick = onRefreshWritingPractice) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.question_writing_practice_refresh))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WritingPracticeUsageCard(state: ReaderUiState) {
    val usedCount = state.writingPracticeUsage.count { it.used }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.question_writing_practice_usage, usedCount, state.writingPracticeUsage.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.writingPracticeUsage.forEach { item ->
                    val colors = if (item.used) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    }
                    Card(colors = colors) {
                        Text(
                            item.requirement.phrase,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (item.used) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.question_writing_practice_usage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WritingScoreCard(score: WritingScore) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.question_score_label),
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
                stringResource(R.string.question_word_count_format, score.wordCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.question_sub_content, score.subScores.content), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.question_sub_language, score.subScores.language), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.question_sub_structure, score.subScores.structure), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.question_sub_format, score.subScores.format), style = MaterialTheme.typography.bodySmall)
            }
            if (score.deductions.isNotEmpty()) {
                Text(stringResource(R.string.question_deductions), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                score.deductions.forEach { d ->
                    Text("${d.reason} (${d.score})", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (score.summary.isNotBlank()) {
                Text(stringResource(R.string.question_overall), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(score.summary, style = MaterialTheme.typography.bodySmall)
            }
            if (score.suggestions.isNotEmpty()) {
                Text(stringResource(R.string.question_suggestions), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                score.suggestions.forEach { s ->
                    Text("• $s", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
