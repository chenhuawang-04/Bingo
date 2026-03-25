package com.xty.englishhelper.ui.screen.article

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onCreateArticle: () -> Unit,
    onReadArticle: (Long) -> Unit,
    onSettings: () -> Unit,
    onGuardianBrowse: () -> Unit = {},
    viewModel: ArticleListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val articles = uiState.articles
    val hasSourceArticles = uiState.allArticles.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedCategoryName = uiState.categories
        .firstOrNull { it.id == uiState.selectedCategoryId }
        ?.name
        ?: "全部文章"
    val hasFilterConfig = hasArticleFilterConfig(
        lengthFilter = uiState.lengthFilter,
        scoreFilter = uiState.scoreFilter,
        sortOption = uiState.sortOption
    )
    var showCreateCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LibraryOverviewCard(
                    selectedCategoryName = selectedCategoryName,
                    visibleCount = articles.size,
                    totalCount = uiState.allArticles.size,
                    filterEnabled = uiState.filterEnabled,
                    hasFilterConfig = hasFilterConfig,
                    onOpenCategories = { showCategorySheet = true },
                    onCreateCategory = { showCreateCategoryDialog = true }
                )

                if (articles.isEmpty()) {
                    EmptyArticleStateCard(
                        hasSourceArticles = hasSourceArticles,
                        filterEnabled = uiState.filterEnabled,
                        onCreateArticle = onCreateArticle,
                        onOpenCategories = { showCategorySheet = true },
                        onResetFilters = viewModel::resetFilters
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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

    if (showCategorySheet) {
        CategorySelectionSheet(
            categories = uiState.categories,
            selectedCategoryId = uiState.selectedCategoryId,
            filterEnabled = uiState.filterEnabled,
            hasFilterConfig = hasFilterConfig,
            onSelect = {
                viewModel.selectCategory(it)
                showCategorySheet = false
            },
            onCreate = {
                showCategorySheet = false
                showCreateCategoryDialog = true
            },
            onDismiss = { showCategorySheet = false }
        )
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
                TextButton(
                    onClick = {
                        showCreateCategoryDialog = false
                        newCategoryName = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryOverviewCard(
    selectedCategoryName: String,
    visibleCount: Int,
    totalCount: Int,
    filterEnabled: Boolean,
    hasFilterConfig: Boolean,
    onOpenCategories: () -> Unit,
    onCreateCategory: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
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
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
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
                        text = "本地文章库",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = selectedCategoryName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            filterEnabled -> "当前展示 $visibleCount / $totalCount 篇文章，筛选结果已生效。"
                            hasFilterConfig -> "已保存筛选条件，目前仍在查看全部 $totalCount 篇文章。"
                            else -> "当前共整理 $totalCount 篇文章，按分类安静浏览。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EditorialPill(text = "可见 $visibleCount 篇", emphasized = true)
                    EditorialPill(text = "总计 $totalCount 篇")
                    if (filterEnabled) {
                        EditorialPill(
                            text = "筛选开启",
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    } else if (hasFilterConfig) {
                        EditorialPill(text = "条件待启用")
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stackedLayout = maxWidth < 560.dp
                    if (stackedLayout) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "当前分类",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedCategoryName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EditorialActionButton(
                                    text = "切换分类",
                                    icon = Icons.Default.Edit,
                                    onClick = onOpenCategories
                                )
                                EditorialActionButton(
                                    text = "新建",
                                    icon = Icons.Default.Add,
                                    onClick = onCreateCategory,
                                    prominent = true
                                )
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
                                    text = "当前分类",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedCategoryName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EditorialActionButton(
                                    text = "切换分类",
                                    icon = Icons.Default.Edit,
                                    onClick = onOpenCategories
                                )
                                EditorialActionButton(
                                    text = "新建",
                                    icon = Icons.Default.Add,
                                    onClick = onCreateCategory,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategorySelectionSheet(
    categories: List<ArticleCategory>,
    selectedCategoryId: Long?,
    filterEnabled: Boolean,
    hasFilterConfig: Boolean,
    onSelect: (Long?) -> Unit,
    onCreate: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "分类与范围",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = if (filterEnabled) {
                    "筛选开启时，仅对当前分类下符合条件的文章生效。"
                } else if (hasFilterConfig) {
                    "你已经配置筛选条件，启用后再缩小当前分类范围。"
                } else {
                    "先切换分类，再决定是否叠加长度、评分与排序条件。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorialPill(
                    text = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "全部文章",
                    emphasized = true
                )
                if (filterEnabled) {
                    EditorialPill(
                        text = "筛选开启",
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else if (hasFilterConfig) {
                    EditorialPill(text = "条件待启用")
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategorySheetRow(
                        title = "全部文章",
                        supporting = "浏览当前库中的所有文章",
                        selected = selectedCategoryId == null,
                        onClick = { onSelect(null) }
                    )
                    categories.forEach { category ->
                        val supporting = when (category.id) {
                            ArticleCategoryDefaults.DEFAULT_ID -> "默认收纳分类，适合日常整理"
                            ArticleCategoryDefaults.SOURCE_ID -> "文章出题后自动沉淀到这里"
                            else -> "你手动维护的自定义分类"
                        }
                        CategorySheetRow(
                            title = category.name,
                            supporting = supporting,
                            selected = selectedCategoryId == category.id,
                            onClick = { onSelect(category.id) }
                        )
                    }
                }
            }

            TextButton(
                onClick = onCreate,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("新建分类")
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
@Composable
private fun CategorySheetRow(
    title: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = supporting,
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
}

@Composable
private fun EmptyArticleStateCard(
    hasSourceArticles: Boolean,
    filterEnabled: Boolean,
    onCreateArticle: () -> Unit,
    onOpenCategories: () -> Unit,
    onResetFilters: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
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
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                        )
                    )
                )
                .padding(horizontal = 22.dp, vertical = 26.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditorialPill(
                    text = if (filterEnabled) "筛选结果为空" else "等待内容进入分类",
                    emphasized = true
                )
                Text(
                    text = when {
                        hasSourceArticles -> "当前分类下暂时没有符合条件的文章"
                        else -> "还没有文章进入这个分类"
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = when {
                        hasSourceArticles && filterEnabled ->
                            "可以先调整分类或重置筛选条件，再决定保留哪些文章继续阅读。"
                        hasSourceArticles ->
                            "文章仍在库中，只是当前分类里没有内容。可以切换分类继续浏览。"
                        else ->
                            "你可以新建一篇本地文章，或者先在右上角进入在线阅读，把合适的文章保存回来。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = if (hasSourceArticles) "切换分类" else "新建文章",
                            modifier = Modifier
                                .clickable(onClick = if (hasSourceArticles) onOpenCategories else onCreateArticle)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (hasSourceArticles && filterEnabled) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Text(
                                text = "重置筛选",
                                modifier = Modifier
                                    .clickable(onClick = onResetFilters)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        article.suitabilityScore != null -> "评分 ${article.suitabilityScore}"
        else -> "未评分"
    }
    val scoreColor = when {
        isEvaluating -> MaterialTheme.colorScheme.primary
        article.suitabilityScore != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val parseStatusText = parseStatusText(article.parseStatus)
    val coverModel = article.coverImageUri ?: article.coverImageUrl
    val placeholderSeed = article.title.firstOrNull()?.uppercase() ?: "A"

    Card(
        onClick = onRead,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            EditorialThumbnail(
                imageModel = coverModel,
                fallbackSeed = placeholderSeed,
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(0.76f)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (sourceLine.isNotBlank()) {
                            Text(
                                text = sourceLine,
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

                if (snippet.isNotBlank()) {
                    Text(
                        text = snippet,
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
                    if (article.wordCount > 0) {
                        EditorialPill(text = "${article.wordCount} 词")
                    }
                    if (!categoryName.isNullOrBlank()) {
                        EditorialPill(
                            text = categoryName,
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    EditorialPill(
                        text = scoreText,
                        containerColor = scoreColor.copy(alpha = 0.12f),
                        contentColor = scoreColor
                    )
                    if (parseStatusText != null) {
                        EditorialPill(text = parseStatusText)
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
            }
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
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(modifier = modifier) {
        IconButton(onClick = onExpand) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "文章操作",
                tint = iconTint
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
        .take(170)
}

private fun parseStatusText(status: ArticleParseStatus): String? {
    return when (status) {
        ArticleParseStatus.PROCESSING -> "解析中"
        ArticleParseStatus.FAILED -> "解析失败"
        else -> null
    }
}
