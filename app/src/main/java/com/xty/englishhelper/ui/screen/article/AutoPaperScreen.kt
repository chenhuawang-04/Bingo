package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.ArticleAdvancedScoringTargets
import com.xty.englishhelper.domain.model.ExamPaperProfile
import com.xty.englishhelper.domain.model.ExamPaperSlotSelectionStatus
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect

@Composable
fun AutoPaperScreen(
    onBack: () -> Unit,
    onOpenPaper: (Long) -> Unit,
    viewModel: AutoPaperViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    AppTopBarEffect(
        title = { Text(stringResource(R.string.auto_paper_title)) },
        navigationIcon = { AppTopBarBackButton(onBack) }
    )
    LaunchedEffect(state.error) { /* 状态直接在页面展示，保留到用户下一次操作 */ }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(stringResource(R.string.auto_paper_profile), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExamPaperProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = state.profile == profile,
                        onClick = { viewModel.setProfile(profile) },
                        label = { Text(if (profile == ExamPaperProfile.ENGLISH_ONE) "英语一" else "英语二") }
                    )
                }
            }
        }
        item {
            Text(stringResource(R.string.auto_paper_special_type), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ArticleAdvancedScoringTargets.selectableSpecialTypes.forEach { type ->
                    FilterChip(
                        selected = state.specialType == type,
                        onClick = { viewModel.setSpecialType(type) },
                        label = { Text(type.displayName) }
                    )
                }
            }
        }
        item {
            Button(onClick = viewModel::start, enabled = !state.isStarting, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isStarting) stringResource(R.string.auto_paper_starting) else stringResource(R.string.auto_paper_start))
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
        state.paper?.let { paper ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOpenPaper(paper.id) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_paper_open_paper))
                    }
                    Button(onClick = viewModel::generateNow, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_paper_generate_now))
                    }
                }
            }
            items(state.slots, key = { it.slotKey }) { slot ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            ArticleAdvancedScoringTargets.targetFor(slot.questionType, slot.variant)?.displayName
                                ?: slot.questionType.displayName,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            when (slot.status) {
                                ExamPaperSlotSelectionStatus.PENDING -> "等待选择"
                                ExamPaperSlotSelectionStatus.SELECTING -> "AI 正在选择"
                                ExamPaperSlotSelectionStatus.SELECTED -> "已选择：${slot.articleTitle.orEmpty()}（${slot.selectedScore ?: 0} 分）"
                                ExamPaperSlotSelectionStatus.EMPTY -> "暂时空置"
                                ExamPaperSlotSelectionStatus.FAILED -> "选择失败"
                            }
                        )
                        slot.reason?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
