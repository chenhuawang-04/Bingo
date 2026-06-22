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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.OnlineReadingSource
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.screen.article.ArticleFilterActionButton
import com.xty.englishhelper.ui.screen.article.EditorialActionButton
import com.xty.englishhelper.ui.screen.article.EditorialPill
import com.xty.englishhelper.ui.screen.article.EditorialThumbnail
import com.xty.englishhelper.ui.components.article.UnifiedArticleCard

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
        stringResource(R.string.guardian_featured_home)
    } else {
        uiState.sections.firstOrNull { it.key == uiState.selectedSection }?.label ?: stringResource(R.string.guardian_home)
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
                title = { Text(stringResource(R.string.article_online_reading)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                        helperText = stringResource(R.string.guardian_filter_hint)
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
                                title = stringResource(R.string.guardian_load_failed),
                                message = stringResource(R.string.guardian_load_failed_msg),
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
                                    Text(stringResource(R.string.guardian_opening_article), style = MaterialTheme.typography.bodyMedium)
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isHomePage) stringResource(R.string.guardian_homepage_recommend) else selectedSource.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sectionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.common_refresh), style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onOpenScopeSheet) {
                    Text(stringResource(R.string.guardian_switch_column), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (visibleEvaluatingCount > 0) {
                Text(
                    text = stringResource(R.string.guardian_evaluating_count, visibleEvaluatingCount, totalVisibleCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = stringResource(R.string.guardian_article_count, totalVisibleCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (filterEnabled) {
                Text(
                    text = stringResource(R.string.guardian_filter_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                text = stringResource(R.string.guardian_scope_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.guardian_scope_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.guardian_article_source),
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
                    text = stringResource(R.string.guardian_column_scope),
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
        OnlineReadingSource.GUARDIAN -> stringResource(R.string.guardian_desc_guardian)
        OnlineReadingSource.CSMONITOR -> stringResource(R.string.guardian_desc_csmonitor)
        OnlineReadingSource.ATLANTIC -> stringResource(R.string.guardian_desc_atlantic)
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
    title: String = if (hasSourceArticles) stringResource(R.string.guardian_empty_has_source) else stringResource(R.string.guardian_empty_no_source),
    message: String = if (hasSourceArticles) {
        stringResource(R.string.guardian_empty_has_source_msg)
    } else {
        stringResource(R.string.guardian_empty_no_source_msg)
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
                    text = if (error) stringResource(R.string.guardian_error_pill) else stringResource(R.string.guardian_empty_pill),
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
                            text = stringResource(R.string.guardian_adjust_scope),
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
                            text = stringResource(R.string.guardian_retry),
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
            val sourceLine = buildList {
                add(article.source?.label ?: "Online")
                article.sectionLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (!article.author.isNullOrBlank()) add(article.author)
            }.joinToString(" · ")

            UnifiedArticleCard(
                title = article.title,
                sourceLine = sourceLine,
                snippet = article.trailText.orEmpty(),
                coverModel = article.coverImageUrl ?: article.thumbnailUrl,
                placeholderSeed = article.title.firstOrNull()?.uppercase() ?: "A",
                wordCount = article.wordCount,
                scoreText = when {
                    article.isEvaluating -> stringResource(R.string.article_evaluating)
                    article.suitabilityScore != null -> stringResource(R.string.article_score_format, article.suitabilityScore)
                    else -> stringResource(R.string.article_no_score)
                },
                suitabilityReason = article.suitabilityReason?.takeIf { it.isNotBlank() },
                categoryName = null,
                isEvaluating = article.isEvaluating,
                onRead = {
                    if (!isLoadingArticle) {
                        onArticleClick(article)
                    }
                },
                onReevaluate = { onReevaluate(article.url) },
                onDelete = null,
                onMoveCategory = null,
                categories = null,
                categoryId = null
            )
        }
    }
}
