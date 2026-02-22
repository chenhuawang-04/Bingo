package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.SentenceAnalysisResult
import com.xty.englishhelper.ui.adaptive.currentWindowWidthClass
import com.xty.englishhelper.ui.adaptive.isExpandedOrMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderScreen(
    onBack: () -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    viewModel: ArticleReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val article by viewModel.getArticleFlow().collectAsState(null)
    val snackbarHostState = remember { SnackbarHostState() }
    val windowWidthClass = currentWindowWidthClass()
    val isWide = windowWidthClass.isExpandedOrMedium()
    val listState = rememberLazyListState()

    var showSentenceAnalysis by remember { mutableStateOf(false) }
    var selectedSentenceId by remember { mutableStateOf(0L) }
    var selectedSentenceText by remember { mutableStateOf("") }
    val analysisSheetState = rememberModalBottomSheetState()

    // Scroll to sentence when scrollToSentenceId is provided
    LaunchedEffect(viewModel.scrollToSentenceId, uiState.sentences) {
        if (viewModel.scrollToSentenceId > 0 && uiState.sentences != null) {
            val sentences = uiState.sentences!!
            val targetIndex = sentences.indexOfFirst { it.id == viewModel.scrollToSentenceId }
            if (targetIndex >= 0) {
                // +1 to account for the title item
                listState.animateScrollToItem(targetIndex + 1)
            }
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.title ?: "文章") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
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
            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ArticleReaderContent(
                        article = article!!,
                        sentences = uiState.sentences ?: emptyList(),
                        wordLinks = uiState.wordLinks ?: emptyList(),
                        sentenceAnalysis = uiState.sentenceAnalysis,
                        isAnalyzing = uiState.isAnalyzing,
                        onAnalyzeSentence = { sentenceId, text ->
                            selectedSentenceId = sentenceId
                            selectedSentenceText = text
                            showSentenceAnalysis = true
                            viewModel.analyzeSentence(sentenceId, text)
                        },
                        onWordClick = onWordClick,
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp),
                        listState = listState
                    )

                    // Statistics sidebar
                    uiState.statistics?.let { stats ->
                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("统计信息", style = MaterialTheme.typography.titleMedium)
                            Text("总词数: ${stats.wordCount}", style = MaterialTheme.typography.bodySmall)
                            Text("句数: ${stats.sentenceCount}", style = MaterialTheme.typography.bodySmall)
                            Text("字数: ${stats.charCount}", style = MaterialTheme.typography.bodySmall)
                            Text("不同词: ${stats.uniqueWordCount}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                ArticleReaderContent(
                    article = article!!,
                    sentences = uiState.sentences ?: emptyList(),
                    wordLinks = uiState.wordLinks ?: emptyList(),
                    sentenceAnalysis = uiState.sentenceAnalysis,
                    isAnalyzing = uiState.isAnalyzing,
                    onAnalyzeSentence = { sentenceId, text ->
                        selectedSentenceId = sentenceId
                        selectedSentenceText = text
                        showSentenceAnalysis = true
                        viewModel.analyzeSentence(sentenceId, text)
                    },
                    onWordClick = onWordClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    listState = listState
                )
            }
        }
    }

    // Sentence Analysis Sheet
    if (showSentenceAnalysis) {
        ModalBottomSheet(
            onDismissRequest = { showSentenceAnalysis = false },
            sheetState = analysisSheetState
        ) {
            SentenceAnalysisSheet(
                sentenceText = selectedSentenceText,
                analysis = uiState.sentenceAnalysis[selectedSentenceId],
                isLoading = uiState.isAnalyzing == selectedSentenceId,
                error = uiState.analyzeError,
                onDismiss = { showSentenceAnalysis = false }
            )
        }
    }
}

