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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onCreateArticle: () -> Unit,
    onReadArticle: (Long) -> Unit,
    onSettings: () -> Unit,
    onGuardianBrowse: () -> Unit = {},
    onScanDetail: () -> Unit = {},
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文章阅读") },
                actions = {
                    IconButton(onClick = onGuardianBrowse) {
                        Icon(Icons.Default.Language, contentDescription = "在线阅读")
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
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateArticle) {
                Icon(Icons.Default.Add, contentDescription = "新建文章")
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
                            ArticleCard(
                                article = article,
                                categoryName = uiState.categories.firstOrNull { it.id == article.categoryId }?.name,
                                categories = uiState.categories,
                                onRead = { onReadArticle(article.id) },
                                onDelete = { viewModel.deleteArticle(article.id) },
                                onReevaluate = { viewModel.reEvaluateArticle(article.id) },
                                isEvaluating = uiState.evaluatingIds.contains(article.id),
                                onMoveCategory = { categoryId ->
                                    viewModel.moveArticleToCategory(article.id, categoryId)
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
            title = { Text("新建分类") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("分类名称") },
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
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateCategoryDialog = false
                    newCategoryName = ""
                }) {
                    Text("取消")
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
                text = "全部",
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
                Text("+ 新建", style = MaterialTheme.typography.labelMedium)
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
                text = "评分 ${if (scoreFilter != ArticleScoreFilter.ALL) "▾" else "▾"}",
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
                text = "长度 ${if (lengthFilter != ArticleLengthFilter.ALL) "▾" else "▾"}",
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
                text = "排序 ${if (sortOption != ArticleSortOption.DEFAULT) "▾" else "▾"}",
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
                text = "重置",
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
        if (isActive && scanTask != null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp),
                progress = {
                    if (scanTask.progressTotal > 0) {
                        scanTask.progressCurrent.toFloat() / scanTask.progressTotal
                    } else 0f
                },
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        Text(
            text = if (scanTask != null) {
                "${scanTask.progressCurrent}/${scanTask.progressTotal} 在线扫描"
            } else "在线扫描",
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
                hasSourceArticles && filterEnabled -> "当前筛选条件下没有文章"
                hasSourceArticles -> "当前分类下暂时没有文章"
                else -> "还没有文章进入这个分类"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!hasSourceArticles) {
                TextButton(onClick = onCreateArticle) {
                    Text("新建文章")
                }
            }
            if (hasSourceArticles && filterEnabled) {
                TextButton(onClick = onResetFilters) {
                    Text("重置筛选")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleCard(
    article: Article,
    categoryName: String?,
    categories: List<ArticleCategory>,
    onRead: () -> Unit,
    onDelete: () -> Unit,
    onReevaluate: () -> Unit,
    isEvaluating: Boolean,
    onMoveCategory: (Long) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }

    val snippet = remember(article.summary, article.content) {
        buildArticleSnippet(article.summary, article.content)
    }
    val sourceLine = remember(article.author, article.source) {
        buildList {
            if (article.author.isNotBlank()) add(article.author)
            if (article.source.isNotBlank()) add(article.source)
        }.joinToString(" · ")
    }
    val scoreText = when {
        isEvaluating -> "评估中"
        article.suitabilityScore != null -> "${article.suitabilityScore}分"
        else -> "未评分"
    }
    val coverModel = article.coverImageUri ?: article.coverImageUrl
    val placeholderSeed = article.title.firstOrNull()?.uppercase() ?: "A"

    Card(
        onClick = onRead,
        modifier = Modifier.fillMaxWidth(),
        shape = ArticleShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            EditorialThumbnail(
                imageModel = coverModel,
                fallbackSeed = placeholderSeed,
                modifier = Modifier
                    .width(40.dp)
                    .aspectRatio(0.77f)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (snippet.isNotBlank()) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sourceLine.isNotBlank()) {
                        Text(
                            text = sourceLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (article.wordCount > 0) {
                        Text(
                            text = "${article.wordCount}词",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = scoreText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (!categoryName.isNullOrBlank()) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ArticleCardMenu(
                expanded = showMenu,
                onExpand = { showMenu = true },
                onDismiss = { showMenu = false },
                onReevaluate = onReevaluate,
                onMoveCategory = {
                    selectedCategoryId = article.categoryId
                    showMoveDialog = true
                },
                onDelete = { showDeleteConfirm = true }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除文章") },
            text = { Text("确定删除《${article.title}》吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("移动分类") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategoryId = category.id },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedCategoryId == category.id,
                                onClick = { selectedCategoryId = category.id }
                            )
                            Text(category.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedCategoryId?.let(onMoveCategory)
                        showMoveDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ArticleCardMenu(
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onReevaluate: () -> Unit,
    onMoveCategory: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(onClick = onExpand) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "文章操作"
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text("重新评估") },
                onClick = {
                    onDismiss()
                    onReevaluate()
                }
            )
            DropdownMenuItem(
                text = { Text("移动分类") },
                onClick = {
                    onDismiss()
                    onMoveCategory()
                }
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
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
