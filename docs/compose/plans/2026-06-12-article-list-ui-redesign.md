# 文章列表页 UI 重构实施计划

> [!NOTE]
> This document may not reflect the current implementation.
> See the final report for up-to-date state:
> [Final Report](../reports/article-list-ui-redesign.md)

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构文章列表页，移除顶部大卡片，改为轻量内联组件，新增扫描详情页

**Architecture:** 保持现有 MVVM 架构，ArticleListScreen 重构布局，新增 ScanDetailScreen 扫描详情页，复用现有筛选逻辑

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Navigation Compose

---

### Task 1: 更新 ArticleShapes 圆角

**Covers:** [S11]

**Files:**
- Modify: `app/src/main/java/com/xty/englishhelper/ui/designsystem/tokens/ArticleShapes.kt`

- [ ] **Step 1: 修改圆角值**

```kotlin
package com.xty.englishhelper.ui.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object ArticleShapes {
    val Hero = RoundedCornerShape(8.dp)
    val Section = RoundedCornerShape(6.dp)
    val Card = RoundedCornerShape(6.dp)
    val Thumbnail = RoundedCornerShape(4.dp)
    val Control = RoundedCornerShape(4.dp)
    val Chip = RoundedCornerShape(4.dp)
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/xty/englishhelper/ui/designsystem/tokens/ArticleShapes.kt
git commit -m "refactor: 减小 ArticleShapes 圆角值"
```

---

### Task 2: 添加扫描详情路由

**Covers:** [S6, S7]

**Files:**
- Modify: `app/src/main/java/com/xty/englishhelper/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/xty/englishhelper/ui/navigation/NavGraph.kt`

- [ ] **Step 1: 在 Screen.kt 添加路由**

在文件末尾（`PlanRoute` 之后）添加：

```kotlin
@Serializable
data object ScanDetailRoute
```

- [ ] **Step 2: 在 NavGraph.kt 添加 composable**

在 `composable<BackgroundTaskRoute>` 之前添加：

```kotlin
composable<ScanDetailRoute> {
    ScanDetailScreen(
        onBack = { navController.popBackStack() }
    )
}
```

并在文件顶部添加 import：

```kotlin
import com.xty.englishhelper.ui.screen.article.ScanDetailScreen
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xty/englishhelper/ui/navigation/Screen.kt app/src/main/java/com/xty/englishhelper/ui/navigation/NavGraph.kt
git commit -m "feat: 添加 ScanDetailRoute 路由"
```

---

### Task 3: 创建 ScanDetailScreen

**Covers:** [S7]

**Files:**
- Create: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ScanDetailScreen.kt`
- Create: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ScanDetailViewModel.kt`

- [ ] **Step 1: 创建 ScanDetailViewModel**

```kotlin
package com.xty.englishhelper.ui.screen.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanDetailUiState(
    val scanTask: BackgroundTask? = null,
    val maxPerSection: Int = 5,
    val rescoreAfterHours: Int = 24,
    val isConfigExpanded: Boolean = false
)

@HiltViewModel
class ScanDetailViewModel @Inject constructor(
    private val backgroundTaskRepository: BackgroundTaskRepository,
    private val backgroundTaskManager: BackgroundTaskManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanDetailUiState())
    val uiState: StateFlow<ScanDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            backgroundTaskRepository.getTasksByType(BackgroundTaskType.ONLINE_ARTICLE_SCAN_SCORE)
                .collect { tasks ->
                    val task = tasks.firstOrNull { it.status != BackgroundTaskStatus.CANCELED }
                    _uiState.update { it.copy(scanTask = task) }
                }
        }
        viewModelScope.launch {
            settingsDataStore.scanMaxPerSection.collect { value ->
                _uiState.update { it.copy(maxPerSection = value) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.scanRescoreAfterHours.collect { value ->
                _uiState.update { it.copy(rescoreAfterHours = value) }
            }
        }
    }

    fun toggleScanConfig() {
        _uiState.update { it.copy(isScanConfigExpanded = !it.isConfigExpanded) }
    }

    fun setScanMaxPerSection(value: Int) {
        _uiState.update { it.copy(maxPerSection = value) }
        viewModelScope.launch { settingsDataStore.setScanMaxPerSection(value) }
    }

    fun setScanRescoreAfterHours(value: Int) {
        _uiState.update { it.copy(rescoreAfterHours = value) }
        viewModelScope.launch { settingsDataStore.setScanRescoreAfterHours(value) }
    }

    fun triggerScan() {
        viewModelScope.launch {
            backgroundTaskManager.startOnlineArticleScanScore(
                maxPerSection = _uiState.value.maxPerSection,
                rescoreAfterHours = _uiState.value.rescoreAfterHours
            )
        }
    }

    fun cancelScan() {
        viewModelScope.launch {
            _uiState.value.scanTask?.let { task ->
                backgroundTaskRepository.updateStatus(task.id, BackgroundTaskStatus.CANCELED)
            }
        }
    }

    fun pauseScan() {
        viewModelScope.launch {
            _uiState.value.scanTask?.let { task ->
                backgroundTaskRepository.updateStatus(task.id, BackgroundTaskStatus.PAUSED)
            }
        }
    }

    fun resumeScan() {
        viewModelScope.launch {
            _uiState.value.scanTask?.let { task ->
                backgroundTaskRepository.updateStatus(task.id, BackgroundTaskStatus.RUNNING)
            }
        }
    }

    fun deleteScanTask() {
        viewModelScope.launch {
            _uiState.value.scanTask?.let { task ->
                backgroundTaskRepository.deleteTask(task.id)
            }
        }
    }
}
```

