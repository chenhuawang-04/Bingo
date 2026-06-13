package com.xty.englishhelper.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiScopeConfig
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.OnlineReadingSource
import com.xty.englishhelper.domain.model.PoolRetryMode
import com.xty.englishhelper.domain.model.WordReferenceSource
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import com.xty.englishhelper.util.Constants

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
                title = { Text("设置") },
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
                ProviderListSection(state, viewModel)

                if (state.providerEditor.mode != ProviderEditorMode.NONE) {
                    ProviderEditorSection(state, viewModel)
                }

                HorizontalDivider()

                ImageCompressionSection(
                    enabled = state.imageCompressionEnabled,
                    targetBytes = state.imageCompressionTargetBytes,
                    onEnabledChange = viewModel::onImageCompressionEnabledChange,
                    onTargetBytesChange = viewModel::onImageCompressionTargetBytesChange
                )

                HorizontalDivider()

                BackgroundTaskSection(
                    concurrency = state.backgroundTaskConcurrency,
                    onConcurrencyChange = viewModel::onBackgroundTaskConcurrencyChange,
                    onManage = onBackgroundTasks
                )

                HorizontalDivider()

                ScopeConfigSection(state, viewModel)

                HorizontalDivider()

                WordOrganizeSection(
                    enabled = state.wordOrganizeHighQualityEnabled,
                    source = state.wordOrganizeReferenceSource,
                    onEnabledChange = viewModel::onWordOrganizeHighQualityEnabledChange,
                    onSourceChange = viewModel::onWordOrganizeReferenceSourceChange
                )

                HorizontalDivider()

                PoolSettingsSection(
                    windowSize = state.poolWindowSize,
                    maxConcurrent = state.poolMaxConcurrent,
                    requestsPerMinute = state.poolRequestsPerMinute,
                    retryMode = state.poolRetryMode,
                    managedMode = state.poolManagedMode,
                    onWindowSizeChange = viewModel::onPoolWindowSizeChange,
                    onMaxConcurrentChange = viewModel::onPoolMaxConcurrentChange,
                    onRequestsPerMinuteChange = viewModel::onPoolRequestsPerMinuteChange,
                    onRetryModeChange = viewModel::onPoolRetryModeChange,
                    onManagedModeChange = viewModel::onPoolManagedModeChange
                )

                HorizontalDivider()

                ModelAdvancedSection(state, viewModel)

                HorizontalDivider()

                OnlineReadingSection(
                    concurrency = state.guardianDetailConcurrency,
                    selectedSource = state.onlineReadingSource,
                    onSourceChange = viewModel::onOnlineReadingSourceChange,
                    onConcurrencyChange = viewModel::onGuardianDetailConcurrencyChange
                )

                HorizontalDivider()

                AutoScanSection(
                    rescoreAfterHours = state.scanRescoreAfterHours,
                    onRescoreAfterHoursChange = viewModel::onScanRescoreAfterHoursChange
                )

                HorizontalDivider()

                TtsSection(
                    rate = state.ttsRate,
                    pitch = state.ttsPitch,
                    locale = state.ttsLocale,
                    autoStudy = state.ttsAutoStudy,
                    prewarmConcurrency = state.ttsPrewarmConcurrency,
                    prewarmRetry = state.ttsPrewarmRetry,
                    onRateChange = viewModel::onTtsRateChange,
                    onPitchChange = viewModel::onTtsPitchChange,
                    onLocaleChange = viewModel::onTtsLocaleChange,
                    onAutoStudyChange = viewModel::onTtsAutoStudyChange,
                    onPrewarmConcurrencyChange = viewModel::onTtsPrewarmConcurrencyChange,
                    onPrewarmRetryChange = viewModel::onTtsPrewarmRetryChange,
                    onTest = viewModel::playTtsSample
                )

                HorizontalDivider()

                CloudSyncSection(
                    state = state.cloudSync,
                    onOwnerChange = viewModel::onGitHubOwnerChange,
                    onRepoChange = viewModel::onGitHubRepoChange,
                    onPatChange = viewModel::onGitHubPatChange,
                    onTestConnection = viewModel::testSyncConnection,
                    onSync = viewModel::performSync,
                    onForceUpload = viewModel::performForceUpload,
                    onForceDownload = viewModel::performForceDownload,
                    onClearError = viewModel::clearSyncError
                )

                HorizontalDivider()

                Button(
                    onClick = onTtsDiagnostics,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("语音诊断")
                }
            }
        }
    }
}

