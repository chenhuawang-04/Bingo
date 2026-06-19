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
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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

                BrainstormSettingsSection(
                    clusterSize = state.brainstormClusterSize,
                    qualityMinConfidence = state.brainstormQualityMinConfidence,
                    activeRecall = state.brainstormActiveRecall,
                    onClusterSizeChange = viewModel::onBrainstormClusterSizeChange,
                    onQualityMinConfidenceChange = viewModel::onBrainstormQualityMinConfidenceChange,
                    onActiveRecallChange = viewModel::onBrainstormActiveRecallChange
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

                LocaleSection(
                    currentLocale = state.appLocale,
                    onLocaleChange = viewModel::onLocaleChange
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
                    Text(stringResource(R.string.settings_voice_diagnostics))
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
        Text(stringResource(R.string.settings_word_organize_enhance), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_word_organize_enhance_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_high_quality_mode), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_high_quality_desc),
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
            stringResource(R.string.settings_reference_source),
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = source == WordReferenceSource.FAST,
                onClick = { onSourceChange(WordReferenceSource.FAST) },
                label = { Text(stringResource(R.string.settings_fast_model)) }
            )
            FilterChip(
                selected = source == WordReferenceSource.SEARCH,
                onClick = { onSourceChange(WordReferenceSource.SEARCH) },
                label = { Text(stringResource(R.string.settings_search_model)) }
            )
        }
        Text(
            stringResource(R.string.settings_reference_source_desc),
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
        Text(stringResource(R.string.settings_pool_organize), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_pool_organize_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Retry mode
        Text(stringResource(R.string.settings_retry_mode), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = retryMode == PoolRetryMode.AGGRESSIVE,
                onClick = { onRetryModeChange(PoolRetryMode.AGGRESSIVE) },
                label = { Text(stringResource(R.string.settings_retry_aggressive)) }
            )
            FilterChip(
                selected = retryMode == PoolRetryMode.LENIENT,
                onClick = { onRetryModeChange(PoolRetryMode.LENIENT) },
                label = { Text(stringResource(R.string.settings_retry_lenient)) }
            )
        }
        Text(
            text = when (retryMode) {
                PoolRetryMode.AGGRESSIVE ->
                    stringResource(R.string.settings_retry_aggressive_desc)
                PoolRetryMode.LENIENT ->
                    stringResource(R.string.settings_retry_lenient_desc)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.settings_retry_desc),
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
            Text(stringResource(R.string.settings_managed_mode), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = managedMode, onCheckedChange = onManagedModeChange)
        }
        Text(
            stringResource(R.string.settings_managed_mode_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // Window size
        Text(stringResource(R.string.settings_window_size, windowSize), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_window_size_desc),
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
        Text(stringResource(R.string.settings_max_concurrent, maxConcurrent), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_max_concurrent_desc),
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
        Text(stringResource(R.string.settings_rpm_limit, requestsPerMinute), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_rpm_limit_desc),
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
private fun BrainstormSettingsSection(
    clusterSize: Int,
    qualityMinConfidence: Float,
    activeRecall: Boolean,
    onClusterSizeChange: (Int) -> Unit,
    onQualityMinConfidenceChange: (Float) -> Unit,
    onActiveRecallChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_brainstorm), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_brainstorm_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Cluster size
        Text(stringResource(R.string.settings_cluster_size, clusterSize), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_cluster_size_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = clusterSize.toFloat(),
                onValueChange = { onClusterSizeChange(it.toInt()) },
                valueRange = 2f..12f,
                steps = 9,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$clusterSize",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(36.dp)
            )
        }

        HorizontalDivider()

        // Quality threshold
        Text(
            stringResource(R.string.settings_quality_threshold, String.format("%.2f", qualityMinConfidence)),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            stringResource(R.string.settings_quality_threshold_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = qualityMinConfidence,
                onValueChange = onQualityMinConfidenceChange,
                valueRange = 0f..0.9f,
                steps = 17,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format("%.2f", qualityMinConfidence),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(40.dp)
            )
        }

        HorizontalDivider()

        // Active recall (opt-in)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.settings_active_recall), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = activeRecall, onCheckedChange = onActiveRecallChange)
        }
        Text(
            stringResource(R.string.settings_active_recall_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                Text(stringResource(R.string.settings_ai_providers), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_provider_desc),
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
                stringResource(R.string.settings_no_providers),
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
                                "${stringResource(providerLabel(provider.format))}  ·  ${provider.baseUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (provider.hasApiKey) stringResource(R.string.settings_api_key_configured) else stringResource(R.string.settings_api_key_not_configured),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (provider.hasApiKey) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (provider.name != state.defaultProviderName) {
                                    TextButton(onClick = { viewModel.setDefaultProvider(provider.name) }) {
                                        Text(stringResource(R.string.settings_set_default))
                                    }
                                } else {
                                    Text(stringResource(R.string.settings_default_label), style = MaterialTheme.typography.labelMedium)
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
                if (editor.mode == ProviderEditorMode.CREATE) stringResource(R.string.settings_create_provider) else stringResource(R.string.settings_edit_provider),
                style = MaterialTheme.typography.titleMedium
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_model_scopes), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_model_scopes_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val scopeItems = listOf(
            ScopeItem(
                AiSettingsScope.MAIN, stringResource(R.string.settings_scope_main), stringResource(R.string.settings_scope_main_desc),
                details = stringResource(R.string.scope_main_details)
            ),
            ScopeItem(
                AiSettingsScope.FAST, stringResource(R.string.settings_scope_fast), stringResource(R.string.settings_scope_fast_desc),
                details = stringResource(R.string.scope_fast_details)
            ),
            ScopeItem(
                AiSettingsScope.OCR, stringResource(R.string.settings_scope_ocr), stringResource(R.string.settings_scope_ocr_desc),
                details = stringResource(R.string.scope_ocr_details)
            ),
            ScopeItem(
                AiSettingsScope.POOL, stringResource(R.string.settings_scope_pool), stringResource(R.string.settings_scope_pool_desc),
                details = stringResource(R.string.scope_pool_details)
            ),
            ScopeItem(
                AiSettingsScope.ARTICLE, stringResource(R.string.settings_scope_article), stringResource(R.string.settings_scope_article_desc),
                details = stringResource(R.string.scope_article_details)
            ),
            ScopeItem(
                AiSettingsScope.SEARCH, stringResource(R.string.settings_scope_search), stringResource(R.string.settings_scope_search_desc),
                details = stringResource(R.string.scope_search_details)
            ),
            ScopeItem(
                AiSettingsScope.REVIEWER, stringResource(R.string.settings_scope_reviewer), stringResource(R.string.settings_scope_reviewer_desc),
                details = stringResource(R.string.scope_reviewer_details)
            ),
            ScopeItem(
                AiSettingsScope.QUESTION_GENERATE, stringResource(R.string.settings_scope_question_generate), stringResource(R.string.settings_scope_question_generate_desc),
                details = stringResource(R.string.scope_question_generate_details)
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
        Text(stringResource(R.string.settings_model_advanced), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_model_advanced_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_debug_mode), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_debug_mode_desc),
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
                Text(stringResource(R.string.settings_response_unwrap), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_response_unwrap_desc),
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
                Text(stringResource(R.string.settings_json_repair), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_json_repair_desc),
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
        Text(stringResource(R.string.settings_image_compression), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_image_compress_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_enable_image_compress), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_image_compress_off_desc),
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
            label = { Text(stringResource(R.string.settings_target_size_kb)) },
            placeholder = { Text("1024") },
            supportingText = { Text(stringResource(R.string.settings_target_size_hint)) },
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
        Text(stringResource(R.string.settings_background_task), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_bg_task_desc),
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
            label = { Text(stringResource(R.string.settings_bg_concurrency)) },
            placeholder = { Text("2") },
            supportingText = { Text(stringResource(R.string.settings_bg_concurrency_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onManage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_open_bg_task_mgmt))
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
                    Text(stringResource(R.string.settings_got_it))
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
                        text = if (isLoadingModels) stringResource(R.string.settings_models_loading) else stringResource(R.string.settings_models_manual_refresh),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefreshModels) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.settings_refresh_models_cd))
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
        stringResource(R.string.settings_provider_not_used)
    } else {
        val scopeLabels = pending.affectedScopes.map { stringResource(scopeLabel(it)) }.joinToString("、")
        stringResource(R.string.settings_affected_scopes, scopeLabels)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_delete_provider)) },
        text = { Text(stringResource(R.string.settings_delete_fallback, scopesText)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_confirm_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
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
        Text(stringResource(R.string.article_online_reading), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_online_reading_desc),
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
            label = { Text(stringResource(R.string.settings_detail_concurrency)) },
            placeholder = { Text("5") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocaleSection(
    currentLocale: String,
    onLocaleChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val locales = listOf(
        "system" to stringResource(R.string.locale_system),
        "zh" to "中文",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
        "de" to "Deutsch",
        "ru" to "Русский",
        "es" to "Español",
        "fr" to "Français",
        "pt" to "Português",
        "ar" to "العربية"
    )

    val currentLabel = locales.find { it.first == currentLocale }?.second ?: stringResource(R.string.locale_system)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_app_locale), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_app_locale_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_app_locale)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                locales.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onLocaleChange(code)
                            expanded = false
                        }
                    )
                }
            }
        }
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
        Text(stringResource(R.string.settings_auto_scan), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_auto_scan_desc),
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
            label = { Text(stringResource(R.string.settings_rescore_interval)) },
            placeholder = { Text("24") },
            supportingText = { Text(stringResource(R.string.settings_rescore_hint)) },
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
        "system" to stringResource(R.string.settings_tts_follow_system),
        "en-US" to stringResource(R.string.settings_tts_en_us),
        "en-GB" to stringResource(R.string.settings_tts_en_gb)
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
        Text(stringResource(R.string.settings_tts), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_tts_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(stringResource(R.string.settings_tts_rate_value, "%.2f".format(rate)), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = rate,
            onValueChange = onRateChange,
            valueRange = 0.5f..2.0f
        )

        Text(stringResource(R.string.settings_tts_pitch_value, "%.2f".format(pitch)), style = MaterialTheme.typography.bodySmall)
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
                Text(stringResource(R.string.settings_tts_auto_study), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_tts_auto_study_desc),
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

        Text(stringResource(R.string.settings_tts_prewarm), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.settings_tts_prewarm_desc),
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
            label = { Text(stringResource(R.string.settings_tts_prewarm_concurrency)) },
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
            label = { Text(stringResource(R.string.settings_tts_prewarm_retry)) },
            placeholder = { Text("2") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onTest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_play_sample))
        }
    }
}

private data class ScopeItem(
    val scope: AiSettingsScope,
    val title: String,
    val description: String,
    val details: String
)

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

private fun scopeLabel(scope: AiSettingsScope): Int {
    return when (scope) {
        AiSettingsScope.MAIN -> R.string.settings_scope_main
        AiSettingsScope.FAST -> R.string.settings_scope_fast
        AiSettingsScope.OCR -> R.string.settings_scope_ocr
        AiSettingsScope.POOL -> R.string.settings_scope_pool
        AiSettingsScope.ARTICLE -> R.string.settings_scope_article
        AiSettingsScope.SEARCH -> R.string.settings_scope_search
        AiSettingsScope.REVIEWER -> R.string.settings_scope_reviewer
        AiSettingsScope.QUESTION_GENERATE -> R.string.settings_scope_question_generate
    }
}
