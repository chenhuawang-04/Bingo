@file:OptIn(ExperimentalMaterial3Api::class)

package com.xty.englishhelper.ui.screen.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiScopeConfig
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing
import com.xty.englishhelper.ui.screen.settings.ProviderEditorMode
import com.xty.englishhelper.ui.screen.settings.ProviderSummary
import com.xty.englishhelper.ui.screen.settings.SettingsUiState
import com.xty.englishhelper.ui.screen.settings.SettingsViewModel
import com.xty.englishhelper.ui.screen.settings.components.SettingsSwitchRow
import com.xty.englishhelper.util.Constants

/**
 * AI 设置区块
 * 包含：提供商列表、提供商编辑器、作用域配置、模型高级设置
 */
@Composable
internal fun AiSettingsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val spacing = LocalEhSpacing.current

    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        // 1. 提供商列表
        Card(
            shape = ArticleShapes.Section,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_ai_providers),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.settings_provider_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = viewModel::startCreateProvider) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_provider_cd))
                        Text(stringResource(R.string.settings_add_provider))
                    }
                }

                if (state.providers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_providers),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.providers.forEach { provider ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(spacing.md),
                                verticalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(provider.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${stringResource(providerLabel(provider.format))}  ·  ${provider.baseUrl}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (provider.hasApiKey) stringResource(R.string.settings_api_key_configured)
                                                   else stringResource(R.string.settings_api_key_not_configured),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (provider.hasApiKey) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                            if (provider.name != state.defaultProviderName) {
                                                TextButton(onClick = { viewModel.setDefaultProvider(provider.name) }) {
                                                    Text(stringResource(R.string.settings_set_default))
                                                }
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.settings_default_label),
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                            IconButton(onClick = { viewModel.startEditProvider(provider.name) }) {
                                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit))
                                            }
                                            IconButton(onClick = { viewModel.requestDeleteProvider(provider.name) }) {
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 提供商编辑器（条件显示）
        if (state.providerEditor.mode != ProviderEditorMode.NONE) {
            ProviderEditorCard(state, viewModel)
        }

        // 3. 作用域配置
        ScopeConfigSection(state, viewModel)

        // 4. 模型高级设置
        Card(
            shape = ArticleShapes.Section,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.settings_model_advanced),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_model_advanced_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_debug_mode),
                    description = stringResource(R.string.settings_debug_mode_desc),
                    checked = state.aiDebugMode,
                    onCheckedChange = viewModel::onAiDebugModeChange
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_response_unwrap),
                    description = stringResource(R.string.settings_response_unwrap_desc),
                    checked = state.aiResponseUnwrapEnabled,
                    onCheckedChange = viewModel::onAiResponseUnwrapEnabledChange
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_json_repair),
                    description = stringResource(R.string.settings_json_repair_desc),
                    checked = state.aiJsonRepairEnabled,
                    onCheckedChange = viewModel::onAiJsonRepairEnabledChange
                )
            }
        }
    }
}

