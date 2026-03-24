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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.xty.englishhelper.ui.components.reading.HighlightedParagraphText
import com.xty.englishhelper.ui.components.reading.ParagraphBlock
import com.xty.englishhelper.ui.components.reading.TranslationBlock
import com.xty.englishhelper.ui.components.reading.TtsPlaybackBar
import com.xty.englishhelper.ui.designsystem.tokens.ArticleTypography
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
    var showToolsMenu by rememberSaveable { mutableStateOf(false) }
    var draftPaperTitle by rememberSaveable { mutableStateOf("") }
    var selectedGenerateId by rememberSaveable { mutableStateOf("read") }

    data class GenerateOption(
        val id: String,
        val label: String,
        val description: String,
        val questionType: QuestionType,
        val variant: String? = null
    )

    val generateOptions = remember {
        listOf(
            GenerateOption("read", "阅读理解", "5 题 · 400-500 词", QuestionType.READING_COMPREHENSION),
            GenerateOption("cloze", "完形填空", "20 空 · 280-360 词", QuestionType.CLOZE),
            GenerateOption("trans_e1", "翻译（英语一）", "长文 5 处划线", QuestionType.TRANSLATION, "ENG1"),
            GenerateOption("trans_e2", "翻译（英语二）", "整段短文", QuestionType.TRANSLATION, "ENG2"),
            GenerateOption("write_small", "写作（小作文）", "应用文 · 100 词", QuestionType.WRITING, "SMALL"),
            GenerateOption("write_large", "写作（大作文）", "图表/图片作文 · 160-200 词", QuestionType.WRITING, "LARGE"),
            GenerateOption("para_order", "新题型：段落排序", "8 段 · 5 空", QuestionType.PARAGRAPH_ORDER),
            GenerateOption("sentence_insert", "新题型：句子插入", "5 空 · 7 句", QuestionType.SENTENCE_INSERTION),
            GenerateOption("comment_match", "新题型：评论观点匹配", "5 题 · 7 选项", QuestionType.COMMENT_OPINION_MATCH),
            GenerateOption("subheading_match", "新题型：小标题匹配", "5 题 · 7 标题", QuestionType.SUBHEADING_MATCH),
            GenerateOption("info_match", "新题型：信息匹配", "5 题 · 7 信息", QuestionType.INFORMATION_MATCH)
        )
    }

    val selectedGenerate = generateOptions.firstOrNull { it.id == selectedGenerateId } ?: generateOptions.first()

    val defaultPaperTitle = remember(article?.title) {
        val safeTitle = article?.title?.takeIf { it.isNotBlank() } ?: "未命名文章"
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        "文章出题 - $safeTitle - $date"
    }

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

    LaunchedEffect(showGenerateDialog, defaultPaperTitle) {
        if (showGenerateDialog) {
            draftPaperTitle = defaultPaperTitle
        }
    }

    val ttsSessionId = article?.let { "article:${it.id}" }
    val isArticleSpeaking = uiState.ttsState.isSpeaking && uiState.ttsState.sessionId == ttsSessionId
    val canSpeak = article != null &&
        (uiState.paragraphs?.isNotEmpty() == true) &&
        uiState.ttsState.isReady

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: "文章", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showToolsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "阅读工具菜单")
                        }
                        DropdownMenu(
                            expanded = showToolsMenu,
                            onDismissRequest = { showToolsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isArticleSpeaking) "暂停朗读" else "开始朗读") },
                                onClick = {
                                    showToolsMenu = false
                                    viewModel.toggleSpeakArticle()
                                },
                                enabled = canSpeak
                            )
                            DropdownMenuItem(
                                text = { Text(if (uiState.translationEnabled) "关闭翻译" else "显示翻译") },
                                onClick = {
                                    showToolsMenu = false
                                    viewModel.toggleTranslation()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (uiState.isGeneratingQuestions) "出题中…" else "基于文章出题") },
                                onClick = {
                                    showToolsMenu = false
                                    showGenerateDialog = true
                                },
                                enabled = article != null && !uiState.isGeneratingQuestions
                            )
                            if (article?.isSaved == false) {
                                DropdownMenuItem(
                                    text = { Text(if (uiState.isSavingToLocal) "保存中…" else "保存到本地") },
                                    onClick = {
                                        showToolsMenu = false
                                        viewModel.saveToLocal()
                                    },
                                    enabled = !uiState.isSavingToLocal
                                )
                            }
                        }
                    }
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
                    IconButton(onClick = { viewModel.toggleNotebook() }) {
                        if (uiState.collectedWords.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text("${uiState.collectedWords.size}")
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "收纳本",
                                    tint = notebookTint,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = notebookPulseScale.value
                                        scaleY = notebookPulseScale.value
                                    }
                                )
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "收纳本",
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
        },
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
                Text("加载中…", style = MaterialTheme.typography.bodyLarge)
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
                canSpeak = canSpeak,
                isArticleSpeaking = isArticleSpeaking,
                isSavingToLocal = uiState.isSavingToLocal,
                isGeneratingQuestions = uiState.isGeneratingQuestions,
                onToggleSpeak = viewModel::toggleSpeakArticle,
                onToggleTranslation = viewModel::toggleTranslation,
                onGenerateQuestions = { showGenerateDialog = true },
                onSaveToLocal = viewModel::saveToLocal,
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
            title = { Text("文章出题") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = draftPaperTitle,
                        onValueChange = { draftPaperTitle = it },
                        label = { Text("试卷名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    enabled = !uiState.isGeneratingQuestions,
                    onClick = {
                        showGenerateDialog = false
                        viewModel.generateQuestions(
                            paperTitle = draftPaperTitle,
                            questionType = selectedGenerate.questionType,
                            variant = selectedGenerate.variant
                        )
                    }
                ) { Text("开始出题") }
            },
            dismissButton = {
                TextButton(onClick = { showGenerateDialog = false }) { Text("取消") }
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
    canSpeak: Boolean,
    isArticleSpeaking: Boolean,
    isSavingToLocal: Boolean,
    isGeneratingQuestions: Boolean,
    onToggleSpeak: () -> Unit,
    onToggleTranslation: () -> Unit,
    onGenerateQuestions: () -> Unit,
    onSaveToLocal: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState
) {
    val coverUri = article.coverImageUri ?: article.coverImageUrl
    val displayWordCount = article.wordCount.takeIf { it > 0 }
        ?: statistics?.wordCount?.takeIf { it > 0 }
    val metaParts = remember(article.author, article.source, displayWordCount) {
        buildList {
            if (article.author.isNotBlank()) add(article.author)
            if (article.source.isNotBlank()) add(article.source)
            if (displayWordCount != null) add("$displayWordCount 词")
            parseStatusText(article.parseStatus)?.let(::add)
        }
    }
    val hasSummary = article.summary.isNotBlank()
    val hasQuickActions = true
    val headerCount = (if (coverUri != null) 1 else 0) + 1 + 1 +
        (if (hasSummary) 1 else 0) + (if (hasQuickActions) 1 else 0) + 1

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
        // Cover image
        if (coverUri != null) {
            item(key = "cover") {
                AsyncImage(
                    model = coverUri,
                    contentDescription = "封面",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // Title
        item(key = "title") {
            Text(
                article.title,
                style = ArticleTypography.ReaderTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Meta row
        item(key = "meta") {
            if (metaParts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metaParts.forEach { part ->
                        ReaderMetaPill(part)
                    }
                }
            }
        }

        // Summary
        if (hasSummary) {
            item(key = "summary") {
                val quoteColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = quoteColor,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        article.summary,
                        style = ArticleTypography.ReaderQuote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "quick_actions") {
            ReaderQuickActionsPanel(
                canSpeak = canSpeak,
                isArticleSpeaking = isArticleSpeaking,
                translationEnabled = translationEnabled,
                isSavingToLocal = isSavingToLocal,
                isGeneratingQuestions = isGeneratingQuestions,
                showSaveAction = article.isSaved == false,
                onToggleSpeak = onToggleSpeak,
                onToggleTranslation = onToggleTranslation,
                onGenerateQuestions = onGenerateQuestions,
                onSaveToLocal = onSaveToLocal
            )
        }

        if (prewarmActive) {
            item(key = "tts_prewarm") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "正在缓存朗读音频 $prewarmDone/${ttsState.prewarmTotal}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
private fun ReaderQuickActionsPanel(
    canSpeak: Boolean,
    isArticleSpeaking: Boolean,
    translationEnabled: Boolean,
    isSavingToLocal: Boolean,
    isGeneratingQuestions: Boolean,
    showSaveAction: Boolean,
    onToggleSpeak: () -> Unit,
    onToggleTranslation: () -> Unit,
    onGenerateQuestions: () -> Unit,
    onSaveToLocal: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "阅读工具",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onToggleSpeak,
                    enabled = canSpeak,
                    label = {
                        Text(if (isArticleSpeaking) "暂停朗读" else "开始朗读")
                    },
                    leadingIcon = {
                        Icon(
                            if (isArticleSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    }
                )
                AssistChip(
                    onClick = onToggleTranslation,
                    label = {
                        Text(if (translationEnabled) "关闭翻译" else "显示翻译")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Translate, contentDescription = null)
                    }
                )
                AssistChip(
                    onClick = onGenerateQuestions,
                    enabled = !isGeneratingQuestions,
                    label = {
                        Text(if (isGeneratingQuestions) "出题中…" else "基于文章出题")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Quiz, contentDescription = null)
                    }
                )
                if (showSaveAction) {
                    AssistChip(
                        onClick = onSaveToLocal,
                        enabled = !isSavingToLocal,
                        label = {
                            Text(if (isSavingToLocal) "保存中…" else "保存到本地")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderMetaPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(999.dp)
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