- [ ] **Step 2: 创建 ScanDetailScreen**

```kotlin
package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDetailScreen(
    onBack: () -> Unit,
    viewModel: ScanDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val scanTask = uiState.scanTask
    val status = scanTask?.status
    val (statusLabel, statusColor) = when (status) {
        null -> "未运行" to MaterialTheme.colorScheme.onSurfaceVariant
        BackgroundTaskStatus.PENDING -> "等待中" to MaterialTheme.colorScheme.secondary
        BackgroundTaskStatus.RUNNING -> "扫描中" to MaterialTheme.colorScheme.primary
        BackgroundTaskStatus.PAUSED -> "已暂停" to MaterialTheme.colorScheme.tertiary
        BackgroundTaskStatus.SUCCESS -> "已完成" to MaterialTheme.colorScheme.primary
        BackgroundTaskStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
        BackgroundTaskStatus.CANCELED -> "已停止" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isActive = status == BackgroundTaskStatus.PENDING || status == BackgroundTaskStatus.RUNNING
    val isPaused = status == BackgroundTaskStatus.PAUSED
    val isTerminal = status == BackgroundTaskStatus.SUCCESS ||
        status == BackgroundTaskStatus.FAILED ||
        status == BackgroundTaskStatus.CANCELED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("在线文章扫描") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        EhMaxWidthContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            maxWidth = 720.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                    color = statusColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = statusLabel,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor
                                    )
                                }
                                Text(
                                    text = "扫描状态",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            IconButton(onClick = viewModel::toggleScanConfig) {
                                Icon(
                                    imageVector = if (uiState.isConfigExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (uiState.isConfigExpanded) "收起配置" else "展开配置"
                                )
                            }
                        }

                        if (isActive) {
                            val task = scanTask!!
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    progress = {
                                        if (task.progressTotal > 0) {
                                            task.progressCurrent.toFloat() / task.progressTotal
                                        } else 0f
                                    }
                                )
                                Text(
                                    text = "进度 ${task.progressCurrent}/${task.progressTotal}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val errorMsg = scanTask?.errorMessage
                        if (status == BackgroundTaskStatus.FAILED && errorMsg != null) {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 3
                            )
                        }

                        if (uiState.isConfigExpanded) {
                            HorizontalDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "每栏目最多 ${uiState.maxPerSection} 篇",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Slider(
                                        value = uiState.maxPerSection.toFloat(),
                                        onValueChange = { viewModel.setScanMaxPerSection(it.roundToInt()) },
                                        valueRange = 1f..20f,
                                        steps = 18
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "重评间隔 ${uiState.rescoreAfterHours} 小时",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Slider(
                                        value = uiState.rescoreAfterHours.toFloat(),
                                        onValueChange = { viewModel.setScanRescoreAfterHours(it.roundToInt()) },
                                        valueRange = 1f..720f,
                                        steps = 0
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (status == null || isTerminal) {
                                Button(onClick = viewModel::triggerScan) {
                                    Text("开始扫描")
                                }
                            }
                            if (isActive) {
                                Button(
                                    onClick = viewModel::cancelScan,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("停止")
                                }
                            }
                            if (isPaused) {
                                Button(onClick = viewModel::resumeScan) {
                                    Text("继续")
                                }
                                TextButton(onClick = viewModel::deleteScanTask) {
                                    Text("清除")
                                }
                            }
                            if (isActive) {
                                TextButton(onClick = viewModel::pauseScan) {
                                    Text("暂停")
                                }
                            }
                            if (status == BackgroundTaskStatus.FAILED || status == BackgroundTaskStatus.CANCELED) {
                                TextButton(onClick = viewModel::deleteScanTask) {
                                    Text("清除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xty/englishhelper/ui/screen/article/ScanDetailScreen.kt app/src/main/java/com/xty/englishhelper/ui/screen/article/ScanDetailViewModel.kt
git commit -m "feat: 添加 ScanDetailScreen 扫描详情页"
```

---

### Task 4: 重构 ArticleListScreen 顶部区域

**Covers:** [S3, S4, S5, S6]