@Composable
private fun WordOrganizeSection(
    enabled: Boolean,
    source: WordReferenceSource,
    onEnabledChange: (Boolean) -> Unit,
    onSourceChange: (WordReferenceSource) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("单词整理增强", style = MaterialTheme.typography.titleMedium)
        Text(
            "高质量整理模式会先检索网络上的易混词、同义词、同根词和形近词摘要，再交给主模型整理。单个整理和后台批量整理都会生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("高质量整理模式", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "会增加耗时与 token 消耗，但能补充考研高频易混点。参考步骤不可用时会回退为常规整理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }

        Text(
            "参考来源",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = source == WordReferenceSource.FAST,
                onClick = { onSourceChange(WordReferenceSource.FAST) },
                label = { Text("快速模型") }
            )
            FilterChip(
                selected = source == WordReferenceSource.SEARCH,
                onClick = { onSourceChange(WordReferenceSource.SEARCH) },
                label = { Text("搜索模型") }
            )
        }
        Text(
            "默认使用快速模型；如果你切到搜索模型，会改用“搜索模型”作用域进行参考检索。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PoolSettingsSection(
    windowSize: Int,
    maxConcurrent: Int,
    requestsPerMinute: Int,
    retryMode: PoolRetryMode,
    managedMode: Boolean,
    onWindowSizeChange: (Int) -> Unit,
    onMaxConcurrentChange: (Int) -> Unit,
    onRequestsPerMinuteChange: (Int) -> Unit,
    onRetryModeChange: (PoolRetryMode) -> Unit,
    onManagedModeChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("词池整理", style = MaterialTheme.typography.titleMedium)
        Text(
            "质量优先模式的词池构建参数。窗口大小决定每个词与前面多少个词分批比较；并发和速率控制影响后台整理速度与 API 压力。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Retry mode
        Text("重试模式", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = retryMode == PoolRetryMode.AGGRESSIVE,
                onClick = { onRetryModeChange(PoolRetryMode.AGGRESSIVE) },
                label = { Text("积极") }
            )
            FilterChip(
                selected = retryMode == PoolRetryMode.LENIENT,
                onClick = { onRetryModeChange(PoolRetryMode.LENIENT) },
                label = { Text("宽松") }
            )
        }
        Text(
            text = when (retryMode) {
                PoolRetryMode.AGGRESSIVE ->
                    "积极：可重试错误短延迟后重试；遇到不可重试的错误立即中止整本构建。"
                PoolRetryMode.LENIENT ->
                    "宽松：任何失败都继续重试，仅在重试耗尽后才中止。重试间隔 = 当前词累计失败次数 × 10 秒（上限 2 分钟），失败越多等得越久，给限流/过载更多恢复时间。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "修改后从「下一个词」起生效（与并发设置一致）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // Managed mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("托管模式", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = managedMode, onCheckedChange = onManagedModeChange)
        }
        Text(
            "开启后，词池构建因重试失败而中止时，将在中止 10 分钟后自动续传，无上限（适合夜间无人值守）。仍可随时手动取消或填块。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // Window size
        Text("每批候选词数：$windowSize", style = MaterialTheme.typography.bodyMedium)
        Text(
            "每个词与前面的词比较时，每批发送给 AI 的候选词数量。例如设为50，则 AI 每次收到 1个目标词 + 50个候选词。越大单次 AI 调用越慢但覆盖更广。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = windowSize.toFloat(),
                onValueChange = { onWindowSizeChange(it.toInt()) },
                valueRange = 10f..200f,
                steps = 18,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$windowSize",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(36.dp)
            )
        }

        HorizontalDivider()

        // Max concurrent requests
        Text("最大并发请求数：$maxConcurrent", style = MaterialTheme.typography.bodyMedium)
        Text(
            "同时发起的 AI 请求数上限。增大可加速整理，但会占用更多网络和 API 配额。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = maxConcurrent.toFloat(),
                onValueChange = { onMaxConcurrentChange(it.toInt()) },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$maxConcurrent",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(36.dp)
            )
        }

        // Requests per minute
        Text("每分钟请求数上限：$requestsPerMinute", style = MaterialTheme.typography.bodyMedium)
        Text(
            "限制每分钟提交的 AI 请求数量，避免触发 API 速率限制。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = requestsPerMinute.toFloat(),
                onValueChange = { onRequestsPerMinuteChange(it.toInt()) },
                valueRange = 5f..120f,
                steps = 22,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$requestsPerMinute",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(36.dp)
            )
        }
    }
}

