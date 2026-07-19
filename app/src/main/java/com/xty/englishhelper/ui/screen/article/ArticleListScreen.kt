package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.components.article.UnifiedArticleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onCreateArticle: () -> Unit,
    onReadArticle: (Long) -> Unit,
    onSettings: () -> Unit,
    onGuardianBrowse: () -> Unit = {},
    onScanDetail: () -> Unit = {},
    onAutoPaper: () -> Unit = {},
    viewModel: ArticleListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val articles = uiState.articles
    val snackbarHostState = remember { SnackbarHostState() }
    val hasFilterConfig = hasArticleFilterConfig(
        lengthFilter = uiState.lengthFilter,
        scoreFilter = uiState.scoreFilter,
        sortOption = uiState.sortOption
    )
    var showCreateCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var newCategoryName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    AppTopBarEffect(
        title = { Text(stringResource(R.string.article_reading)) },
        actions = {
            IconButton(onClick = onAutoPaper) {
                Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.auto_paper_title))
            }
            IconButton(onClick = onGuardianBrowse) {
                Icon(Icons.Default.Language, contentDescription = stringResource(R.string.article_online_reading))
            }
            ArticleFilterActionButton(
                filterEnabled = uiState.filterEnabled,
                lengthFilter = uiState.lengthFilter,
                scoreFilter = uiState.scoreFilter,
                sortOption = uiState.sortOption,
                onFilterEnabledChange = viewModel::setFilterEnabled,
                onLengthFilterChange = viewModel::setLengthFilter,
                onScoreFilterChange = viewModel::setScoreFilter,
                onSortOptionChange = viewModel::setSortOption,
                onReset = viewModel::resetFilters
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.common_settings))
            }
        }
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateArticle) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.article_create))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        EhMaxWidthContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            maxWidth = 920.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryTabRow(
                    categories = uiState.categories,
                    selectedCategoryId = uiState.selectedCategoryId,
                    onSelectCategory = viewModel::selectCategory,
                    onCreateCategory = { showCreateCategoryDialog = true }
                )

                FilterRow(
                    filterEnabled = uiState.filterEnabled,
                    hasFilterConfig = hasFilterConfig,
                    lengthFilter = uiState.lengthFilter,
                    scoreFilter = uiState.scoreFilter,
                    sortOption = uiState.sortOption,
                    onLengthFilterChange = viewModel::setLengthFilter,
                    onScoreFilterChange = viewModel::setScoreFilter,
                    onSortOptionChange = viewModel::setSortOption,
                    onFilterEnabledChange = viewModel::setFilterEnabled,
                    onReset = viewModel::resetFilters
                )

                ScanProgressRow(
                    scanTask = uiState.scanTask,
                    onClick = onScanDetail
                )

                if (articles.isEmpty()) {
                    EmptyArticleState(
                        hasSourceArticles = uiState.allArticles.isNotEmpty(),
                        filterEnabled = uiState.filterEnabled,
                        onCreateArticle = onCreateArticle,
                        onResetFilters = viewModel::resetFilters
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(articles, key = { it.id }) { article ->
                            val categoryName = uiState.categories.firstOrNull { it.id == article.categoryId }?.name
                            val isEvaluating = uiState.evaluatingIds.contains(article.id)

                            UnifiedArticleCard(
                                title = article.title,
                                sourceLine = buildList {
                                    if (article.author.isNotBlank()) add(article.author)
                                    if (article.source.isNotBlank()) add(article.source)
                                }.joinToString(" · "),
                                snippet = buildArticleSnippet(article.summary, article.content),
                                coverModel = article.coverImageUri ?: article.coverImageUrl,
                                placeholderSeed = article.title.firstOrNull()?.uppercase() ?: "A",
                                wordCount = article.wordCount.takeIf { it > 0 },
                                scoreText = when {
                                    isEvaluating -> stringResource(R.string.article_evaluating)
                                    article.suitabilityScore != null -> stringResource(R.string.article_score_format, article.suitabilityScore)
                                    else -> stringResource(R.string.article_no_score)
                                },
                                suitabilityReason = article.suitabilityReason.takeIf { it.isNotBlank() },
                                categoryName = categoryName,
                                isEvaluating = isEvaluating,
                                onRead = { onReadArticle(article.id) },
                                onReevaluate = { viewModel.reEvaluateArticle(article.id) },
                                onDelete = { viewModel.deleteArticle(article.id) },
                                onMoveCategory = { targetCategoryId ->
                                    viewModel.moveArticleToCategory(article.id, targetCategoryId)
                                },
                                categories = uiState.categories,
                                categoryId = article.categoryId,
                                advancedScores = uiState.advancedScoresByArticle[article.id].orEmpty(),
                                onAddAdvancedScoreToPaper = { score ->
                                    viewModel.addAdvancedScoreToTodayPaper(article.id, score)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateCategoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateCategoryDialog = false
                newCategoryName = ""
            },
            title = { Text(stringResource(R.string.article_new_category)) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text(stringResource(R.string.article_category_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createCategory(newCategoryName)
                        showCreateCategoryDialog = false
                        newCategoryName = ""
                    }
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateCategoryDialog = false
                    newCategoryName = ""
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun CategoryTabRow(
    categories: List<ArticleCategory>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    onCreateCategory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CategoryTab(
                text = stringResource(R.string.article_all),
                selected = selectedCategoryId == null,
                onClick = { onSelectCategory(null) }
            )
            categories.forEach { category ->
                CategoryTab(
                    text = category.name,
                    selected = selectedCategoryId == category.id,
                    onClick = { onSelectCategory(category.id) }
                )
            }
            TextButton(onClick = onCreateCategory) {
                Text(stringResource(R.string.article_new_tab), style = MaterialTheme.typography.labelMedium)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CategoryTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = if (selected) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (selected) {
            HorizontalDivider(
                modifier = Modifier.width(24.dp),
                color = MaterialTheme.colorScheme.secondary,
                thickness = 2.dp
            )
        }
    }
}

@Composable
private fun FilterRow(
    filterEnabled: Boolean,
    hasFilterConfig: Boolean,
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption,
    onLengthFilterChange: (ArticleLengthFilter) -> Unit,
    onScoreFilterChange: (ArticleScoreFilter) -> Unit,
    onSortOptionChange: (ArticleSortOption) -> Unit,
    onFilterEnabledChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    var showLengthMenu by remember { mutableStateOf(false) }
    var showScoreMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Text(
                text = stringResource(R.string.article_filter_with_arrow),
                style = MaterialTheme.typography.labelMedium,
                color = if (scoreFilter != ArticleScoreFilter.ALL) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.clickable { showScoreMenu = true }
            )
            DropdownMenu(expanded = showScoreMenu, onDismissRequest = { showScoreMenu = false }) {
                ArticleScoreFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.label) },
                        onClick = {
                            onScoreFilterChange(filter)
                            showScoreMenu = false
                        }
                    )
                }
            }
        }

        Box {
            Text(
                text = stringResource(R.string.article_length_with_arrow),
                style = MaterialTheme.typography.labelMedium,
                color = if (lengthFilter != ArticleLengthFilter.ALL) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.clickable { showLengthMenu = true }
            )
            DropdownMenu(expanded = showLengthMenu, onDismissRequest = { showLengthMenu = false }) {
                ArticleLengthFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.label) },
                        onClick = {
                            onLengthFilterChange(filter)
                            showLengthMenu = false
                        }
                    )
                }
            }
        }

        Box {
            Text(
                text = stringResource(R.string.article_sort_with_arrow),
                style = MaterialTheme.typography.labelMedium,
                color = if (sortOption != ArticleSortOption.DEFAULT) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.clickable { showSortMenu = true }
            )
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                ArticleSortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSortOptionChange(option)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (hasFilterConfig) {
            Text(
                text = stringResource(R.string.article_reset_filter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable {
                    onReset()
                    onFilterEnabledChange(false)
                }
            )
        }
    }
}

