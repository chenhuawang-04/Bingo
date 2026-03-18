package com.xty.englishhelper.ui.components.reading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import coil.compose.AsyncImage
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.ui.designsystem.tokens.ArticleTypography
import com.xty.englishhelper.ui.screen.article.ParagraphAnalysisCard
import kotlinx.coroutines.launch

@Composable
fun ParagraphBlock(
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
    onCollectWord: (word: String, contextSentence: String) -> Unit,
    showContent: Boolean = true
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
        if (showContent) {
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
        }

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

        if (showContent) {
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
        }

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
fun TtsPlaybackBar(
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
fun TranslationBlock(translation: String) {
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
fun HighlightedParagraphText(
    text: String,
    wordLinkMap: Map<String, List<ArticleWordLink>>,
    onWordClick: (Long, Long) -> Unit,
    onCollectWord: (word: String, contextSentence: String) -> Unit,
    style: androidx.compose.ui.text.TextStyle = ArticleTypography.ReaderBody
) {
    val scope = rememberCoroutineScope()
    val underlineStyle = SpanStyle(
        textDecoration = TextDecoration.Underline,
        color = MaterialTheme.colorScheme.primary
    )
    val flashColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
    val flashAlpha = remember { Animatable(0f) }
    var flashRange by remember(text) { mutableStateOf<IntRange?>(null) }

    val annotatedString = remember(text, wordLinkMap, flashRange, flashAlpha.value, flashColor) {
        buildAnnotatedString {
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
            flashRange?.let { range ->
                val start = range.first.coerceAtLeast(0)
                val endExclusive = (range.last + 1).coerceAtMost(text.length)
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
                                val tappedRange = extractWordRangeAtOffset(text, charOffset)
                                val tappedWord = tappedRange?.let { text.substring(it) }
                                if (tappedWord != null) {
                                    val context = extractContextSentence(text, charOffset)
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

fun extractWordAtOffset(text: String, charOffset: Int): String? {
    val range = extractWordRangeAtOffset(text, charOffset) ?: return null
    return text.substring(range)
}

fun extractWordRangeAtOffset(text: String, charOffset: Int): IntRange? {
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

    while (start <= end && !text[start].isLetter()) start++
    while (end >= start && !text[end].isLetter()) end--
    if (start > end) return null

    val word = text.substring(start, end + 1)
    return if (word.length >= 2 && word.all {
            it in 'A'..'Z' || it in 'a'..'z' || it == '\'' || it == '\u2019' || it == '-'
        }) start..end else null
}

fun extractContextSentence(text: String, charOffset: Int): String {
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