**Files:**
- Modify: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleListScreen.kt`

- [ ] **Step 1: 添加导航回调参数**

在 `ArticleListScreen` 函数签名中添加 `onScanDetail` 参数：

```kotlin
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
```

- [ ] **Step 2: 替换 LibraryOverviewCard 为 CategoryTabs**

在 `Scaffold` 的 `content` 中，将 `LibraryOverviewCard(...)` 替换为：

```kotlin
CategoryTabRow(
    categories = uiState.categories,
    selectedCategoryId = uiState.selectedCategoryId,
    onSelectCategory = viewModel::selectCategory,
    onCreateCategory = { showCreateCategoryDialog = true }
)
```

在文件末尾添加 `CategoryTabRow` 组件：

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryTabRow(
    categories: List<ArticleCategory>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    onCreateCategory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
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
```

- [ ] **Step 3: 替换 OnlineScanStatusCard 为内联扫描进度**

在 `CategoryTabRow` 之后、文章列表之前添加：

```kotlin
ScanProgressRow(
    scanTask = uiState.scanTask,
    onClick = onScanDetail
)
```

在文件末尾添加 `ScanProgressRow` 组件：

```kotlin
@Composable
private fun ScanProgressRow(
    scanTask: BackgroundTask?,
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
            LinearProgressIndicator(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp),
                progress = {
                    if (scanTask!!.progressTotal > 0) {
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
```

- [ ] **Step 4: 添加缺失的 import**

在文件顶部添加：

```kotlin
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleListScreen.kt
git commit -m "refactor: 重构文章列表顶部区域为内联组件"
```

---

### Task 5: 简化 ArticleCard

**Covers:** [S8]

**Files:**
- Modify: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleListScreen.kt`

- [ ] **Step 1: 更新 ArticleCard 布局**

将 `ArticleCard` 中的 `Row` 内容改为更紧凑的布局：

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleListScreen.kt
git commit -m "refactor: 简化 ArticleCard 布局，缩略图缩小至 40dp"
```

---

### Task 6: 简化空状态

**Covers:** [S9]

**Files:**
- Modify: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleListScreen.kt`

- [ ] **Step 1: 更新 EmptyArticleStateCard**

```kotlin
@Composable
private fun EmptyArticleStateCard(
    hasSourceArticles: Boolean,
    filterEnabled: Boolean,
    onCreateArticle: () -> Unit,
    onOpenCategories: () -> Unit,
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
            if (hasSourceArticles) {
                TextButton(onClick = onOpenCategories) {
                    Text("切换分类")
                }
            } else {
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleListScreen.kt
git commit -m "refactor: 简化空状态为文字按钮样式"
```

---

### Task 7: 更新 NavGraph 调用

**Covers:** [S6]

**Files:**
- Modify: `app/src/main/java/com/xty/englishhelper/ui/navigation/NavGraph.kt`

- [ ] **Step 1: 在 ArticleListRoute 的 composable 中添加 onScanDetail**

找到 `composable<ArticleListRoute>` 块，添加 `onScanDetail` 参数：

```kotlin
composable<ArticleListRoute> {
    ArticleListScreen(
        onCreateArticle = {
            navController.navigate(ArticleEditorRoute())
        },
        onReadArticle = { articleId ->
            navController.navigate(ArticleReaderRoute(articleId))
        },
        onSettings = {
            navController.navigate(SettingsRoute)
        },
        onGuardianBrowse = {
            navController.navigate(GuardianBrowseRoute)
        },
        onScanDetail = {
            navController.navigate(ScanDetailRoute)
        }
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/xty/englishhelper/ui/navigation/NavGraph.kt
git commit -m "feat: 连接文章列表与扫描详情页导航"
```

---

### Task 8: 移除旧组件

**Covers:** [S3]

**Files:**
- Modify: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleListScreen.kt`

- [ ] **Step 1: 删除 LibraryOverviewCard 和 OnlineScanStatusCard 函数**

从文件中删除这两个 Composable 函数（约 200-300 行）。

- [ ] **Step 2: 删除 CategorySelectionSheet 和 CategorySheetRow**

从文件中删除这两个 Composable 函数（约 100 行）。

- [ ] **Step 3: 清理未使用的 import**

删除不再需要的 import：
```kotlin
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.style.TextOverflow
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleListScreen.kt
git commit -m "refactor: 移除 LibraryOverviewCard 和 OnlineScanStatusCard 旧组件"
```

---

### Task 9: 验证与测试

**Covers:** 全部

**Files:** 无

- [ ] **Step 1: 编译检查**

```bash
.\gradlew.bat assembleDebug
```

- [ ] **Step 2: 运行 lint**

```bash
.\gradlew.bat lintDebug
```

- [ ] **Step 3: 最终 Commit（如有修复）**

```bash
git add -A
git commit -m "fix: 修复编译/lint 问题"
```