@Composable
private fun ArticleReaderContent(
    article: Article,
    sentences: List<ArticleSentence>,
    wordLinks: List<ArticleWordLink>,
    sentenceAnalysis: Map<Long, SentenceAnalysisResult>,
    isAnalyzing: Long,
    onAnalyzeSentence: (Long, String) -> Unit,
    onWordClick: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState
) {
    // Build word link map: sentenceId -> List<ArticleWordLink>
    val wordLinksBySentence = wordLinks.groupBy { it.sentenceId }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(article.title, style = MaterialTheme.typography.headlineSmall)
            if (article.domain.isNotBlank()) {
                Text(article.domain, style = MaterialTheme.typography.labelSmall)
            }
        }

        items(sentences) { sentence ->
            SentenceRow(
                sentenceId = sentence.id,
                sentenceText = sentence.text,
                wordLinks = wordLinksBySentence[sentence.id] ?: emptyList(),
                isAnalyzing = isAnalyzing == sentence.id,
                analysis = sentenceAnalysis[sentence.id],
                onAnalyze = { onAnalyzeSentence(sentence.id, sentence.text) },
                onWordClick = onWordClick
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentenceRow(
    sentenceId: Long,
    sentenceText: String,
    wordLinks: List<ArticleWordLink>,
    isAnalyzing: Boolean,
    analysis: SentenceAnalysisResult?,
    onAnalyze: () -> Unit,
    onWordClick: (Long, Long) -> Unit
) {
    // Build list of (text, wordLink) for rendering - split sentence into highlighted and non-highlighted parts
    val parts = mutableListOf<Pair<String, ArticleWordLink?>>()
    var lastEnd = 0

    wordLinks.forEach { link ->
        val lowerText = sentenceText.lowercase()
        val matchedToken = link.matchedToken.lowercase()
        val startPos = lowerText.indexOf(matchedToken, startIndex = lastEnd)
        if (startPos >= 0 && startPos < sentenceText.length) {
            val endPos = minOf(startPos + matchedToken.length, sentenceText.length)
            // Add non-highlighted text before word
            if (lastEnd < startPos) {
                parts.add(sentenceText.substring(lastEnd, startPos) to null)
            }
            // Add highlighted word
            parts.add(sentenceText.substring(startPos, endPos) to link)
            lastEnd = endPos
        }
    }
    // Add remaining text
    if (lastEnd < sentenceText.length) {
        parts.add(sentenceText.substring(lastEnd) to null)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Render highlighted text with tap and long-press gestures
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        Text(
            buildAnnotatedString {
                parts.forEach { (text, link) ->
                    if (link != null) {
                        pushStringAnnotation(tag = "word", annotation = "${link.wordId}:${link.dictionaryId}")
                        withStyle(
                            style = SpanStyle(
                                background = MaterialTheme.colorScheme.primaryContainer,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            append(text)
                        }
                        pop()
                    } else {
                        append(text)
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(wordLinks) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            textLayoutResult?.let { layout ->
                                val charOffset = layout.getOffsetForPosition(tapOffset)
                                buildAnnotatedString {
                                    parts.forEach { (text, link) ->
                                        if (link != null) {
                                            pushStringAnnotation(tag = "word", annotation = "${link.wordId}:${link.dictionaryId}")
                                            append(text)
                                            pop()
                                        } else {
                                            append(text)
                                        }
                                    }
                                }.getStringAnnotations("word", charOffset, charOffset).firstOrNull()?.let { ann ->
                                    val (wId, dId) = ann.item.split(":")
                                    onWordClick(wId.toLong(), dId.toLong())
                                }
                            }
                        },
                        onLongPress = { onAnalyze() }
                    )
                }
        )

        if (isAnalyzing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
                Text("分析中…", style = MaterialTheme.typography.labelSmall)
            }
        } else if (analysis != null) {
            Text(analysis.meaningZh, style = MaterialTheme.typography.bodySmall)
            if (analysis.grammarPoints.isNotEmpty()) {
                Text(
                    "语法: ${analysis.grammarPoints.take(2).joinToString("; ") { it.title }}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
