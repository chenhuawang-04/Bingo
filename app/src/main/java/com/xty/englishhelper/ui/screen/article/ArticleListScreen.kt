package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import com.xty.englishhelper.domain.model.ArticleParseStatus

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
                    IconButton(onClick = viewModel::toggleSortByScore) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "按评分排序",
                            tint = if (uiState.sortByScore) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(onClick = onGuardianBrowse) {
                        Icon(Icons.Default.Language, contentDescription = "在线阅读")
                    }
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
        if (articles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.categories.isNotEmpty()) {
                    CategoryFilterBar(
                        categories = uiState.categories,
                        selectedCategoryId = uiState.selectedCategoryId,
                        onSelect = viewModel::selectCategory,
                        onCreate = { showCreateCategoryDialog = true }
                    )
                }
                Text("暂无文章，点击 + 创建新文章", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CategoryFilterBar(
                        categories = uiState.categories,
                        selectedCategoryId = uiState.selectedCategoryId,
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

    val hasCover = article.coverImageUri != null || article.coverImageUrl != null
    val scoreText = when {
        isEvaluating -> "评估中…"
        article.suitabilityScore != null -> "评分：${article.suitabilityScore}"
        else -> "未评估"
    }

    Card(
        onClick = onRead,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (hasCover) {
            // Card with cover image
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = article.coverImageUri ?: article.coverImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (article.author.isNotBlank()) {
                        Text(
                            article.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (article.wordCount > 0) {
                            Text(
                                "${article.wordCount} 词",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (article.source.isNotBlank()) {
                            Text(
                                article.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!categoryName.isNullOrBlank()) {
                            Text(
                                categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            scoreText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (article.suitabilityScore != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        val statusText = parseStatusText(article.parseStatus)
                        if (statusText != null) {
                            Text(
                                statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Text("···", style = MaterialTheme.typography.bodyMedium)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("重新评估") },
                            onClick = {
                                showMenu = false
                                onReevaluate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("移动到分类") },
                            onClick = {
                                showMenu = false
                                selectedCategoryId = article.categoryId
                                showMoveDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        } else {
            // Card without cover: gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (article.author.isNotBlank()) {
                                Text(
                                    article.author,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            if (article.wordCount > 0) {
                                Text(
                                    "${article.wordCount} 词",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            if (!categoryName.isNullOrBlank()) {
                                Text(
                                    categoryName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                scoreText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (article.suitabilityScore != null) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                }
                            )
                            val statusText = parseStatusText(article.parseStatus)
                            if (statusText != null) {
                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                                Text("···", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("重新评估") },
                                    onClick = {
                                        showMenu = false
                                        onReevaluate()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("移动到分类") },
                                    onClick = {
                                        showMenu = false
                                        selectedCategoryId = article.categoryId
                                        showMoveDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
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
                        val targetId = selectedCategoryId
                        if (targetId != null) {
                            onMoveCategory(targetId)
                        }
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
private fun CategoryFilterBar(
    categories: List<ArticleCategory>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            item {
                TextButton(onClick = onCreate) {
                    Text("新建分类")
                }
            }
        }
        if (selectedCategoryId == ArticleCategoryDefaults.SOURCE_ID) {
            Text(
                "题目来源文章将默认进入此分类",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
