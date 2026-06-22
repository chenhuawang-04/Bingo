package com.xty.englishhelper.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing
import com.xty.englishhelper.ui.screen.settings.components.SettingsSectionCard
import com.xty.englishhelper.ui.screen.settings.sections.AiSettingsSection
import com.xty.englishhelper.ui.screen.settings.sections.ArticleSettingsSection
import com.xty.englishhelper.ui.screen.settings.sections.StudySettingsSection
import com.xty.englishhelper.ui.screen.settings.sections.SystemSettingsSection

/**
 * 设置主屏幕 v2
 *
 * 重构后的设置页面，采用可折叠区块 + 搜索功能
 * - 4 个功能区块：AI 设置 / 学习功能 / 文章功能 / 系统与同步
 * - 顶部搜索框过滤区块
 * - 所有区块默认折叠
 * - 底部操作按钮：后台任务管理 / 语音诊断
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTtsDiagnostics: () -> Unit,
    onBackgroundTasks: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val spacing = LocalEhSpacing.current

    // 搜索状态
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.message, state.error) {
        (state.message ?: state.error)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    state.pendingDelete?.let { pending ->
        DeleteProviderDialog(
            pending = pending,
            onConfirm = viewModel::confirmDeleteProvider,
            onDismiss = viewModel::dismissDeleteProvider
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
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
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索设置项...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true
                )

                // AI 设置区块
                SettingsSectionCard(
                    icon = Icons.Default.SmartToy,
                    title = "AI 设置",
                    subtitle = "提供商、作用域、模型参数",
                    searchQuery = searchQuery
                ) {
                    AiSettingsSection(state, viewModel)
                }

                // 学习功能区块
                SettingsSectionCard(
                    icon = Icons.Default.School,
                    title = "学习功能",
                    subtitle = "词条整理、词池、头脑风暴",
                    searchQuery = searchQuery
                ) {
                    StudySettingsSection(state, viewModel)
                }

                // 文章功能区块
                SettingsSectionCard(
                    icon = Icons.Default.Article,
                    title = "文章功能",
                    subtitle = "在线阅读、自动扫描、图片压缩",
                    searchQuery = searchQuery
                ) {
                    ArticleSettingsSection(state, viewModel)
                }

                // 系统与同步区块
                SettingsSectionCard(
                    icon = Icons.Default.Settings,
                    title = "系统与同步",
                    subtitle = "语言、后台任务、云同步、语音合成",
                    searchQuery = searchQuery
                ) {
                    SystemSettingsSection(state, viewModel, onTtsDiagnostics)
                }

                // 底部操作按钮
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    OutlinedButton(
                        onClick = onBackgroundTasks,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Task,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(spacing.xs))
                        Text("后台任务管理")
                    }

                    OutlinedButton(
                        onClick = onTtsDiagnostics,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(spacing.xs))
                        Text("语音诊断")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteProviderDialog(
    pending: PendingDeleteProvider,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_delete_provider_title)) },
        text = { Text(stringResource(R.string.settings_delete_provider_confirm, pending.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
