package com.xty.englishhelper.ui.screen.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.PlanDaySummary
import com.xty.englishhelper.domain.model.PlanAutoEventLog
import com.xty.englishhelper.domain.model.PlanItem
import com.xty.englishhelper.domain.model.PlanTaskProgress
import com.xty.englishhelper.domain.model.PlanTaskType
import com.xty.englishhelper.domain.model.PlanAutoSource
import com.xty.englishhelper.domain.model.PlanStatsMode
import com.xty.englishhelper.domain.model.PlanTemplate
import com.xty.englishhelper.domain.model.PlanTypeSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PlanTab(val title: String) {
    TODAY("今日"),
    TEMPLATE("模板"),
    STATS("统计")
}

private sealed interface PlanLogTarget {
    data class Article(val articleId: Long) : PlanLogTarget
    data class QuestionGroup(val groupId: Long) : PlanLogTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    onOpenArticle: (Long) -> Unit = {},
    onOpenQuestionGroup: (Long) -> Unit = {},
    viewModel: PlanViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(PlanTab.TODAY) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("计划") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                PlanTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            when (selectedTab) {
                PlanTab.TODAY -> TodayTab(
                    tasks = state.todayTasks,
                    eventLogs = state.todayEventLogs,
                    completionRate = state.todayCompletionRate,
                    streakDays = state.streakDays,
                    onIncrement = viewModel::incrementTask,
                    onDecrement = viewModel::decrementTask,
                    onSetCompleted = viewModel::setTaskCompleted,
                    onOpenArticle = onOpenArticle,
                    onOpenQuestionGroup = onOpenQuestionGroup
                )

                PlanTab.TEMPLATE -> TemplateTab(
                    templates = state.templates,
                    activeTemplate = state.activeTemplate,
                    items = state.activeItems,
                    onSetActive = viewModel::setActiveTemplate,
                    onCreateTemplate = viewModel::createTemplate,
                    onRenameTemplate = viewModel::renameTemplate,
                    onDeleteTemplate = viewModel::deleteTemplate,
                    onAddItem = viewModel::addPlanItem,
                    onUpdateItem = viewModel::updatePlanItem,
                    onDeleteItem = viewModel::deletePlanItem
                )

                PlanTab.STATS -> StatsTab(
                    todayTasks = state.todayTasks,
                    mode = state.statsMode,
                    daySummaries = state.daySummaries,
                    typeSummaries = state.typeSummaries,
                    onModeChange = viewModel::setStatsMode
                )
            }
        }
    }
}

