package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
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
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleStatistics
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.ui.designsystem.tokens.ArticleTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    onBack: () -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    viewModel: ArticleReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val article = uiState.article
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var followTts by rememberSaveable { mutableStateOf(true) }

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
                    val ttsSessionId = article?.let { "article:${it.id}" }
                    val isArticleSpeaking = uiState.ttsState.isSpeaking && uiState.ttsState.sessionId == ttsSessionId
                    val canSpeak = article != null && (uiState.paragraphs?.isNotEmpty() == true) && uiState.ttsState.isReady

                    IconButton(
                        onClick = { viewModel.toggleSpeakArticle() },
                        enabled = canSpeak
                    ) {
                        Icon(
                            if (isArticleSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isArticleSpeaking) "暂停朗读" else "朗读"
                        )
                    }

                    // Translation toggle
                    IconButton(onClick = { viewModel.toggleTranslation() }) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = "翻译",
                            tint = if (uiState.translationEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Notebook button with badge
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
                                    contentDescription = "收纳本"
                                )
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "收纳本",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Save button for online/unsaved articles
                    if (article?.isSaved == false) {
                        TextButton(
                            onClick = { viewModel.saveToLocal() },
                            enabled = !uiState.isSavingToLocal
                        ) {
                            if (uiState.isSavingToLocal) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Text("保存")
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
}

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
    val metaParts = remember(article.author, article.source, displayWordCount) {
        buildList {
            if (article.author.isNotBlank()) add(article.author)
            if (article.source.isNotBlank()) add(article.source)
            if (displayWordCount != null) add("$displayWordCount 词")
        }
    }
    val hasSummary = article.summary.isNotBlank()
    val headerCount = (if (coverUri != null) 1 else 0) + 1 + 1 +
        (if (hasSummary) 1 else 0) + 1

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
                Text(
                    metaParts.joinToString(" · "),
                    style = ArticleTypography.ReaderMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

        if (prewarmActive) {
            item(key = "tts_prewarm") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Caching audio $prewarmDone/${ttsState.prewarmTotal}",
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

@Composable
private fun ParagraphBlock(
    paragraph: ArticleParagraph,
    wordLinkMap: Map<String, List<ArticleWordLink>>,
    analysis: ParagraphAnalysisResult?,
    isAnalyzing: Boolean,
    isSpeaking: Boolean,
    translationEnabled: Boolean,
    translation: String?,
    isTranslating: Boolean,
    translationFailed: Boolean,
    analysisExpanded: Boolean,
    onAnalyze: () -> Unit,
    onRetryTranslate: () -> Unit,
    onToggleAnalysisExpanded: () -> Unit,
    onWordClick: (Long, Long) -> Unit,
    onCollectWord: (word: String, contextSentence: String) -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val containerModifier = if (isSpeaking) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(highlightColor)
            .padding(6.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(
        modifier = containerModifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Render paragraph text based on type
        when (paragraph.paragraphType) {
            ParagraphType.HEADING -> {
                Text(
                    paragraph.text,
                    style = ArticleTypography.ReaderHeading,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            ParagraphType.QUOTE -> {
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
                        .padding(start = 12.dp)
                ) {
                    HighlightedParagraphText(
                        text = paragraph.text,
                        wordLinkMap = wordLinkMap,
                        onWordClick = onWordClick,
                        onCollectWord = onCollectWord,
                        style = ArticleTypography.ReaderQuote
                    )
                }
            }
            ParagraphType.IMAGE -> {
                // Image-only paragraph
                val imageUri = paragraph.imageUri ?: paragraph.imageUrl
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }
                if (paragraph.text.isNotBlank()) {
                    Text(
                        paragraph.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ParagraphType.LIST -> {
                Row {
                    Text("•  ", style = ArticleTypography.ReaderBody)
                    HighlightedParagraphText(
                        text = paragraph.text,
                        wordLinkMap = wordLinkMap,
                        onWordClick = onWordClick,
                        onCollectWord = onCollectWord
                    )
                }
            }
            else -> {
                HighlightedParagraphText(
                    text = paragraph.text,
                    wordLinkMap = wordLinkMap,
                    onWordClick = onWordClick,
                    onCollectWord = onCollectWord
                )
            }
        }

        // Translation block (inserted between text and analyze button)
        if (translationEnabled) {
            if (isTranslating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "翻译中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (translation != null) {
                    TranslationBlock(translation)
                }
                if (translation != null || translationFailed) {
                    TextButton(
                        onClick = onRetryTranslate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.padding(start = 10.dp)
                    ) {
                        Text("重试翻译", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Paragraph inline image
        val paraImage = paragraph.imageUri ?: paragraph.imageUrl
        if (paraImage != null && paragraph.paragraphType != ParagraphType.IMAGE) {
            AsyncImage(
                model = paraImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.FillWidth
            )
        }

        // Analyze button
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onAnalyze,
                enabled = !isAnalyzing,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .height(16.dp)
                            .width(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (analysis != null) "重新整理" else "整理", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Analysis result
        if (analysis != null) {
            ParagraphAnalysisCard(
                analysis = analysis,
                expanded = analysisExpanded,
                onToggleExpanded = onToggleAnalysisExpanded
            )
        }
    }
}

@Composable
private fun TtsPlaybackBar(
    isSpeaking: Boolean,
    currentIndex: Int,
    total: Int,
    followEnabled: Boolean,
    onToggleFollow: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit
) {
    if (total <= 0) return

    Surface(
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "朗读 ${currentIndex + 1}/$total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onToggleFollow) {
                    Text(if (followEnabled) "跟随" else "手动")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = currentIndex > 0) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一段")
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isSpeaking) "暂停" else "播放"
                    )
                }
                IconButton(onClick = onNext, enabled = currentIndex < total - 1) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一段")
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "停止")
                }
            }
        }
    }
}

@Composable
private fun TranslationBlock(translation: String) {
    val lineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    lineColor,
                    Offset(0f, 0f),
                    Offset(0f, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
            .padding(start = 10.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Text(
            translation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HighlightedParagraphText(
    text: String,
    wordLinkMap: Map<String, List<ArticleWordLink>>,
    onWordClick: (Long, Long) -> Unit,
    onCollectWord: (word: String, contextSentence: String) -> Unit,
    style: androidx.compose.ui.text.TextStyle = ArticleTypography.ReaderBody
) {
    // Tokenize and build annotated string with underlined dictionary words
    val underlineStyle = SpanStyle(
        textDecoration = TextDecoration.Underline,
        color = MaterialTheme.colorScheme.primary
    )

    val annotatedString = remember(text, wordLinkMap) {
        buildAnnotatedString {
            // Simple word-by-word matching
            val words = text.split(Regex("(?<=\\s)|(?=\\s)|(?<=[,.:;!?\"'()\\[\\]{}])|(?=[,.:;!?\"'()\\[\\]{}])"))
            for (word in words) {
                val cleaned = word.trim().lowercase().trimEnd(',', '.', ':', ';', '!', '?', '"', '\'', ')', ']', '}')
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
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        annotatedString,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { textLayoutResult = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(wordLinkMap) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        textLayoutResult?.let { layout ->
                            val charOffset = layout.getOffsetForPosition(tapOffset)
                            val annotations = annotatedString.getStringAnnotations("word", charOffset, charOffset)
                            if (annotations.isNotEmpty()) {
                                val (wId, dId) = annotations.first().item.split(":")
                                onWordClick(wId.toLong(), dId.toLong())
                            } else {
                                // Non-dictionary word → extract and collect
                                val tappedWord = extractWordAtOffset(text, charOffset)
                                if (tappedWord != null) {
                                    val context = extractContextSentence(text, charOffset)
                                    onCollectWord(tappedWord, context)
                                }
                            }
                        }
                    }
                )
            }
    )
}

private fun extractWordAtOffset(text: String, charOffset: Int): String? {
    if (charOffset < 0 || charOffset >= text.length) return null
    if (!text[charOffset].isLetter()) return null

    fun isWordInternal(index: Int): Boolean {
        val c = text[index]
        if (c.isLetter()) return true
        if (c == '\'' || c == '\u2019' || c == '-') {
            return index > 0 && text[index - 1].isLetter()
                && index < text.length - 1 && text[index + 1].isLetter()
        }
        return false
    }

    var start = charOffset
    while (start > 0 && isWordInternal(start - 1)) start--
    var end = charOffset
    while (end < text.length - 1 && isWordInternal(end + 1)) end++

    // Trim: ensure first and last chars are letters
    while (start <= end && !text[start].isLetter()) start++
    while (end >= start && !text[end].isLetter()) end--
    if (start > end) return null

    val word = text.substring(start, end + 1)
    return if (word.length >= 2 && word.all {
            it in 'A'..'Z' || it in 'a'..'z' || it == '\'' || it == '\u2019' || it == '-'
        }) word else null
}

private fun extractContextSentence(text: String, charOffset: Int): String {
    // Find sentence boundaries around the charOffset
    val sentenceEnders = setOf('.', '!', '?')

    var start = charOffset
    while (start > 0) {
        val prev = text[start - 1]
        if (prev in sentenceEnders && start < text.length && text[start] == ' ') break
        start--
    }

    var end = charOffset
    while (end < text.length) {
        if (text[end] in sentenceEnders) {
            end++
            break
        }
        end++
    }

    return text.substring(start, end.coerceAtMost(text.length)).trim()
}
