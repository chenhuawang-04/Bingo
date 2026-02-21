package com.xty.englishhelper.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.xty.englishhelper.ui.adaptive.currentWindowWidthClass
import com.xty.englishhelper.ui.adaptive.isExpandedOrMedium
import com.xty.englishhelper.ui.components.ConfirmDialog
import com.xty.englishhelper.ui.components.DictionaryCard
import com.xty.englishhelper.ui.components.EmptyState
import com.xty.englishhelper.ui.components.LoadingIndicator
import com.xty.englishhelper.ui.designsystem.components.EhCard
import com.xty.englishhelper.ui.designsystem.components.EhStatTile
import com.xty.englishhelper.ui.theme.DictionaryColors
import com.xty.englishhelper.ui.theme.EhTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onDictionaryClick: (Long) -> Unit,
    onImportExport: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val windowWidthClass = currentWindowWidthClass()
    val isWide = windowWidthClass.isExpandedOrMedium()

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDashboard()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的辞书") },
                actions = {
                    IconButton(onClick = onImportExport) {
                        Icon(Icons.Default.ImportExport, contentDescription = "导入导出")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showCreateDialog) {
                Icon(Icons.Default.Add, contentDescription = "创建辞书")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(Modifier.padding(padding))
            state.dictionaries.isEmpty() -> EmptyState(
                message = "还没有辞书\n点击 + 创建一个吧",
                modifier = Modifier.padding(padding)
            )
            else -> {
                if (isWide) {
                    // Medium/Expanded: side-by-side dashboard + grid
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        // Left panel: Dashboard
                        if (state.dashboard.hasData) {
                            Column(
                                modifier = Modifier
                                    .width(320.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                DashboardPanel(state.dashboard)
                            }
                            VerticalDivider()
                        }
                        // Right panel: Dictionary grid
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 280.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.dictionaries, key = { it.id }) { dict ->
                                DictionaryCard(
                                    dictionary = dict,
                                    onClick = { onDictionaryClick(dict.id) },
                                    onDelete = { viewModel.showDeleteConfirm(dict) }
                                )
                            }
                        }
                    }
                } else {
                    // Compact: original single-column list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.dashboard.hasData) {
                            item(key = "dashboard") {
                                DashboardCard(state.dashboard)
                            }
                        }
                        items(state.dictionaries, key = { it.id }) { dict ->
                            DictionaryCard(
                                dictionary = dict,
                                onClick = { onDictionaryClick(dict.id) },
                                onDelete = { viewModel.showDeleteConfirm(dict) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create dialog
    if (state.showCreateDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissCreateDialog,
            title = { Text("创建辞书") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.newDictName,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("辞书名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.newDictDesc,
                        onValueChange = viewModel::onDescChange,
                        label = { Text("辞书描述（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("选择颜色", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DictionaryColors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (index == state.selectedColorIndex)
                                            Modifier.border(3.dp, Color.White, CircleShape)
                                                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { viewModel.onColorSelect(index) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmCreate,
                    enabled = state.newDictName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCreateDialog) { Text("取消") }
            }
        )
    }

    // Delete confirm dialog
    state.deleteTarget?.let { dict ->
        ConfirmDialog(
            title = "删除辞书",
            message = "确定要删除辞书「${dict.name}」吗？其中的所有单词也将被删除。",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

@Composable
private fun DashboardCard(stats: DashboardStats) {
    val semantic = EhTheme.semanticColors
    val retentionColor = when {
        stats.averageRetention >= 0.8 -> semantic.retentionHigh
        stats.averageRetention >= 0.6 -> semantic.retentionMid
        else -> semantic.retentionLow
    }
    val retentionText = "%.1f%%".format(stats.averageRetention * 100)
    val progressText = "${stats.reviewedToday}/${stats.todayTotal}"
    val clearText = stats.estimatedClearHours?.let { "~%.1f时".format(it) } ?: "—"

    EhCard {
        Text(
            text = "学习仪表板",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EhStatTile(
                value = retentionText,
                label = "留存率",
                valueColor = retentionColor,
                modifier = Modifier.weight(1f)
            )
            EhStatTile(
                value = stats.dueCount.toString(),
                label = "到期词",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EhStatTile(
                value = progressText,
                label = "今日进度",
                modifier = Modifier.weight(1f)
            )
            EhStatTile(
                value = clearText,
                label = "清空预估",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DashboardPanel(stats: DashboardStats) {
    val semantic = EhTheme.semanticColors
    val retentionColor = when {
        stats.averageRetention >= 0.8 -> semantic.retentionHigh
        stats.averageRetention >= 0.6 -> semantic.retentionMid
        else -> semantic.retentionLow
    }
    val retentionText = "%.1f%%".format(stats.averageRetention * 100)
    val progressText = "${stats.reviewedToday}/${stats.todayTotal}"
    val clearText = stats.estimatedClearHours?.let { "~%.1f时".format(it) } ?: "—"

    EhCard {
        Text(
            text = "学习仪表板",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EhStatTile(
                value = retentionText,
                label = "留存率",
                valueColor = retentionColor,
                modifier = Modifier.fillMaxWidth()
            )
            EhStatTile(
                value = stats.dueCount.toString(),
                label = "到期词",
                modifier = Modifier.fillMaxWidth()
            )
            EhStatTile(
                value = progressText,
                label = "今日进度",
                modifier = Modifier.fillMaxWidth()
            )
            EhStatTile(
                value = clearText,
                label = "清空预估",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