@Composable
private fun ProviderEditorCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val editor = state.providerEditor
    val nameEditable = editor.mode == ProviderEditorMode.CREATE
    val spacing = LocalEhSpacing.current

    Card(
        shape = ArticleShapes.Section,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = if (editor.mode == ProviderEditorMode.CREATE)
                          stringResource(R.string.settings_create_provider)
                       else stringResource(R.string.settings_edit_provider),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = editor.name,
                onValueChange = viewModel::onProviderNameChange,
                enabled = nameEditable,
                label = { Text(stringResource(R.string.settings_provider_name)) },
                placeholder = { Text(stringResource(R.string.settings_provider_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                FilterChip(
                    selected = editor.format == AiProvider.ANTHROPIC,
                    onClick = { viewModel.onProviderFormatChange(AiProvider.ANTHROPIC) },
                    label = { Text("Anthropic") }
                )
                FilterChip(
                    selected = editor.format == AiProvider.OPENAI_COMPATIBLE,
                    onClick = { viewModel.onProviderFormatChange(AiProvider.OPENAI_COMPATIBLE) },
                    label = { Text(stringResource(R.string.settings_openai_compatible)) }
                )
            }

            OutlinedTextField(
                value = editor.baseUrl,
                onValueChange = viewModel::onProviderBaseUrlChange,
                label = { Text(stringResource(R.string.settings_base_url)) },
                placeholder = { Text(defaultBaseUrlHint(editor.format)) },
                supportingText = { Text(stringResource(R.string.settings_base_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editor.apiKey,
                onValueChange = viewModel::onProviderApiKeyChange,
                label = { Text(stringResource(R.string.settings_api_key)) },
                placeholder = { Text(apiKeyHint(editor.format)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Button(
                    onClick = viewModel::saveProvider,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.common_save))
                }
                TextButton(
                    onClick = viewModel::cancelProviderEditor,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
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
                        Text("  ${stringResource(R.string.settings_testing)}")
                    } else {
                        Text(stringResource(R.string.settings_test_connection))
                    }
                }
                TextButton(
                    onClick = viewModel::fetchModelsForEditor,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.settings_refresh_models_cd))
                    Text(stringResource(R.string.settings_fetch_models))
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
    val spacing = LocalEhSpacing.current

    Card(
        shape = ArticleShapes.Section,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.settings_model_scopes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.settings_model_scopes_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val scopeItems = listOf(
                ScopeItem(
                    AiSettingsScope.MAIN,
                    stringResource(R.string.settings_scope_main),
                    stringResource(R.string.settings_scope_main_desc),
                    stringResource(R.string.scope_main_details)
                ),
                ScopeItem(
                    AiSettingsScope.FAST,
                    stringResource(R.string.settings_scope_fast),
                    stringResource(R.string.settings_scope_fast_desc),
                    stringResource(R.string.scope_fast_details)
                ),
                ScopeItem(
                    AiSettingsScope.OCR,
                    stringResource(R.string.settings_scope_ocr),
                    stringResource(R.string.settings_scope_ocr_desc),
                    stringResource(R.string.scope_ocr_details)
                ),
                ScopeItem(
                    AiSettingsScope.POOL,
                    stringResource(R.string.settings_scope_pool),
                    stringResource(R.string.settings_scope_pool_desc),
                    stringResource(R.string.scope_pool_details)
                ),
                ScopeItem(
                    AiSettingsScope.ARTICLE,
                    stringResource(R.string.settings_scope_article),
                    stringResource(R.string.settings_scope_article_desc),
                    stringResource(R.string.scope_article_details)
                ),
                ScopeItem(
                    AiSettingsScope.SEARCH,
                    stringResource(R.string.settings_scope_search),
                    stringResource(R.string.settings_scope_search_desc),
                    stringResource(R.string.scope_search_details)
                ),
                ScopeItem(
                    AiSettingsScope.REVIEWER,
                    stringResource(R.string.settings_scope_reviewer),
                    stringResource(R.string.settings_scope_reviewer_desc),
                    stringResource(R.string.scope_reviewer_details)
                ),
                ScopeItem(
                    AiSettingsScope.QUESTION_GENERATE,
                    stringResource(R.string.settings_scope_question_generate),
                    stringResource(R.string.settings_scope_question_generate_desc),
                    stringResource(R.string.scope_question_generate_details)
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
}

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
    val spacing = LocalEhSpacing.current

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
                    Text(stringResource(R.string.settings_got_it))
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showDetailsDialog = true }) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.settings_view_scenarios_cd),
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
                    label = { Text(stringResource(R.string.settings_provider_label)) },
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
                    label = { Text(stringResource(R.string.settings_model_label)) },
                    placeholder = { Text(stringResource(R.string.settings_model_input_hint)) },
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
                        text = if (isLoadingModels) stringResource(R.string.settings_models_loading)
                               else stringResource(R.string.settings_models_manual_refresh),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefreshModels) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
                    }
                }
            }
        }
    }
}

// 辅助数据类
private data class ScopeItem(
    val scope: AiSettingsScope,
    val title: String,
    val description: String,
    val details: String
)

// 辅助函数
private fun providerLabel(provider: AiProvider): Int {
    return when (provider) {
        AiProvider.ANTHROPIC -> R.string.settings_anthropic
        AiProvider.OPENAI_COMPATIBLE -> R.string.settings_openai_compatible
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