@Composable
private fun ScanProgressRow(
    scanTask: com.xty.englishhelper.domain.model.BackgroundTask?,
    onClick: () -> Unit
) {
    val status = scanTask?.status
    val isActive = status == BackgroundTaskStatus.PENDING || status == BackgroundTaskStatus.RUNNING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive) {
            // isActive 为 true 时 scanTask 必不为 null（因为 status 来自 scanTask?.status）
            scanTask!!.let { task ->
                LinearProgressIndicator(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp),
                    progress = {
                        if (task.progressTotal > 0) {
                            task.progressCurrent.toFloat() / task.progressTotal
                        } else 0f
                    },
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        Text(
            text = if (scanTask != null) {
                stringResource(R.string.article_scan_progress, scanTask.progressCurrent, scanTask.progressTotal)
            } else stringResource(R.string.article_online_scan),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyArticleState(
    hasSourceArticles: Boolean,
    filterEnabled: Boolean,
    onCreateArticle: () -> Unit,
    onResetFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = when {
                hasSourceArticles && filterEnabled -> stringResource(R.string.article_empty_filtered)
                hasSourceArticles -> stringResource(R.string.article_empty_category)
                else -> stringResource(R.string.article_empty)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!hasSourceArticles) {
                TextButton(onClick = onCreateArticle) {
                    Text(stringResource(R.string.article_create))
                }
            }
            if (hasSourceArticles && filterEnabled) {
                TextButton(onClick = onResetFilters) {
                    Text(stringResource(R.string.article_reset_filter_selection))
                }
            }
        }
    }
}


private fun buildArticleSnippet(summary: String, content: String): String {
    val preferred = summary.takeIf { it.isNotBlank() } ?: content
    return preferred
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(100)
}
