package com.xty.englishhelper.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer
import com.xty.englishhelper.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var modelExpanded by remember { mutableStateOf(false) }
    var fastModelExpanded by remember { mutableStateOf(false) }

    val availableModels = when (state.provider) {
        AiProvider.ANTHROPIC -> Constants.ANTHROPIC_AVAILABLE_MODELS
        AiProvider.OPENAI_COMPATIBLE -> Constants.OPENAI_AVAILABLE_MODELS
    }

    val sectionTitle = when (state.provider) {
        AiProvider.ANTHROPIC -> "Anthropic API"
        AiProvider.OPENAI_COMPATIBLE -> "OpenAI Compatible API"
    }

    val baseUrlPlaceholder = when (state.provider) {
        AiProvider.ANTHROPIC -> "https://api.anthropic.com/"
        AiProvider.OPENAI_COMPATIBLE -> "https://api.openai.com/"
    }

    val apiKeyPlaceholder = when (state.provider) {
        AiProvider.ANTHROPIC -> "sk-ant-..."
        AiProvider.OPENAI_COMPATIBLE -> "sk-..."
    }

    LaunchedEffect(state.testResult, state.error) {
        (state.testResult ?: state.error)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
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
            maxWidth = 560.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Provider selection
                Text("AI 服务提供商", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.provider == AiProvider.ANTHROPIC,
                        onClick = { viewModel.onProviderChange(AiProvider.ANTHROPIC) },
                        label = { Text("Anthropic") }
                    )
                    FilterChip(
                        selected = state.provider == AiProvider.OPENAI_COMPATIBLE,
                        onClick = { viewModel.onProviderChange(AiProvider.OPENAI_COMPATIBLE) },
                        label = { Text("OpenAI 兼容") }
                    )
                }

                Text(sectionTitle, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::onBaseUrlChange,
                    label = { Text("Base URL") },
                    placeholder = { Text(baseUrlPlaceholder) },
                    supportingText = { Text("本地服务使用 http://IP:端口") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = { Text("API Key") },
                    placeholder = { Text(apiKeyPlaceholder) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val filteredModels = availableModels.filter { (modelId, modelName) ->
                    state.selectedModel.isBlank() ||
                        modelId.contains(state.selectedModel, ignoreCase = true) ||
                        modelName.contains(state.selectedModel, ignoreCase = true)
                }
                val filteredFastModels = availableModels.filter { (modelId, modelName) ->
                    state.fastModel.isBlank() ||
                        modelId.contains(state.fastModel, ignoreCase = true) ||
                        modelName.contains(state.fastModel, ignoreCase = true)
                }

                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.selectedModel,
                        onValueChange = { value ->
                            viewModel.onModelChange(value)
                            modelExpanded = true
                        },
                        label = { Text("模型") },
                        placeholder = { Text("选择或输入模型名称") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                    )
                    if (filteredModels.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            filteredModels.forEach { (modelId, modelName) ->
                                DropdownMenuItem(
                                    text = { Text("$modelName ($modelId)") },
                                    onClick = {
                                        viewModel.onModelChange(modelId)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = fastModelExpanded,
                    onExpandedChange = { fastModelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = state.fastModel,
                        onValueChange = { value ->
                            viewModel.onFastModelChange(value)
                            fastModelExpanded = true
                        },
                        label = { Text("快速模型") },
                        placeholder = { Text("选择或输入模型名称") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fastModelExpanded) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                    )
                    if (filteredFastModels.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = fastModelExpanded,
                            onDismissRequest = { fastModelExpanded = false }
                        ) {
                            filteredFastModels.forEach { (modelId, modelName) ->
                                DropdownMenuItem(
                                    text = { Text("$modelName ($modelId)") },
                                    onClick = {
                                        viewModel.onFastModelChange(modelId)
                                        fastModelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = viewModel::testConnection,
                    enabled = !state.isTesting && state.apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("  正在测试…")
                    } else {
                        Text("测试连接")
                    }
                }

                HorizontalDivider()

                // Pool AI settings
                ScopedAiSettingsSection(
                    title = "词池 AI",
                    description = "为词池生成配置独立的 AI 模型（可使用更便宜的模型）",
                    scope = AiSettingsScope.POOL,
                    scoped = state.poolAiSettings,
                    viewModel = viewModel
                )

                HorizontalDivider()

                // OCR AI settings
                ScopedAiSettingsSection(
                    title = "OCR AI",
                    description = "为文章 OCR 配置独立的 AI 模型（需支持多模态）",
                    scope = AiSettingsScope.OCR,
                    scoped = state.ocrAiSettings,
                    viewModel = viewModel
                )

                HorizontalDivider()

                // 在线阅读
                OnlineReadingSection(
                    concurrency = state.guardianDetailConcurrency,
                    onConcurrencyChange = viewModel::onGuardianDetailConcurrencyChange
                )

                HorizontalDivider()

                // Cloud Sync
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineReadingSection(
    concurrency: Int,
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
            "设置详情并发加载数量（1-10）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopedAiSettingsSection(
    title: String,
    description: String,
    scope: AiSettingsScope,
    scoped: ScopedAiSettingsState,
    viewModel: SettingsViewModel
) {
    var modelExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = scoped.enabled,
                onCheckedChange = { viewModel.onScopedToggle(scope, it) }
            )
        }

        if (scoped.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = scoped.provider == AiProvider.ANTHROPIC,
                    onClick = { viewModel.onScopedProviderChange(scope, AiProvider.ANTHROPIC) },
                    label = { Text("Anthropic") }
                )
                FilterChip(
                    selected = scoped.provider == AiProvider.OPENAI_COMPATIBLE,
                    onClick = { viewModel.onScopedProviderChange(scope, AiProvider.OPENAI_COMPATIBLE) },
                    label = { Text("OpenAI 兼容") }
                )
            }

            val baseUrlPlaceholder = when (scoped.provider) {
                AiProvider.ANTHROPIC -> "https://api.anthropic.com/"
                AiProvider.OPENAI_COMPATIBLE -> "https://api.openai.com/"
            }
            val apiKeyPlaceholder = when (scoped.provider) {
                AiProvider.ANTHROPIC -> "sk-ant-..."
                AiProvider.OPENAI_COMPATIBLE -> "sk-..."
            }

            OutlinedTextField(
                value = scoped.baseUrl,
                onValueChange = { viewModel.onScopedBaseUrlChange(scope, it) },
                label = { Text("Base URL") },
                placeholder = { Text(baseUrlPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = scoped.apiKey,
                onValueChange = { viewModel.onScopedApiKeyChange(scope, it) },
                label = { Text("API Key") },
                placeholder = { Text(apiKeyPlaceholder) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            val availableModels = when (scoped.provider) {
                AiProvider.ANTHROPIC -> Constants.ANTHROPIC_AVAILABLE_MODELS
                AiProvider.OPENAI_COMPATIBLE -> Constants.OPENAI_AVAILABLE_MODELS
            }
            val filteredModels = availableModels.filter { (modelId, modelName) ->
                scoped.selectedModel.isBlank() ||
                    modelId.contains(scoped.selectedModel, ignoreCase = true) ||
                    modelName.contains(scoped.selectedModel, ignoreCase = true)
            }

            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = it }
            ) {
                OutlinedTextField(
                    value = scoped.selectedModel,
                    onValueChange = { value ->
                        viewModel.onScopedModelChange(scope, value)
                        modelExpanded = true
                    },
                    label = { Text("模型") },
                    placeholder = { Text("选择或输入模型名称") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                )
                if (filteredModels.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        filteredModels.forEach { (modelId, modelName) ->
                            DropdownMenuItem(
                                text = { Text("$modelName ($modelId)") },
                                onClick = {
                                    viewModel.onScopedModelChange(scope, modelId)
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.testScopedConnection(scope) },
                enabled = !scoped.isTesting && scoped.apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (scoped.isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("  正在测试…")
                } else {
                    Text("测试连接")
                }
            }

            if (scoped.testResult != null) {
                Text(
                    text = scoped.testResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scoped.testResult.contains("成功")) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
            }
        } else {
            Text(
                "未启用，使用主设置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
