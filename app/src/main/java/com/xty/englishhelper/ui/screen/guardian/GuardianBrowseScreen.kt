package com.xty.englishhelper.ui.screen.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import com.xty.englishhelper.ui.screen.article.ArticleFilterActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianBrowseScreen(
    onBack: () -> Unit,
    onArticleClick: (articleId: Long) -> Unit,
    viewModel: GuardianBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val visibleEvaluatingCount = uiState.articles.count { it.isEvaluating }

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
                },
                actions = {
                    ArticleFilterActionButton(
                        filterEnabled = uiState.filterEnabled,
                        lengthFilter = uiState.lengthFilter,
                        scoreFilter = uiState.scoreFilter,
                        sortOption = uiState.sortOption,
                        onFilterEnabledChange = viewModel::setFilterEnabled,
                        onLengthFilterChange = viewModel::setLengthFilter,
                        onScoreFilterChange = viewModel::setScoreFilter,
                        onSortOptionChange = viewModel::setSortOption,
                        onReset = viewModel::resetFilters,
                        helperText = "自动评分只会处理当前筛选后仍然可见的文章。"
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        EhMaxWidthContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            maxWidth = 980.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OnlineBrowseControlPanel(
                    sources = uiState.sources,
                    selectedSource = uiState.selectedSource,
                    sections = uiState.sections,
                    selectedSection = uiState.selectedSection,
                    filterEnabled = uiState.filterEnabled,
                    visibleEvaluatingCount = visibleEvaluatingCount,
                    totalVisibleCount = uiState.articles.size,
                    onSourceSelected = viewModel::selectSource,
                    onSectionSelected = { viewModel.loadSection(it) }
                )

                Spacer(Modifier.height(12.dp))

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        uiState.articles.isEmpty() && uiState.error == null -> {
                            EmptyOnlineArticleState(
                                modifier = Modifier.align(Alignment.Center),
                                hasSourceArticles = uiState.allArticles.isNotEmpty(),
                                onRetry = { viewModel.refresh() }
                            )
                        }
                        uiState.error != null && uiState.articles.isEmpty() -> {
                            EmptyOnlineArticleState(
                                modifier = Modifier.align(Alignment.Center),
                                hasSourceArticles = false,
                                title = "加载失败",
                                message = "当前来源或栏目暂时无法获取文章，请重试。",
                                onRetry = { viewModel.refresh() },
                                error = true
                            )
                        }
                        else -> {
                            ArticleList(
                                articles = uiState.articles,
                                isLoadingArticle = uiState.isLoadingArticle,
                                onArticleClick = { url ->
                                    viewModel.openArticle(url, onArticleClick)
                                },
                                onReevaluate = viewModel::reEvaluate
                            )
                        }
                    }

                    if (uiState.isLoadingArticle) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineBrowseControlPanel(
    sources: List<OnlineReadingSource>,
    selectedSource: OnlineReadingSource,
    sections: List<GuardianSection>,
    selectedSection: String,
    filterEnabled: Boolean,
    visibleEvaluatingCount: Int,
    totalVisibleCount: Int,
    onSourceSelected: (OnlineReadingSource) -> Unit,
    onSectionSelected: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("来源与栏目", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (filterEnabled) {
                        "当前只展示并自动评估通过筛选的在线文章。"
                    } else {
                        "切换来源或栏目后，会自动评估当前可见文章。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources, key = { it.key }) { source ->
                    FilterChip(
                        selected = source == selectedSource,
                        onClick = { onSourceSelected(source) },
                        label = { Text(source.label) }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "栏目",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SectionChips(
                    sections = sections,
                    selectedSection = selectedSection,
                    onSectionSelected = onSectionSelected
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrowseInfoPill(
                    text = selectedSource.label,
                    emphasized = true
                )
                if (visibleEvaluatingCount > 0) {
                    BrowseInfoPill(
                        text = "评估中 $visibleEvaluatingCount/$totalVisibleCount",
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else if (totalVisibleCount > 0) {
                    BrowseInfoPill(text = "当前列表 $totalVisibleCount 篇")
                }
            }
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

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        grouped.forEach { (group, items) ->
            if (group.isNotBlank()) {
                item(key = "group_$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, end = 2.dp)
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
private fun EmptyOnlineArticleState(
    modifier: Modifier = Modifier,
    hasSourceArticles: Boolean,
    onRetry: () -> Unit,
    title: String = if (hasSourceArticles) "没有符合当前条件的文章" else "暂无文章",
    message: String = if (hasSourceArticles) {
        "可以暂停筛选、切换栏目，或等待更多详情加载后再试。"
    } else {
        "当前来源暂时没有可用文章。"
    },
    error: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun ArticleList(
    articles: List<GuardianBrowseItem>,
    isLoadingArticle: Boolean,
    onArticleClick: (url: String) -> Unit,
    onReevaluate: (url: String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(articles, key = { it.url }) { article ->
            ArticlePreviewCard(
                article = article,
                onClick = {
                    if (!isLoadingArticle) {
                        onArticleClick(article.url)
                    }
                },
                onReevaluate = { onReevaluate(article.url) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticlePreviewCard(
    article: GuardianBrowseItem,
    onClick: () -> Unit,
    onReevaluate: () -> Unit
) {
    val imageUrl = article.coverImageUrl ?: article.thumbnailUrl
    val context = LocalContext.current
    val scoreText = when {
        article.isEvaluating -> "评估中…"
        article.suitabilityScore != null -> "评分 ${article.suitabilityScore}"
        else -> "未评估"
    }
    val scoreColor = when {
        article.isEvaluating -> MaterialTheme.colorScheme.primary
        article.suitabilityScore != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
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
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF25364C), Color(0xFF506680))
                                )
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White,
                            shape = CircleShape
                        ) {
                            Text(
                                text = (article.title.firstOrNull()?.uppercase() ?: "A"),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleMedium,
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

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrowseInfoPill(
                        text = when {
                            article.isAuthorLoading -> "作者加载中"
                            !article.author.isNullOrBlank() -> article.author
                            else -> "作者未知"
                        }
                    )
                    BrowseInfoPill(
                        text = when {
                            article.isWordCountLoading -> "字数加载中"
                            article.wordCount != null && article.wordCount > 0 -> "${article.wordCount} 词"
                            else -> "字数未知"
                        }
                    )
                    BrowseInfoPill(
                        text = scoreText,
                        containerColor = scoreColor.copy(alpha = 0.12f),
                        contentColor = scoreColor
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!article.detailError.isNullOrBlank()) {
                        Text(
                            text = "详情加载不完整",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    TextButton(onClick = onReevaluate, enabled = !article.isEvaluating) {
                        Text("重新评估")
                    }
                }

                if (!article.suitabilityReason.isNullOrBlank()) {
                    Text(
                        article.suitabilityReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseInfoPill(
    text: String,
    emphasized: Boolean = false,
    containerColor: Color = if (emphasized) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    },
    contentColor: Color = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