@Composable
private fun TodayTab(
    tasks: List<PlanTaskProgress>,
    eventLogs: List<PlanAutoEventLog>,
    completionRate: Float,
    streakDays: Int,
    onIncrement: (Long) -> Unit,
    onDecrement: (Long) -> Unit,
    onSetCompleted: (Long, Boolean) -> Unit,
    onOpenArticle: (Long) -> Unit,
    onOpenQuestionGroup: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("今日进度", style = MaterialTheme.typography.titleMedium)
                    Text("连续完成 $streakDays 天", style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { completionRate.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "完成率 ${(completionRate * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("自动联动记录", style = MaterialTheme.typography.titleSmall)
                if (eventLogs.isEmpty()) {
                    Text(
                        text = "今天还没有自动联动事件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    eventLogs.take(5).forEach { log ->
                        val target = resolveLogTarget(log)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = target != null) {
                                    when (target) {
                                        is PlanLogTarget.Article -> onOpenArticle(target.articleId)
                                        is PlanLogTarget.QuestionGroup -> onOpenQuestionGroup(target.groupId)
                                        null -> Unit
                                    }
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = planAutoSourceLabel(log.source),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (target != null) {
                                    Text(
                                        text = "点击查看来源",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = formatTime(log.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "当前模板没有任务，请先到“模板”中添加",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks, key = { it.item.id }) { task ->
                    TodayTaskCard(
                        task = task,
                        onIncrement = { onIncrement(task.item.id) },
                        onDecrement = { onDecrement(task.item.id) },
                        onToggleCompleted = { onSetCompleted(task.item.id, !task.record.isCompleted) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayTaskCard(
    task: PlanTaskProgress,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleCompleted: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        text = planTaskTypeLabel(task.item.taskType),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(
                    checked = task.record.isCompleted,
                    onCheckedChange = { onToggleCompleted() }
                )
            }

            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${task.record.doneCount} / ${task.item.targetCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onDecrement) { Text("-1") }
                    TextButton(onClick = onIncrement) { Text("+1") }
                }
            }
        }
    }
}

@Composable
private fun TemplateTab(
    templates: List<PlanTemplate>,
    activeTemplate: PlanTemplate?,
    items: List<PlanItem>,
    onSetActive: (Long) -> Unit,
    onCreateTemplate: (String) -> Unit,
    onRenameTemplate: (Long, String) -> Unit,
    onDeleteTemplate: (Long) -> Unit,
    onAddItem: (PlanTaskType, String, Int, Boolean, PlanAutoSource?) -> Unit,
    onUpdateItem: (Long, PlanTaskType, String, Int, Boolean, PlanAutoSource?) -> Unit,
    onDeleteItem: (Long) -> Unit
) {
    var showCreateTemplateDialog by rememberSaveable { mutableStateOf(false) }
    var showAddItemDialog by rememberSaveable { mutableStateOf(false) }
    var editTemplate by remember { mutableStateOf<PlanTemplate?>(null) }
    var editItem by remember { mutableStateOf<PlanItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("计划模板", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { showCreateTemplateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建模板")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            templates.forEach { template ->
                FilterChip(
                    selected = template.id == activeTemplate?.id,
                    onClick = { onSetActive(template.id) },
                    label = { Text(template.name) },
                    trailingIcon = if (template.id == activeTemplate?.id) {
                        { Text("当前") }
                    } else {
                        null
                    }
                )
            }
        }

        if (activeTemplate != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = { editTemplate = activeTemplate },
                    label = { Text("重命名") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                AssistChip(
                    onClick = { onDeleteTemplate(activeTemplate.id) },
                    label = { Text("删除模板") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
                AssistChip(
                    onClick = { showAddItemDialog = true },
                    label = { Text("新增任务") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                )
            }
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("该模板暂无任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${planTaskTypeLabel(item.taskType)} · 目标 ${item.targetCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.autoEnabled && item.autoSource != null) {
                                    Text(
                                        text = "自动联动：${planAutoSourceLabel(item.autoSource)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { editItem = item }) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑任务")
                                }
                                IconButton(onClick = { onDeleteItem(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除任务")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateTemplateDialog) {
        TemplateNameDialog(
            title = "新建模板",
            initialName = "",
            onDismiss = { showCreateTemplateDialog = false },
            onConfirm = {
                onCreateTemplate(it)
                showCreateTemplateDialog = false
            }
        )
    }

    editTemplate?.let { template ->
        TemplateNameDialog(
            title = "重命名模板",
            initialName = template.name,
            onDismiss = { editTemplate = null },
            onConfirm = {
                onRenameTemplate(template.id, it)
                editTemplate = null
            }
        )
    }

    if (showAddItemDialog) {
        ItemEditorDialog(
            title = "新增任务",
            initialType = PlanTaskType.CUSTOM,
            initialName = "",
            initialTarget = 1,
            initialAutoEnabled = false,
            initialAutoSource = null,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { type, name, target, autoEnabled, autoSource ->
                onAddItem(
                    type,
                    name,
                    target,
                    autoEnabled,
                    autoSource
                )
                showAddItemDialog = false
            }
        )
    }

    editItem?.let { item ->
        ItemEditorDialog(
            title = "编辑任务",
            initialType = item.taskType,
            initialName = item.title,
            initialTarget = item.targetCount,
            initialAutoEnabled = item.autoEnabled,
            initialAutoSource = item.autoSource,
            onDismiss = { editItem = null },
            onConfirm = { type, name, target, autoEnabled, autoSource ->
                onUpdateItem(item.id, type, name, target, autoEnabled, autoSource)
                editItem = null
            }
        )
    }
}

@Composable
private fun StatsTab(
    todayTasks: List<PlanTaskProgress>,
    mode: PlanStatsMode,
    daySummaries: List<PlanDaySummary>,
    typeSummaries: List<PlanTypeSummary>,
    onModeChange: (PlanStatsMode) -> Unit
) {
    val recentDays = daySummaries.sortedBy { it.dayStart }.takeLast(14)
    val filteredTodayTasks = when (mode) {
        PlanStatsMode.ALL -> todayTasks
        PlanStatsMode.AUTO -> todayTasks.filter { it.item.autoEnabled }
        PlanStatsMode.MANUAL -> todayTasks.filter { !it.item.autoEnabled }
    }
    val filteredDone = filteredTodayTasks.count { it.record.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("今日完成视图", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == PlanStatsMode.ALL,
                        onClick = { onModeChange(PlanStatsMode.ALL) },
                        label = { Text("全部") }
                    )
                    FilterChip(
                        selected = mode == PlanStatsMode.AUTO,
                        onClick = { onModeChange(PlanStatsMode.AUTO) },
                        label = { Text("自动") }
                    )
                    FilterChip(
                        selected = mode == PlanStatsMode.MANUAL,
                        onClick = { onModeChange(PlanStatsMode.MANUAL) },
                        label = { Text("手动") }
                    )
                }
                if (filteredTodayTasks.isEmpty()) {
                    Text("暂无任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        text = "已完成 $filteredDone / ${filteredTodayTasks.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { filteredDone.toFloat() / filteredTodayTasks.size.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("近14天完成趋势", style = MaterialTheme.typography.titleMedium)
                if (recentDays.isEmpty()) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        recentDays.forEach { day ->
                            val ratio = if (day.totalCount <= 0) 0f else day.completedCount.toFloat() / day.totalCount
                            val barHeight = (ratio * 110f).dp
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .height(barHeight)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDate(recentDays.first().dayStart), style = MaterialTheme.typography.labelSmall)
                        Text(formatDate(recentDays.last().dayStart), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("任务类型完成率", style = MaterialTheme.typography.titleMedium)
                if (typeSummaries.isEmpty()) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    typeSummaries.forEach { summary ->
                        val rate = summary.completionRate.coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(planTaskTypeLabel(summary.taskType), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${summary.completedCount}/${summary.totalCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(progress = { rate }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("类型分布", style = MaterialTheme.typography.titleMedium)
                if (typeSummaries.isEmpty()) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    PieLikeDistribution(typeSummaries)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PieLikeDistribution(typeSummaries: List<PlanTypeSummary>) {
    val total = typeSummaries.sumOf { it.totalCount }.coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.width(110.dp).height(110.dp)) {
            var start = -90f
            val palette = listOf(
                Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
                Color(0xFFE91E63), Color(0xFF9C27B0)
            )
            typeSummaries.forEachIndexed { index, summary ->
                val sweep = summary.totalCount.toFloat() / total.toFloat() * 360f
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true
                )
                start += sweep
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            val palette = listOf(
                Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
                Color(0xFFE91E63), Color(0xFF9C27B0)
            )
            typeSummaries.forEachIndexed { index, summary ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.width(10.dp).height(10.dp)) {
                        drawRoundRect(
                            color = palette[index % palette.size],
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${planTaskTypeLabel(summary.taskType)} (${summary.totalCount})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ItemEditorDialog(
    title: String,
    initialType: PlanTaskType,
    initialName: String,
    initialTarget: Int,
    initialAutoEnabled: Boolean,
    initialAutoSource: PlanAutoSource?,
    onDismiss: () -> Unit,
    onConfirm: (PlanTaskType, String, Int, Boolean, PlanAutoSource?) -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var targetText by rememberSaveable(initialTarget) { mutableStateOf(initialTarget.toString()) }
    var type by rememberSaveable(initialType) { mutableStateOf(initialType) }
    var autoEnabled by rememberSaveable(initialAutoEnabled) { mutableStateOf(initialAutoEnabled) }
    var autoSource by rememberSaveable(initialAutoSource) { mutableStateOf(initialAutoSource) }

    androidx.compose.runtime.LaunchedEffect(type) {
        val supported = supportedAutoSources(type)
        if (supported.isEmpty()) {
            autoEnabled = false
            autoSource = null
        } else if (autoSource == null) {
            autoSource = defaultAutoSource(type)
            if (autoEnabled.not()) autoEnabled = defaultAutoEnabled(type)
        } else if (autoSource !in supported) {
            autoSource = supported.first()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("每日目标") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("任务类型", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlanTaskType.entries.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(planTaskTypeLabel(candidate)) }
                        )
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自动联动", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = autoEnabled && supportedAutoSources(type).isNotEmpty(),
                        enabled = supportedAutoSources(type).isNotEmpty(),
                        onCheckedChange = { checked ->
                            autoEnabled = checked
                            if (checked && autoSource == null) {
                                autoSource = defaultAutoSource(type)
                            }
                        }
                    )
                }
                if (autoEnabled && supportedAutoSources(type).isNotEmpty()) {
                    Text("联动来源", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        supportedAutoSources(type).forEach { source ->
                            FilterChip(
                                selected = autoSource == source,
                                onClick = { autoSource = source },
                                label = { Text(planAutoSourceLabel(source)) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = targetText.toIntOrNull() ?: 1
                    onConfirm(type, name, target, autoEnabled, autoSource)
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun planTaskTypeLabel(type: PlanTaskType): String {
    return when (type) {
        PlanTaskType.REVIEW_DUE_WORDS -> "复习到期单词"
        PlanTaskType.STUDY_NEW_WORDS -> "学习新词"
        PlanTaskType.READ_ARTICLE -> "阅读文章"
        PlanTaskType.PRACTICE_QUESTIONS -> "题库练习"
        PlanTaskType.CUSTOM -> "自定义"
    }
}

private fun planAutoSourceLabel(source: PlanAutoSource): String {
    return when (source) {
        PlanAutoSource.STUDY_DUE_SESSION -> "背词完成（到期复习）"
        PlanAutoSource.STUDY_NEW_SESSION -> "背词完成（新词学习）"
        PlanAutoSource.ARTICLE_OPEN -> "打开文章"
        PlanAutoSource.ARTICLE_TTS_FINISHED -> "完成文章朗读"
        PlanAutoSource.QUESTION_SUBMIT -> "提交题库答案"
    }
}

private fun supportedAutoSources(type: PlanTaskType): List<PlanAutoSource> {
    return when (type) {
        PlanTaskType.REVIEW_DUE_WORDS -> listOf(PlanAutoSource.STUDY_DUE_SESSION)
        PlanTaskType.STUDY_NEW_WORDS -> listOf(PlanAutoSource.STUDY_NEW_SESSION)
        PlanTaskType.READ_ARTICLE -> listOf(
            PlanAutoSource.ARTICLE_OPEN,
            PlanAutoSource.ARTICLE_TTS_FINISHED
        )
        PlanTaskType.PRACTICE_QUESTIONS -> listOf(PlanAutoSource.QUESTION_SUBMIT)
        PlanTaskType.CUSTOM -> emptyList()
    }
}

private fun defaultAutoEnabled(type: PlanTaskType): Boolean = supportedAutoSources(type).isNotEmpty()

private fun defaultAutoSource(type: PlanTaskType): PlanAutoSource? = supportedAutoSources(type).firstOrNull()

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun resolveLogTarget(log: PlanAutoEventLog): PlanLogTarget? {
    val parts = log.eventKey.split(":")
    if (parts.size < 3) return null
    val lastId = parts.last().toLongOrNull() ?: return null
    return when (log.source) {
        PlanAutoSource.ARTICLE_OPEN,
        PlanAutoSource.ARTICLE_TTS_FINISHED -> PlanLogTarget.Article(lastId)

        PlanAutoSource.QUESTION_SUBMIT -> PlanLogTarget.QuestionGroup(lastId)

        PlanAutoSource.STUDY_DUE_SESSION,
        PlanAutoSource.STUDY_NEW_SESSION -> null
    }
}