@Composable
private fun ProviderListSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("AI 提供商", style = MaterialTheme.typography.titleMedium)
                Text(
                    "管理不同的 AI 服务提供商，支持 OpenAI 兼容与 Anthropic 格式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = viewModel::startCreateProvider) {
                Icon(Icons.Default.Add, contentDescription = "新增提供商")
                Text("新增")
            }
        }

        if (state.providers.isEmpty()) {
            Text(
                "暂无提供商配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        state.providers.forEach { provider ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${providerLabel(provider.format)}  ·  ${provider.baseUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (provider.hasApiKey) "API Key 已配置" else "API Key 未配置",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (provider.hasApiKey) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (provider.name != state.defaultProviderName) {
                                    TextButton(onClick = { viewModel.setDefaultProvider(provider.name) }) {
                                        Text("设为默认")
                                    }
                                } else {
                                    Text("默认", style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(onClick = { viewModel.startEditProvider(provider.name) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                                }
                                IconButton(onClick = { viewModel.requestDeleteProvider(provider.name) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderEditorSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val editor = state.providerEditor
    val nameEditable = editor.mode == ProviderEditorMode.CREATE

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (editor.mode == ProviderEditorMode.CREATE) "新增提供商" else "编辑提供商",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = editor.name,
                onValueChange = viewModel::onProviderNameChange,
                enabled = nameEditable,
                label = { Text("唯一名称") },
                placeholder = { Text("例如: OpenAI 国内节点") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = editor.format == AiProvider.ANTHROPIC,
                    onClick = { viewModel.onProviderFormatChange(AiProvider.ANTHROPIC) },
                    label = { Text("Anthropic") }
                )
                FilterChip(
                    selected = editor.format == AiProvider.OPENAI_COMPATIBLE,
                    onClick = { viewModel.onProviderFormatChange(AiProvider.OPENAI_COMPATIBLE) },
                    label = { Text("OpenAI 兼容") }
                )
            }

            OutlinedTextField(
                value = editor.baseUrl,
                onValueChange = viewModel::onProviderBaseUrlChange,
                label = { Text("Base URL") },
                placeholder = { Text(defaultBaseUrlHint(editor.format)) },
                supportingText = { Text("本地服务可使用 http://IP:端口") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editor.apiKey,
                onValueChange = viewModel::onProviderApiKeyChange,
                label = { Text("API Key") },
                placeholder = { Text(apiKeyHint(editor.format)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::saveProvider,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
                TextButton(
                    onClick = viewModel::cancelProviderEditor,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::testProviderConnection,
                    enabled = !editor.isTesting && editor.apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (editor.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("  测试中")
                    } else {
                        Text("测试连接")
                    }
                }
                TextButton(
                    onClick = viewModel::fetchModelsForEditor,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "拉取模型列表")
                    Text("拉取模型")
                }
            }

            editor.testResult?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.contains("成功")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ScopeConfigSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("模型作用域", style = MaterialTheme.typography.titleMedium)
        Text(
            "为不同功能选择提供商与模型，支持手动输入模型或从接口拉取列表。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val scopeItems = listOf(
            ScopeItem(
                AiSettingsScope.MAIN, "主模型", "常规对话与主任务使用",
                details = "使用场景：\n" +
                    "· 单词 AI 整理（自动填充音标、释义、词根拆解、近义词等）\n" +
                    "· 文章段落 AI 翻译\n" +
                    "· 文章段落 AI 分析（语法点、关键词汇、句子拆解）\n" +
                    "· 题库 AI 答案生成与解析\n" +
                    "· 后台批量任务（单词整理、答案生成等）\n\n" +
                    "注意：出题功能已独立为「出题模型」作用域。"
            ),
            ScopeItem(
                AiSettingsScope.FAST, "快速模型", "朗读、词链与实时交互使用",
                details = "使用场景：\n" +
                    "· 文章适配度评分（评估是否适合考研出题，0-100 分）\n" +
                    "· 在线文章批量评分（后台任务）\n" +
                    "· 题库翻译评分（逐句对比参考译文）\n" +
                    "· 题库写作批改（五档评分 + 扣分明细）\n" +
                    "· 文章生词快速分析\n" +
                    "· 即时查词与词链高亮\n\n" +
                    "建议选用响应快、成本低的模型。"
            ),
            ScopeItem(
                AiSettingsScope.OCR, "OCR 模型", "OCR 识别与题库扫描",
                details = "使用场景：\n" +
                    "· 拍照批量导入单词（识别手写/印刷体）\n" +
                    "· 试卷扫描识别（题型、文章、题目、选项）\n" +
                    "· 答案页扫描提取\n" +
                    "· 文章 OCR 录入（拍照提取标题、正文）\n\n" +
                    "需要支持视觉/多模态输入的模型。"
            ),
            ScopeItem(
                AiSettingsScope.POOL, "词池整理", "词池整理与批量处理",
                details = "使用场景：\n" +
                    "· 质量优先模式词池构建（逐词 AI 精确比对）\n" +
                    "· 词池条目类型分类\n\n" +
                    "构建途中可在此处热切换模型，下一个请求即生效。"
            ),
            ScopeItem(
                AiSettingsScope.ARTICLE, "文章解析", "段落与语法解析",
                details = "使用场景：\n" +
                    "· 文章段落 AI 分析（中文翻译、语法点、关键词汇、句子拆解）\n\n" +
                    "与主模型的段落分析共用，但可独立配置以使用更适合长文理解的模型。"
            ),
            ScopeItem(
                AiSettingsScope.SEARCH, "搜索模型", "题库来源验证与搜索",
                details = "使用场景：\n" +
                    "· 阅读理解来源验证（联网搜索原文出处）\n" +
                    "· 范文检索（搜索模型范文与链接）\n" +
                    "· 单词整理增强模式的参考检索（当参考来源选'搜索模型'时）\n\n" +
                    "需要支持联网搜索/工具调用的模型。"
            ),
            ScopeItem(
                AiSettingsScope.REVIEWER, "词池审核", "审核与评分词池质量",
                details = "使用场景：\n" +
                    "· 词池质量审核（检查分组合理性）\n" +
                    "· 词池评分\n\n" +
                    "作为独立手动任务触发，不影响自动构建流程。"
            ),
            ScopeItem(
                AiSettingsScope.QUESTION_GENERATE, "出题模型", "文章出题与试卷生成",
                details = "使用场景：\n" +
                    "· 阅读理解出题（5 题 · 400-500 词）\n" +
                    "· 完形填空出题（20 空 · 280-360 词）\n" +
                    "· 翻译题出题（英语一/二）\n" +
                    "· 写作出题（小作文/大作文）\n" +
                    "· 新题型出题（段落排序/句子插入/匹配题）\n\n" +
                    "需要支持长文本理解和复杂推理的模型。"
            )
        )

        scopeItems.forEach { item ->
            val config = state.scopeConfigs[item.scope] ?: AiScopeConfig(
                providerName = state.defaultProviderName,
                model = defaultModelForFallback(state.defaultProviderName, state.providers)
            )
            ScopeConfigCard(
                item = item,
                config = config,
                providers = state.providers,
                modelOptions = state.modelOptions[config.providerName] ?: emptyList(),
                isLoadingModels = state.modelLoading.contains(config.providerName),
                modelError = state.modelError[config.providerName],
                onProviderChange = { viewModel.onScopeProviderChange(item.scope, it) },
                onModelChange = { viewModel.onScopeModelChange(item.scope, it) },
                onRefreshModels = { viewModel.fetchModelsForProvider(config.providerName) }
            )
        }
    }
}

@Composable
private fun ModelAdvancedSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("模型高级选项", style = MaterialTheme.typography.titleMedium)
        Text(
            "高级调试与解析选项，影响所有模型请求。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("调试模式", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "开启后每次 AI 请求都会弹窗展示 JSON。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.aiDebugMode,
                onCheckedChange = viewModel::onAiDebugModeChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("响应脱壳", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "当服务返回完整响应 JSON 时自动提取内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.aiResponseUnwrapEnabled,
                onCheckedChange = viewModel::onAiResponseUnwrapEnabledChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("修复 JSON", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "检测到未转义引号等错误时自动修复再解析。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.aiJsonRepairEnabled,
                onCheckedChange = viewModel::onAiJsonRepairEnabledChange
            )
        }
    }
}

@Composable
private fun ImageCompressionSection(
    enabled: Boolean,
    targetBytes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onTargetBytesChange: (Int) -> Unit
) {
    val targetKb = (targetBytes / 1024).coerceAtLeast(1)
    var input by remember { mutableStateOf(targetKb.toString()) }

    LaunchedEffect(targetBytes) {
        val current = targetKb.toString()
        if (input != current) {
            input = current
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("图片压缩", style = MaterialTheme.typography.titleMedium)
        Text(
            "自动压缩 OCR/扫描图片以减少请求体积，默认约 1MB。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("启用图片压缩", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "关闭后将上传原始图片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                val filtered = value.filter { it.isDigit() }
                input = filtered
                if (filtered.isBlank()) return@OutlinedTextField
                val kb = filtered.toIntOrNull() ?: return@OutlinedTextField
                val clampedKb = kb.coerceIn(200, 4096)
                if (clampedKb * 1024 != targetBytes) {
                    onTargetBytesChange(clampedKb * 1024)
                }
            },
            enabled = enabled,
            label = { Text("目标大小 (KB)") },
            placeholder = { Text("1024") },
            supportingText = { Text("建议 800-1500 KB") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BackgroundTaskSection(
    concurrency: Int,
    onConcurrencyChange: (Int) -> Unit,
    onManage: () -> Unit
) {
    var input by remember { mutableStateOf(concurrency.toString()) }

    LaunchedEffect(concurrency) {
        val current = concurrency.toString()
        if (input != current) {
            input = current
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("后台任务", style = MaterialTheme.typography.titleMedium)
        Text(
            "查看与管理后台整理、题库答案生成等任务，并控制后台并发。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                val filtered = value.filter { it.isDigit() }
                input = filtered
                if (filtered.isBlank()) {
                    onConcurrencyChange(2)
                    return@OutlinedTextField
                }
                val parsed = filtered.toIntOrNull() ?: return@OutlinedTextField
                val clamped = parsed.coerceIn(1, 6)
                if (clamped != concurrency) {
                    onConcurrencyChange(clamped)
                }
            },
            label = { Text("后台并发") },
            placeholder = { Text("2") },
            supportingText = { Text("建议 2-4，过高可能挤占 OCR 与朗读资源") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onManage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开后台任务管理")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeConfigCard(
    item: ScopeItem,
    config: AiScopeConfig,
    providers: List<ProviderSummary>,
    modelOptions: List<String>,
    isLoadingModels: Boolean,
    modelError: String?,
    onProviderChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onRefreshModels: () -> Unit
) {
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(item.title) },
            text = {
                Text(
                    text = item.details,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showDetailsDialog = true }) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "查看使用场景",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = it }
            ) {
                OutlinedTextField(
                    value = config.providerName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("提供商") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false }
                ) {
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.name) },
                            onClick = {
                                onProviderChange(provider.name)
                                providerExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = it }
            ) {
                OutlinedTextField(
                    value = config.model,
                    onValueChange = {
                        onModelChange(it)
                        modelExpanded = true
                    },
                    label = { Text("模型") },
                    placeholder = { Text("手动输入或选择模型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                )
                if (modelOptions.isNotEmpty()) {
                    val filtered = modelOptions.filter { it.contains(config.model, ignoreCase = true) }
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        filtered.forEach { modelId ->
                            DropdownMenuItem(
                                text = { Text(modelId) },
                                onClick = {
                                    onModelChange(modelId)
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (modelError != null) {
                    Text(
                        text = modelError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = if (isLoadingModels) "模型列表加载中..." else "模型列表可手动刷新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefreshModels) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新模型")
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
    val scopesText = if (pending.affectedScopes.isEmpty()) {
        "该提供商未被任何作用域使用。"
    } else {
        "受影响的作用域: " + pending.affectedScopes.joinToString("、") { scopeLabel(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除提供商") },
        text = { Text("删除后会自动回退到默认提供商。$scopesText") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineReadingSection(
    concurrency: Int,
    selectedSource: OnlineReadingSource,
    onSourceChange: (OnlineReadingSource) -> Unit,
    onConcurrencyChange: (Int) -> Unit
) {
    var input by remember { mutableStateOf(concurrency.toString()) }

    LaunchedEffect(concurrency) {
        val current = concurrency.toString()
        if (input != current) {
            input = current
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("在线阅读", style = MaterialTheme.typography.titleMedium)
        Text(
            "设置默认阅读源与在线文章详情并发加载数量。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnlineReadingSource.values().forEach { source ->
                FilterChip(
                    selected = source == selectedSource,
                    onClick = { onSourceChange(source) },
                    label = { Text(source.label) }
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                val filtered = value.filter { it.isDigit() }
                input = filtered
                if (filtered.isBlank()) {
                    onConcurrencyChange(5)
                    return@OutlinedTextField
                }
                val parsed = filtered.toIntOrNull() ?: return@OutlinedTextField
                val clamped = parsed.coerceIn(1, 10)
                if (clamped != concurrency) {
                    onConcurrencyChange(clamped)
                }
            },
            label = { Text("详情并发") },
            placeholder = { Text("5") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AutoScanSection(
    rescoreAfterHours: Int,
    onRescoreAfterHoursChange: (Int) -> Unit
) {
    var input by remember { mutableStateOf(rescoreAfterHours.toString()) }

    LaunchedEffect(rescoreAfterHours) {
        val current = rescoreAfterHours.toString()
        if (input != current) {
            input = current
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("在线文章自动评分", style = MaterialTheme.typography.titleMedium)
        Text(
            "扫描在线文章源（Guardian/The Atlantic/CS Monitor），自动评估是否适合考研出题。已评分且未过期的文章会被跳过。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                val filtered = value.filter { it.isDigit() }
                input = filtered
                if (filtered.isBlank()) {
                    onRescoreAfterHoursChange(24)
                    return@OutlinedTextField
                }
                val parsed = filtered.toIntOrNull() ?: return@OutlinedTextField
                val clamped = parsed.coerceIn(1, 720)
                if (clamped != rescoreAfterHours) {
                    onRescoreAfterHoursChange(clamped)
                }
            },
            label = { Text("重评间隔（小时）") },
            placeholder = { Text("24") },
            supportingText = { Text("已评分文章在此间隔内不会重复评分，建议 24-168") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TtsSection(
    rate: Float,
    pitch: Float,
    locale: String,
    autoStudy: Boolean,
    prewarmConcurrency: Int,
    prewarmRetry: Int,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onLocaleChange: (String) -> Unit,
    onAutoStudyChange: (Boolean) -> Unit,
    onPrewarmConcurrencyChange: (Int) -> Unit,
    onPrewarmRetryChange: (Int) -> Unit,
    onTest: () -> Unit
) {
    val locales = listOf(
        "system" to "跟随系统",
        "en-US" to "英语(美式)",
        "en-GB" to "英语(英式)"
    )

    var prewarmConcurrencyInput by remember { mutableStateOf(prewarmConcurrency.toString()) }
    var prewarmRetryInput by remember { mutableStateOf(prewarmRetry.toString()) }

    LaunchedEffect(prewarmConcurrency) {
        val current = prewarmConcurrency.toString()
        if (prewarmConcurrencyInput != current) {
            prewarmConcurrencyInput = current
        }
    }
    LaunchedEffect(prewarmRetry) {
        val current = prewarmRetry.toString()
        if (prewarmRetryInput != current) {
            prewarmRetryInput = current
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("语音播报", style = MaterialTheme.typography.titleMedium)
        Text(
            "使用系统 TTS 朗读单词与文章，可调整语速与音调。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("语速: ${"%.2f".format(rate)}x", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = rate,
            onValueChange = onRateChange,
            valueRange = 0.5f..2.0f
        )

        Text("音调: ${"%.2f".format(pitch)}x", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = pitch,
            onValueChange = onPitchChange,
            valueRange = 0.5f..2.0f
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            locales.forEach { (value, label) ->
                FilterChip(
                    selected = locale == value,
                    onClick = { onLocaleChange(value) },
                    label = { Text(label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("背词自动朗读", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "每弹出一个单词自动播放发音。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoStudy,
                onCheckedChange = onAutoStudyChange
            )
        }

        HorizontalDivider()

        Text("TTS 预热", style = MaterialTheme.typography.titleSmall)
        Text(
            "控制段落缓存的并发与重试次数。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = prewarmConcurrencyInput,
            onValueChange = { value ->
                val filtered = value.filter { it.isDigit() }
                prewarmConcurrencyInput = filtered
                if (filtered.isBlank()) {
                    onPrewarmConcurrencyChange(2)
                    return@OutlinedTextField
                }
                val parsed = filtered.toIntOrNull() ?: return@OutlinedTextField
                val clamped = parsed.coerceIn(1, 6)
                if (clamped != prewarmConcurrency) {
                    onPrewarmConcurrencyChange(clamped)
                }
            },
            label = { Text("预热并发") },
            placeholder = { Text("2") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = prewarmRetryInput,
            onValueChange = { value ->
                val filtered = value.filter { it.isDigit() }
                prewarmRetryInput = filtered
                if (filtered.isBlank()) {
                    onPrewarmRetryChange(2)
                    return@OutlinedTextField
                }
                val parsed = filtered.toIntOrNull() ?: return@OutlinedTextField
                val clamped = parsed.coerceIn(0, 5)
                if (clamped != prewarmRetry) {
                    onPrewarmRetryChange(clamped)
                }
            },
            label = { Text("预热重试") },
            placeholder = { Text("2") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onTest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("播放示例")
        }
    }
}

private data class ScopeItem(
    val scope: AiSettingsScope,
    val title: String,
    val description: String,
    val details: String
)

private fun providerLabel(provider: AiProvider): String {
    return when (provider) {
        AiProvider.ANTHROPIC -> "Anthropic"
        AiProvider.OPENAI_COMPATIBLE -> "OpenAI 兼容"
    }
}

private fun apiKeyHint(provider: AiProvider): String {
    return when (provider) {
        AiProvider.ANTHROPIC -> "sk-ant-..."
        AiProvider.OPENAI_COMPATIBLE -> "sk-..."
    }
}

private fun defaultBaseUrlHint(provider: AiProvider): String {
    return when (provider) {
        AiProvider.ANTHROPIC -> Constants.ANTHROPIC_BASE_URL
        AiProvider.OPENAI_COMPATIBLE -> Constants.OPENAI_BASE_URL
    }
}

private fun defaultModelForFallback(providerName: String, providers: List<ProviderSummary>): String {
    val provider = providers.firstOrNull { it.name == providerName }
    return when (provider?.format) {
        AiProvider.OPENAI_COMPATIBLE -> Constants.DEFAULT_OPENAI_MODEL
        else -> Constants.DEFAULT_MODEL
    }
}

private fun scopeLabel(scope: AiSettingsScope): String {
    return when (scope) {
        AiSettingsScope.MAIN -> "主模型"
        AiSettingsScope.FAST -> "快速模型"
        AiSettingsScope.OCR -> "OCR"
        AiSettingsScope.POOL -> "词池整理"
        AiSettingsScope.ARTICLE -> "文章解析"
        AiSettingsScope.SEARCH -> "搜索"
        AiSettingsScope.REVIEWER -> "词池审核"
        AiSettingsScope.QUESTION_GENERATE -> "出题模型"
    }
}
