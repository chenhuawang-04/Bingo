package com.xty.englishhelper.ui.screen.article

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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
                Icon(Icons.Default.Add, contentDescription = "创建文章")
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
            if (articles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ArticleCategoryPanel(
                        categories = uiState.categories,
                        selectedCategoryId = uiState.selectedCategoryId,
                        filterEnabled = uiState.filterEnabled,
                        lengthFilter = uiState.lengthFilter,
                        scoreFilter = uiState.scoreFilter,
                        sortOption = uiState.sortOption,
                        onSelect = viewModel::selectCategory,
                        onCreate = { showCreateCategoryDialog = true }
                    )
                    EmptyArticleStateCard(
                        hasSourceArticles = hasSourceArticles,
                        onCreateArticle = onCreateArticle
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ArticleCategoryPanel(
                            categories = uiState.categories,
                            selectedCategoryId = uiState.selectedCategoryId,
                            filterEnabled = uiState.filterEnabled,
                            lengthFilter = uiState.lengthFilter,
                            scoreFilter = uiState.scoreFilter,
                            sortOption = uiState.sortOption,
                            onSelect = viewModel::selectCategory,
                            onCreate = { showCreateCategoryDialog = true }
                        )
                    }
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
                    item {
                        Spacer(Modifier.height(88.dp))
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
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateCategoryDialog = false
                        newCategoryName = ""
                    }
                ) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArticleCategoryPanel(
    categories: List<ArticleCategory>,
    selectedCategoryId: Long?,
    filterEnabled: Boolean,
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption,
    onSelect: (Long?) -> Unit,
    onCreate: () -> Unit
) {
    val selectedName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "全部文章"
    val hasFilterConfig = hasArticleFilterConfig(lengthFilter, scoreFilter, sortOption)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("分类与范围", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            filterEnabled -> "当前仅展示符合筛选条件的文章"
                            hasFilterConfig -> "筛选条件已保存，尚未启用"
                            else -> "先选分类，再决定是否启用筛选"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onCreate) {
                    Text("新建分类")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArticleInfoPill(
                    text = selectedName,
                    emphasized = true
                )
                if (filterEnabled) {
                    ArticleInfoPill(
                        text = "筛选已启用",
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else if (hasFilterConfig) {
                    ArticleInfoPill(text = "筛选已暂停")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onSelect(null) },
                        label = { Text("全部") }
                    )
                }
                items(categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { onSelect(category.id) },
                        label = { Text(category.name) }
                    )
                }
            }

            if (selectedCategoryId == ArticleCategoryDefaults.SOURCE_ID) {
                Text(
                    "题目来源文章会自动归入该分类，便于和普通阅读文章分开查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyArticleStateCard(
    hasSourceArticles: Boolean,
    onCreateArticle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (hasSourceArticles) "没有符合当前条件的文章" else "还没有文章",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasSourceArticles) {
                    "可以切换分类，或暂停筛选后再看完整列表。"
                } else {
                    "可以手动创建文章，也可以进入在线阅读后保存文章到本地。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!hasSourceArticles) {
                TextButton(onClick = onCreateArticle) {
                    Text("立即创建")
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

    val scoreText = when {
        isEvaluating -> "评估中…"
        article.suitabilityScore != null -> "评分 ${article.suitabilityScore}"
        else -> "未评估"
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
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            ArticleCardThumbnail(
                coverModel = coverModel,
                placeholderSeed = placeholderSeed
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = buildList {
                            if (article.author.isNotBlank()) add(article.author)
                            if (article.source.isNotBlank()) add(article.source)
                        }.joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (article.wordCount > 0) {
                        ArticleInfoPill(text = "${article.wordCount} 词")
                    }
                    if (!categoryName.isNullOrBlank()) {
                        ArticleInfoPill(
                            text = categoryName,
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    ArticleInfoPill(
                        text = scoreText,
                        containerColor = scoreColor.copy(alpha = 0.12f),
                        contentColor = scoreColor
                    )
                    if (parseStatusText != null) {
                        ArticleInfoPill(text = parseStatusText)
                    }
                }

                if (!article.suitabilityReason.isNullOrBlank()) {
                    Text(
                        text = article.suitabilityReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            title = { Text("确认删除") },
            text = { Text("确定要删除「${article.title}」吗？") },
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
            title = { Text("移动到分类") },
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
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ArticleCardThumbnail(
    coverModel: Any?,
    placeholderSeed: String
) {
    if (coverModel != null) {
        AsyncImage(
            model = coverModel,
            contentDescription = null,
            modifier = Modifier
                .width(92.dp)
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .width(92.dp)
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape
            ) {
                Text(
                    text = placeholderSeed,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ArticleInfoPill(
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
                contentDescription = "更多操作",
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
                text = { Text("移动到分类") },
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

private fun parseStatusText(status: ArticleParseStatus): String? {
    return when (status) {
        ArticleParseStatus.PROCESSING -> "解析中…"
        ArticleParseStatus.FAILED -> "解析失败"
        else -> null
    }
}
