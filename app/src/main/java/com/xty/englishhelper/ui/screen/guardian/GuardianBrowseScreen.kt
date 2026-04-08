package com.xty.englishhelper.ui.screen.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.OnlineReadingSource
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.screen.article.ArticleFilterActionButton
import com.xty.englishhelper.ui.screen.article.EditorialActionButton
import com.xty.englishhelper.ui.screen.article.EditorialPill
import com.xty.englishhelper.ui.screen.article.EditorialThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianBrowseScreen(
    onBack: () -> Unit,
    onArticleClick: (articleId: Long) -> Unit,
    viewModel: GuardianBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val displayArticles = if (uiState.isHomePage) uiState.topArticles else uiState.articles
    val visibleEvaluatingCount = displayArticles.count { it.isEvaluating }
    val currentSectionLabel = if (uiState.isHomePage) {
        "精选首页"
    } else {
        uiState.sections.firstOrNull { it.key == uiState.selectedSection }?.label ?: "首页"
    }
    var showScopeSheet by rememberSaveable { mutableStateOf(false) }

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
                        helperText = "自动评估只会作用于当前已筛出的文章；已评过的文章不会重复评估。"
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OnlineBrowseOverviewCard(
                    selectedSource = uiState.selectedSource,
                    sectionLabel = currentSectionLabel,
                    isHomePage = uiState.isHomePage,
                    filterEnabled = uiState.filterEnabled,
                    visibleEvaluatingCount = visibleEvaluatingCount,
                    totalVisibleCount = displayArticles.size,
                    onOpenScopeSheet = { showScopeSheet = true },
                    onRefresh = { viewModel.refresh() }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        (!uiState.isHomePage && uiState.isLoading) || (uiState.isHomePage && uiState.isHomeLoading) -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        displayArticles.isEmpty() && uiState.error == null -> {
                            EmptyOnlineArticleState(
                                modifier = Modifier.align(Alignment.Center),
                                hasSourceArticles = if (uiState.isHomePage) {
                                    uiState.topArticles.isNotEmpty()
                                } else {
                                    uiState.allArticles.isNotEmpty()
                                },
                                onAdjustScope = { showScopeSheet = true },
                                onRetry = { viewModel.refresh() }
                            )
                        }
                        uiState.error != null && displayArticles.isEmpty() -> {
                            EmptyOnlineArticleState(
                                modifier = Modifier.align(Alignment.Center),
                                hasSourceArticles = false,
                                title = "加载失败",
                                message = "当前栏目暂时无法读取文章，可以刷新或切换其他来源与栏目。",
                                onAdjustScope = { showScopeSheet = true },
                                onRetry = { viewModel.refresh() },
                                error = true
                            )
                        }
                        else -> {
                            ArticleList(
                                articles = displayArticles,
                                isLoadingArticle = uiState.isLoadingArticle,
                                onArticleClick = { article ->
                                    viewModel.openArticle(article, onArticleClick)
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
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                ),
                                elevation = CardDefaults.cardElevation(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text("正在打开文章", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScopeSheet) {
        OnlineScopeSheet(
            sources = uiState.sources,
            selectedSource = uiState.selectedSource,
            sections = uiState.sections,
            selectedSection = uiState.selectedSection,
            onSourceSelected = viewModel::selectSource,
            onSectionSelected = {
                viewModel.loadSection(it)
                showScopeSheet = false
            },
            onDismiss = { showScopeSheet = false }
        )
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineBrowseOverviewCard(
    selectedSource: OnlineReadingSource,
    sectionLabel: String,
    isHomePage: Boolean,
    filterEnabled: Boolean,
    visibleEvaluatingCount: Int,
    totalVisibleCount: Int,
    onOpenScopeSheet: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        shape = ArticleShapes.Section,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isHomePage) "首页推荐" else "在线题源",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = sectionLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isHomePage) {
                            "展示三个来源中评分最高的前 5 篇文章。进入“更改范围”可切换到任一来源栏目。"
                        } else if (filterEnabled) {
                            "当前列表只保留符合筛选条件的文章，自动评估也会限定在这一批内容里。"
                        } else {
                            "先收窄来源与栏目，再决定是否用筛选与评分排序继续精修题源。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EditorialPill(
                        text = if (isHomePage) "跨来源 Top 5" else selectedSource.label,
                        emphasized = true
                    )
                    if (visibleEvaluatingCount > 0) {
                        EditorialPill(
                            text = "评估中 $visibleEvaluatingCount/$totalVisibleCount",
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        EditorialPill(text = "当前 $totalVisibleCount 篇")
                    }
                    if (filterEnabled) {
                        EditorialPill(text = "筛选开启")
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackedLayout = maxWidth < 560.dp
                    if (stackedLayout) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "当前栏目",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${selectedSource.label} · $sectionLabel",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EditorialActionButton(
                                    text = "更改范围",
                                    icon = Icons.Default.Edit,
                                    onClick = onOpenScopeSheet
                                )
                                if (!isHomePage) {
                                    EditorialActionButton(
                                        text = "刷新",
                                        icon = Icons.Default.Refresh,
                                        onClick = onRefresh,
                                        prominent = true
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "当前栏目",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${selectedSource.label} · $sectionLabel",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EditorialActionButton(
                                    text = "更改范围",
                                    icon = Icons.Default.Edit,
                                    onClick = onOpenScopeSheet
                                )
                                if (!isHomePage) {
                                    EditorialActionButton(
                                        text = "刷新",
                                        icon = Icons.Default.Refresh,
                                        onClick = onRefresh,
                                        prominent = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OnlineScopeSheet(
    sources: List<OnlineReadingSource>,
    selectedSource: OnlineReadingSource,
    sections: List<GuardianSection>,
    selectedSection: String,
    onSourceSelected: (OnlineReadingSource) -> Unit,
    onSectionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "选择文章来源与栏目",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "把来源和栏目都收进这里，主列表只保留真正需要阅读的内容。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "文章来源",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                sources.forEach { source ->
                    ScopeSourceCard(
                        source = source,
                        selected = source == selectedSource,
                        onClick = { onSourceSelected(source) }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "栏目范围",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                sections.groupBy { it.group }.forEach { (group, items) ->
                    ScopeSectionGroup(
                        group = group.ifBlank { "Main" },
                        sections = items,
                        selectedSection = selectedSection,
                        onSectionSelected = onSectionSelected
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
@Composable
private fun ScopeSourceCard(
    source: OnlineReadingSource,
    selected: Boolean,
    onClick: () -> Unit
) {
    val description = when (source) {
        OnlineReadingSource.GUARDIAN -> "新闻覆盖广，适合筛选高频考研题源。"
        OnlineReadingSource.CSMONITOR -> "分析性更强，议题集中，适合练习深度阅读。"
        OnlineReadingSource.ATLANTIC -> "篇幅更长，评论性更强，适合高阶素材积累。"
    }

    Surface(
        shape = ArticleShapes.Section,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = source.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopeSectionGroup(
    group: String,
    sections: List<GuardianSection>,
    selectedSection: String,
    onSectionSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = group,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.forEach { section ->
                Surface(
                    shape = ArticleShapes.Chip,
                    color = if (section.key == selectedSection) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (section.key == selectedSection) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Text(
                        text = section.label,
                        modifier = Modifier
                            .clickable { onSectionSelected(section.key) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyOnlineArticleState(
    modifier: Modifier = Modifier,
    hasSourceArticles: Boolean,
    onAdjustScope: () -> Unit,
    onRetry: () -> Unit,
    title: String = if (hasSourceArticles) "当前范围没有文章" else "还没有抓到文章",
    message: String = if (hasSourceArticles) {
        "可以先放宽来源与栏目，或关闭筛选条件，再决定要保留哪一批文章。"
    } else {
        "尝试切换到其他来源或栏目，系统会在进入列表后自动评估当前可见文章。"
    },
    error: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ArticleShapes.Section,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.46f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
                        )
                    )
                )
                .padding(horizontal = 22.dp, vertical = 26.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorialPill(
                    text = if (error) "读取异常" else "列表为空",
                    emphasized = true
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = ArticleShapes.Control,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = "调整范围",
                            modifier = Modifier
                                .clickable(onClick = onAdjustScope)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Surface(
                        shape = ArticleShapes.Control,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Text(
                            text = "重试",
                            modifier = Modifier
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun ArticleList(
    articles: List<GuardianBrowseItem>,
    isLoadingArticle: Boolean,
    onArticleClick: (article: GuardianBrowseItem) -> Unit,
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
                        onArticleClick(article)
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
    val scoreText = when {
        article.isEvaluating -> "评估中"
        article.suitabilityScore != null -> "评分 ${article.suitabilityScore}"
        else -> "未评分"
    }
    val scoreColor = when {
        article.isEvaluating -> MaterialTheme.colorScheme.primary
        article.suitabilityScore != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val supportingLine = buildList {
        add(article.source?.label ?: "Online")
        article.sectionLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (!article.author.isNullOrBlank()) add(article.author)
    }.joinToString(" · ")
    val placeholderSeed = article.title.firstOrNull()?.uppercase() ?: "A"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = ArticleShapes.Section,
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            EditorialThumbnail(
                imageModel = imageUrl,
                fallbackSeed = placeholderSeed,
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(0.76f)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (supportingLine.isNotBlank()) {
                    Text(
                        text = supportingLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!article.trailText.isNullOrBlank()) {
                    Text(
                        text = article.trailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EditorialPill(
                        text = when {
                            article.isWordCountLoading -> "统计词数中"
                            article.wordCount != null && article.wordCount > 0 -> "${article.wordCount} 词"
                            else -> "词数未知"
                        }
                    )
                    EditorialPill(
                        text = scoreText,
                        containerColor = scoreColor.copy(alpha = 0.12f),
                        contentColor = scoreColor
                    )
                    if (!article.detailError.isNullOrBlank()) {
                        EditorialPill(
                            text = "详情待补全",
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (!article.suitabilityReason.isNullOrBlank()) {
                    Text(
                        text = article.suitabilityReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!article.detailError.isNullOrBlank()) {
                        Text(
                            text = article.detailError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    EditorialActionButton(
                        text = "重新评估",
                        icon = Icons.Default.Refresh,
                        onClick = onReevaluate,
                        enabled = !article.isEvaluating
                    )
                }
            }
        }
    }
}
