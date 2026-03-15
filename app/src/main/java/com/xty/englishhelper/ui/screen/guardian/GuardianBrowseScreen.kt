package com.xty.englishhelper.ui.screen.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xty.englishhelper.domain.model.OnlineReadingSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianBrowseScreen(
    onBack: () -> Unit,
    onArticleClick: (articleId: Long) -> Unit,
    viewModel: GuardianBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("在线阅读") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SourceChips(
                sources = uiState.sources,
                selectedSource = uiState.selectedSource,
                onSourceSelected = viewModel::selectSource
            )

            // Section chips
            SectionChips(
                sections = uiState.sections,
                selectedSection = uiState.selectedSection,
                onSectionSelected = { viewModel.loadSection(it) }
            )

            HorizontalDivider()

            // Content area
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.articles.isEmpty() && uiState.error == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("暂无文章", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.refresh() }) {
                                Text("重试")
                            }
                        }
                    }
                    uiState.error != null && uiState.articles.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "加载失败",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.refresh() }) {
                                Text("重试")
                            }
                        }
                    }
                    else -> {
                        ArticleList(
                            articles = uiState.articles,
                            isLoadingArticle = uiState.isLoadingArticle,
                            onArticleClick = { url ->
                                viewModel.openArticle(url, onArticleClick)
                            }
                        )
                    }
                }

                // Overlay loading indicator when opening an article
                if (uiState.isLoadingArticle) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text("正在加载文章…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChips(
    sources: List<OnlineReadingSource>,
    selectedSource: OnlineReadingSource,
    onSourceSelected: (OnlineReadingSource) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(sources, key = { it.key }) { source ->
            FilterChip(
                selected = source == selectedSource,
                onClick = { onSourceSelected(source) },
                label = { Text(source.label) }
            )
        }
    }
}

@Composable
private fun SectionChips(
    sections: List<GuardianSection>,
    selectedSection: String,
    onSectionSelected: (String) -> Unit
) {
    val grouped = remember(sections) { sections.groupBy { it.group } }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        grouped.forEach { (group, items) ->
            if (group.isNotBlank()) {
                item(key = "group_$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 2.dp, top = 8.dp)
                    )
                }
            }
            items(items, key = { it.key }) { section ->
                FilterChip(
                    selected = section.key == selectedSection,
                    onClick = { onSectionSelected(section.key) },
                    label = { Text(section.label) }
                )
            }
        }
    }
}

@Composable
private fun ArticleList(
    articles: List<GuardianBrowseItem>,
    isLoadingArticle: Boolean,
    onArticleClick: (url: String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(articles, key = { it.url }) { article ->
            ArticlePreviewCard(
                article = article,
                onClick = {
                    if (!isLoadingArticle) {
                        onArticleClick(article.url)
                    }
                }
            )
        }

        item {
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ArticlePreviewCard(
    article: GuardianBrowseItem,
    onClick: () -> Unit
) {
    val imageUrl = article.coverImageUrl ?: article.thumbnailUrl
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1F1F1F))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (!article.trailText.isNullOrBlank()) {
                    Text(
                        article.trailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val authorText = when {
                    article.isAuthorLoading -> "作者：加载中…"
                    !article.author.isNullOrBlank() -> "作者：${article.author}"
                    else -> "作者：未知"
                }
                val wordCountText = when {
                    article.isWordCountLoading -> "字数：加载中…"
                    article.wordCount != null && article.wordCount > 0 -> "字数：${article.wordCount}"
                    else -> "字数：未知"
                }
                val metaColor = if (article.isAuthorLoading || article.isWordCountLoading) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    authorText,
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor
                )
                Text(
                    wordCountText,
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor
                )
            }
        }
    }
}
