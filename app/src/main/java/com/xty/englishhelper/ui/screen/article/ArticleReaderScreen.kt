package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import coil.compose.AsyncImage
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleStatistics
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.ExamPaperBlueprint
import com.xty.englishhelper.domain.model.ExamPaperProfile
import com.xty.englishhelper.ui.components.reading.HighlightedParagraphText
import com.xty.englishhelper.ui.components.reading.ParagraphBlock
import com.xty.englishhelper.ui.components.reading.TranslationBlock
import com.xty.englishhelper.ui.components.reading.TtsPlaybackBar
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect
import com.xty.englishhelper.ui.designsystem.tokens.ArticleTypography
import com.xty.englishhelper.ui.designsystem.tokens.LocalReaderColors
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArticleReaderScreen(
    onBack: () -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    onOpenQuestionGroup: ((Long) -> Unit)? = null,
    viewModel: ArticleReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val article = uiState.article
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val notebookPulseScale = remember { Animatable(1f) }
    val notebookPulseTint = remember { Animatable(0f) }
    var followTts by rememberSaveable { mutableStateOf(true) }
    var showGenerateDialog by rememberSaveable { mutableStateOf(false) }
    var selectedGenerateId by rememberSaveable { mutableStateOf("read") }
    var selectedPaperProfile by rememberSaveable { mutableStateOf(ExamPaperProfile.ENGLISH_ONE.name) }

    data class GenerateOption(
        val id: String,
        val label: String,
        val description: String,
        val questionType: QuestionType,
        val variant: String? = null
    )

    val paperProfile = runCatching { ExamPaperProfile.valueOf(selectedPaperProfile) }
        .getOrDefault(ExamPaperProfile.ENGLISH_ONE)
    val blueprint = remember(paperProfile) {
        ExamPaperBlueprint.forYear(
            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
            paperProfile
        )
    }
    val generateOptions = remember(blueprint, paperProfile) {
        listOf(
            GenerateOption("read", "阅读理解", "需要 4 篇，每篇生成 5 题", QuestionType.READING_COMPREHENSION),
            GenerateOption("cloze", "完形填空", "需要 1 篇，生成 20 空", QuestionType.CLOZE),
            GenerateOption(
                "translation",
                if (paperProfile == ExamPaperProfile.ENGLISH_ONE) "翻译（英语一）" else "翻译（英语二）",
                if (paperProfile == ExamPaperProfile.ENGLISH_ONE) "需要 1 篇，5 处划线" else "需要 1 篇，整段翻译",
                QuestionType.TRANSLATION,
                if (paperProfile == ExamPaperProfile.ENGLISH_ONE) "ENG1" else "ENG2"
            ),
            GenerateOption("write_small", "写作（小作文）", "需要 1 篇作为命题素材", QuestionType.WRITING, "SMALL"),
            GenerateOption("write_large", "写作（大作文）", "需要 1 篇作为命题素材", QuestionType.WRITING, "LARGE"),
            GenerateOption(
                "special",
                "轮换新题型：${blueprint.specialQuestionType.displayName}",
                "本套卷只收集这一种新题型",
                blueprint.specialQuestionType
            )
        )
    }

    val selectedGenerate = generateOptions.firstOrNull { it.id == selectedGenerateId } ?: generateOptions.first()

    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect {
            onBack()
        }
    }

    LaunchedEffect(uiState.analyzeError) {
        uiState.analyzeError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.ttsState.error) {
        uiState.ttsState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTtsError()
        }
    }

    LaunchedEffect(uiState.generateError) {
        uiState.generateError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.generateMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
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

    val ttsSessionId = article?.let { "article:${it.id}" }
    val isArticleSpeaking = uiState.ttsState.isSpeaking && uiState.ttsState.sessionId == ttsSessionId
    val canSpeak = article != null &&
        (uiState.paragraphs?.isNotEmpty() == true) &&
        uiState.ttsState.isReady
    val topBarTitleText = article?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.article_reading)

    AppTopBarEffect(
        title = {
            Text(
                text = topBarTitleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = { AppTopBarBackButton(onBack) },
        actions = {
            val notebookBaseTint = if (uiState.collectedWords.isNotEmpty()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val notebookTint = lerp(
                notebookBaseTint,
                MaterialTheme.colorScheme.primary,
                notebookPulseTint.value
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderTopActionButton(
                    icon = if (isArticleSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isArticleSpeaking) stringResource(R.string.article_tts_pause) else stringResource(R.string.article_tts_play),
                    onClick = { viewModel.toggleSpeakArticle() },
                    enabled = canSpeak,
                    active = isArticleSpeaking
                )
                ReaderTopActionButton(
                    icon = Icons.Default.Translate,
                    contentDescription = if (uiState.translationEnabled) stringResource(R.string.article_translate_off) else stringResource(R.string.article_translate_toggle),
                    onClick = { viewModel.toggleTranslation() },
                    active = uiState.translationEnabled
                )
                ReaderTopActionButton(
                    icon = Icons.Default.AutoAwesome,
                    contentDescription = if (uiState.isAddingToPaper) "正在加入试卷" else "组卷",
                    onClick = { showGenerateDialog = true },
                    enabled = !uiState.isGeneratingQuestions && !uiState.isAddingToPaper,
                    active = uiState.isAddingToPaper
                )
                if (article?.isSaved == false) {
                    ReaderTopActionButton(
                        icon = Icons.Default.Download,
                        contentDescription = if (uiState.isSavingToLocal) stringResource(R.string.article_saving) else stringResource(R.string.article_save_to_local),
                        onClick = { viewModel.saveToLocal() },
                        enabled = !uiState.isSavingToLocal,
                        active = uiState.isSavingToLocal
                    )
                }
                ReaderTopActionButton(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = stringResource(R.string.article_notebook),
                    onClick = { viewModel.toggleNotebook() },
                    tint = notebookTint,
                    pulseScale = notebookPulseScale.value,
                    badgeCount = uiState.collectedWords.size,
                    active = uiState.collectedWords.isNotEmpty()
                )
            }
        }
    )

    Scaffold(
        bottomBar = {
            val ttsSessionId = article?.let { "article:${it.id}" }
            val isCurrent = ttsSessionId != null && uiState.ttsState.sessionId == ttsSessionId
            if (isCurrent) {
                TtsPlaybackBar(
                    isSpeaking = uiState.ttsState.isSpeaking,
                    currentIndex = uiState.ttsState.currentIndex,
                    total = uiState.ttsState.total,
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
        if (article == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            ArticleReaderContent(
                article = article,
                paragraphs = uiState.paragraphs ?: emptyList(),
                wordLinkMap = uiState.wordLinkMap,
                sentencesByParagraph = uiState.sentencesByParagraph,
                paragraphAnalysis = uiState.paragraphAnalysis,
                analyzingParagraphId = uiState.analyzingParagraphId,
                ttsState = uiState.ttsState,
                followTts = followTts,
                translationEnabled = uiState.translationEnabled,
                paragraphTranslations = uiState.paragraphTranslations,
                translatingParagraphIds = uiState.translatingParagraphIds,
                translationFailedParagraphIds = uiState.translationFailedParagraphIds,
                statistics = uiState.statistics,
                expandedParagraphIds = uiState.expandedParagraphIds,
                onAnalyzeParagraph = { paragraphId, text ->
                    viewModel.analyzeParagraph(paragraphId, text)
                },
                onRetryTranslateParagraph = { paragraphId, text ->
                    viewModel.retryTranslateParagraph(paragraphId, text)
                },
                onToggleAnalysisExpanded = { paragraphId ->
                    viewModel.toggleParagraphAnalysisExpanded(paragraphId)
                },
                onWordClick = onWordClick,
                onCollectWord = { word, context ->
                    viewModel.collectWord(word, context)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                listState = listState
            )
        }
    }

    // Collection notebook sheet
    if (uiState.showNotebook) {
        CollectionNotebookSheet(
            collectedWords = uiState.collectedWords,
            dictionaries = uiState.dictionaries,
            onLoadUnits = { dictId -> viewModel.getUnitsForDictionary(dictId) },
            onRemoveWord = { viewModel.removeCollectedWord(it) },
            onAddToDictionary = { word, dictId, unitId ->
                viewModel.addToDictionary(word, dictId, unitId)
            },
            onDismiss = { viewModel.dismissNotebook() }
        )
    }

    if (showGenerateDialog) {
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            title = { Text("加入每日考研试卷") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "文章会保存到本地并加入当天试卷；9 个来源槽位凑齐后自动后台出题。轮换新题型每套只需要当前显示的一种。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("考试类型", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = paperProfile == ExamPaperProfile.ENGLISH_ONE,
                            onClick = { selectedPaperProfile = ExamPaperProfile.ENGLISH_ONE.name }
                        )
                        Text(
                            "英语一",
                            modifier = Modifier.clickable {
                                selectedPaperProfile = ExamPaperProfile.ENGLISH_ONE.name
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        RadioButton(
                            selected = paperProfile == ExamPaperProfile.ENGLISH_TWO,
                            onClick = { selectedPaperProfile = ExamPaperProfile.ENGLISH_TWO.name }
                        )
                        Text(
                            "英语二",
                            modifier = Modifier.clickable {
                                selectedPaperProfile = ExamPaperProfile.ENGLISH_TWO.name
                            }
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        generateOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGenerateId = option.id },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedGenerateId == option.id,
                                    onClick = { selectedGenerateId = option.id }
                                )
                                Column {
                                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        option.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isGeneratingQuestions && !uiState.isAddingToPaper,
                    onClick = {
                        showGenerateDialog = false
                        viewModel.addToExamPaper(
                            profile = paperProfile,
                            questionType = selectedGenerate.questionType,
                            variant = selectedGenerate.variant
                        )
                    }
                ) { Text("加入试卷") }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }


}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleReaderContent(
    article: Article,
    paragraphs: List<ArticleParagraph>,
    wordLinkMap: Map<String, List<ArticleWordLink>>,
    sentencesByParagraph: Map<Long, List<ArticleSentence>>,
    paragraphAnalysis: Map<Long, ParagraphAnalysisResult>,
    analyzingParagraphId: Long,
    ttsState: com.xty.englishhelper.domain.model.TtsState,
    followTts: Boolean,
    translationEnabled: Boolean,
    paragraphTranslations: Map<Long, String>,
    translatingParagraphIds: Set<Long>,
    translationFailedParagraphIds: Set<Long>,
    statistics: ArticleStatistics?,
    expandedParagraphIds: Set<Long>,
    onAnalyzeParagraph: (Long, String) -> Unit,
    onRetryTranslateParagraph: (Long, String) -> Unit,
    onToggleAnalysisExpanded: (Long) -> Unit,
    onWordClick: (Long, Long) -> Unit,
    onCollectWord: (word: String, contextSentence: String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState
) {
    val coverUri = article.coverImageUri ?: article.coverImageUrl
    val displayWordCount = article.wordCount.takeIf { it > 0 }
        ?: statistics?.wordCount?.takeIf { it > 0 }
    val wordCountText = displayWordCount?.let { stringResource(R.string.article_word_count_display, it) }
    val parseStatusProcessingText = stringResource(R.string.article_parsing)
    val parseStatusFailedText = stringResource(R.string.article_parse_failed)
    val metaParts = remember(article.author, article.source, article.domain, displayWordCount, article.parseStatus) {
        buildList {
            if (article.author.isNotBlank()) add(article.author)
            if (article.source.isNotBlank()) add(article.source)
            else if (article.domain.isNotBlank()) add(article.domain)
            if (wordCountText != null) add(wordCountText)
            val statusText = when (article.parseStatus) {
                ArticleParseStatus.PROCESSING -> parseStatusProcessingText
                ArticleParseStatus.FAILED -> parseStatusFailedText
                else -> null
            }
            statusText?.let(::add)
        }
    }

    val ttsSessionId = "article:${article.id}"
    val ttsActive = ttsState.sessionId == ttsSessionId
    val prewarmActive = ttsState.isPrewarming &&
        ttsState.prewarmSessionId == ttsSessionId &&
        ttsState.prewarmTotal > 0
    val prewarmDone = if (prewarmActive) {
        ttsState.prewarmDone.coerceIn(0, ttsState.prewarmTotal)
    } else 0
    val speakableParagraphs = remember(paragraphs) {
        paragraphs.filter { it.paragraphType != ParagraphType.IMAGE && it.text.isNotBlank() }
    }
    val spokenParagraphId = if (ttsActive && ttsState.currentIndex > 0) {
        speakableParagraphs.getOrNull(ttsState.currentIndex - 1)?.id
    } else null
    val headerCount = 1 + (if (prewarmActive) 1 else 0) + 1

    LaunchedEffect(ttsActive, ttsState.currentIndex, paragraphs.size, headerCount, followTts) {
        if (!ttsActive || !followTts) return@LaunchedEffect
        val targetIndex = if (ttsState.currentIndex <= 0) {
            0
        } else {
            val targetId = spokenParagraphId ?: return@LaunchedEffect
            val paraIndex = paragraphs.indexOfFirst { it.id == targetId }
            if (paraIndex < 0) 0 else headerCount + paraIndex
        }
        listState.animateScrollToItem(targetIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = ArticleTypography.HorizontalPadding,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "hero") {
            ReaderHeroCard(
                article = article,
                coverUri = coverUri,
                metaParts = metaParts,
                translationEnabled = translationEnabled,
                isArticleSpeaking = ttsActive && ttsState.isSpeaking
            )
        }

        if (prewarmActive) {
            item(key = "tts_prewarm") {
                Surface(
                    shape = ArticleShapes.Control,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.article_tts_prewarm, prewarmDone, ttsState.prewarmTotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "divider") {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
        }

        // Paragraphs
        items(paragraphs, key = { it.id }) { paragraph ->
            ParagraphBlock(
                paragraph = paragraph,
                wordLinkMap = wordLinkMap,
                analysis = paragraphAnalysis[paragraph.id],
                isAnalyzing = analyzingParagraphId == paragraph.id,
                isSpeaking = spokenParagraphId == paragraph.id,
                translationEnabled = translationEnabled,
                translation = paragraphTranslations[paragraph.id],
                isTranslating = paragraph.id in translatingParagraphIds,
                translationFailed = paragraph.id in translationFailedParagraphIds,
                analysisExpanded = paragraph.id in expandedParagraphIds,
                onAnalyze = { onAnalyzeParagraph(paragraph.id, paragraph.text) },
                onRetryTranslate = { onRetryTranslateParagraph(paragraph.id, paragraph.text) },
                onToggleAnalysisExpanded = { onToggleAnalysisExpanded(paragraph.id) },
                onWordClick = onWordClick,
                onCollectWord = onCollectWord
            )
            Spacer(Modifier.height(ArticleTypography.ParagraphSpacing))
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderHeroCard(
    article: Article,
    coverUri: String?,
    metaParts: List<String>,
    translationEnabled: Boolean,
    isArticleSpeaking: Boolean
) {
    val readerColors = LocalReaderColors.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = stringResource(R.string.article_cover),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Source in MintTeal + rest in gray
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (article.source.isNotBlank()) {
                    Text(
                        text = article.source,
                        style = ArticleTypography.ReaderMeta,
                        color = readerColors.quoteBar
                    )
                }
                if (article.author.isNotBlank() || article.domain.isNotBlank()) {
                    Text(
                        text = buildString {
                            if (article.author.isNotBlank()) append(article.author)
                            if (article.domain.isNotBlank()) {
                                if (isNotBlank()) append(" · ")
                                append(article.domain)
                            }
                        },
                        style = ArticleTypography.ReaderMeta,
                        color = readerColors.meta
                    )
                }
            }

            // Title
            Text(
                text = article.title,
                style = ArticleTypography.ReaderTitle,
                color = readerColors.title
            )

            // Thin rule
            HorizontalDivider(
                modifier = Modifier.width(40.dp),
                color = readerColors.quoteBar,
                thickness = 1.dp
            )

            // Summary / 导语
            if (article.summary.isNotBlank()) {
                Text(
                    text = article.summary,
                    style = ArticleTypography.ReaderQuote,
                    color = readerColors.body
                )
            }
        }
    }
}

@Composable
private fun ReaderTopActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    tint: Color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    pulseScale: Float = 1f,
    badgeCount: Int = 0
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
        active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    Surface(
        modifier = modifier,
        shape = ArticleShapes.Control,
        color = containerColor
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val iconModifier = Modifier.graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            if (badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge {
                            Text("$badgeCount")
                        }
                    }
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = tint,
                        modifier = iconModifier
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = iconModifier
                )
            }
        }
    }
}

@Composable
private fun ReaderMetaPill(
    text: String,
    emphasized: Boolean = false
) {
    Surface(
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = ArticleShapes.Chip
    ) {
        Text(
            text = text,
            style = ArticleTypography.ReaderMeta,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// ParagraphBlock, TtsPlaybackBar, TranslationBlock, HighlightedParagraphText,
// extractWordAtOffset, extractContextSentence are now in
// com.xty.englishhelper.ui.components.reading.ReadingComponents

private fun parseStatusText(status: ArticleParseStatus): String? {
    return when (status) {
        ArticleParseStatus.PROCESSING -> "解析中…"
        ArticleParseStatus.FAILED -> "解析失败"
        else -> null
    }
}
