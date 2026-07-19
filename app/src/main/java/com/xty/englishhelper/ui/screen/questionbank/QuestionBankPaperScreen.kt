package com.xty.englishhelper.ui.screen.questionbank

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.ExamPaperSourceStatus
import com.xty.englishhelper.domain.model.ExamPaperStatus
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionBankPaperScreen(
    onBack: () -> Unit,
    onStartPaper: (groupId: Long, paperId: Long) -> Unit,
    onGroupClick: (groupId: Long) -> Unit,
    onArticleClick: (articleId: Long) -> Unit,
    viewModel: QuestionBankPaperViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val paper = state.paper

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    AppTopBarEffect(
        title = { Text(paper?.title ?: "试卷") },
        navigationIcon = { AppTopBarBackButton(onBack) }
    )

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (paper == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("试卷不存在")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item("paper_header") {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(paper.title, style = MaterialTheme.typography.titleLarge)
                                    paper.description?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(
                                        when (paper.status) {
                                            ExamPaperStatus.COLLECTING -> "收集中"
                                            ExamPaperStatus.READY -> "待出题"
                                            ExamPaperStatus.GENERATING -> "出题中"
                                            ExamPaperStatus.READY_TO_PRACTICE -> "可练习"
                                            ExamPaperStatus.FAILED -> "失败"
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Text("已收集 ${state.sources.size} 篇来源 · 已生成 ${state.groups.size} 个题组 · ${paper.totalQuestions} 题")
                            if (state.groups.isNotEmpty()) {
                                Text("整卷进度 ${state.completedGroupIds.size}/${state.groups.size} 个题组")
                            }
                            paper.specialQuestionType?.let { Text("本套轮换新题型：${it.displayName}") }
                            if (state.isGenerating || paper.status == ExamPaperStatus.GENERATING) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("后台自动出题中，已完成 ${state.groups.size}/${state.sources.size}")
                                }
                            }
                            paper.generationError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            if (paper.status == ExamPaperStatus.FAILED) {
                                OutlinedButton(onClick = viewModel::retryGeneration) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("从失败处继续")
                                }
                            }
                            if (paper.status == ExamPaperStatus.READY_TO_PRACTICE && state.groups.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        val resumeGroup = state.groups.firstOrNull { it.id !in state.completedGroupIds }
                                            ?: state.groups.first()
                                        onStartPaper(resumeGroup.id, paper.id)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (state.completedGroupIds.isEmpty()) "开始整卷练习" else "继续整卷练习")
                                }
                            }
                        }
                    }
                }

                if (state.sources.isNotEmpty()) {
                    item("sources_title") { Text("组卷来源", style = MaterialTheme.typography.titleMedium) }
                    items(state.sources, key = { "source_${it.id}" }) { source ->
                        Card(Modifier.fillMaxWidth().clickable { if (source.articleId > 0) onArticleClick(source.articleId) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(source.questionType.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(source.slotKey, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    when (source.status) {
                                        ExamPaperSourceStatus.COLLECTED -> "已收集"
                                        ExamPaperSourceStatus.GENERATING -> "生成中"
                                        ExamPaperSourceStatus.GENERATED -> "已生成"
                                        ExamPaperSourceStatus.FAILED -> "失败"
                                    },
                                    color = if (source.status == ExamPaperSourceStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                item("groups_title") {
                    Spacer(Modifier.height(4.dp))
                    Text("试卷题组", style = MaterialTheme.typography.titleMedium)
                }
                items(state.groups, key = { "group_${it.id}" }) { group ->
                    Card(Modifier.fillMaxWidth().clickable { onGroupClick(group.id) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${group.orderInPaper + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(group.sectionLabel ?: group.questionType.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(group.questionType.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (group.id in state.completedGroupIds) "已完成" else "查看",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (group.id in state.completedGroupIds) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
