@file:OptIn(ExperimentalMaterial3Api::class)

package com.xty.englishhelper.ui.screen.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.xty.englishhelper.R
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing
import com.xty.englishhelper.ui.screen.settings.CloudSyncSection
import com.xty.englishhelper.ui.screen.settings.SettingsUiState
import com.xty.englishhelper.ui.screen.settings.SettingsViewModel
import com.xty.englishhelper.ui.screen.settings.components.ChipOption
import com.xty.englishhelper.ui.screen.settings.components.SettingsChipRow
import com.xty.englishhelper.ui.screen.settings.components.SettingsSliderRow
import com.xty.englishhelper.ui.screen.settings.components.SettingsSwitchRow
import com.xty.englishhelper.ui.screen.settings.components.SettingsTextFieldRow

/**
 * 系统与同步设置区块
 * 包含：语言设置、后台任务、云同步、语音合成
 */
@Composable
internal fun SystemSettingsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onTtsDiagnostics: () -> Unit
) {
    val spacing = LocalEhSpacing.current

    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        // 1. 语言设置
        LocaleCard(state, viewModel)

        // 2. 后台任务
        BackgroundTaskCard(state, viewModel)

        // 3. 云同步
        Card(
            shape = ArticleShapes.Section,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(spacing.md)) {
                CloudSyncSection(
                    state = state.cloudSync,
                    onOwnerChange = viewModel::onGitHubOwnerChange,
                    onRepoChange = viewModel::onGitHubRepoChange,
                    onConfigSyncEnabledChange = viewModel::onGitHubConfigSyncEnabledChange,
                    onConfigRepoChange = viewModel::onGitHubConfigRepoChange,
                    onPatChange = viewModel::onGitHubPatChange,
                    onTestConnection = viewModel::testSyncConnection,
                    onSync = viewModel::performSync,
                    onForceUpload = viewModel::performForceUpload,
                    onForceDownload = viewModel::performForceDownload,
                    onClearError = viewModel::clearSyncError
                )
            }
        }

        // 4. 语音合成 (TTS)
        TtsCard(state, viewModel, onTtsDiagnostics)
    }
}

@Composable
private fun LocaleCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val spacing = LocalEhSpacing.current

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

    val currentLabel = locales.find { it.first == state.appLocale }?.second
        ?: stringResource(R.string.locale_system)

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
                text = stringResource(R.string.settings_app_locale),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.settings_app_locale_desc),
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
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    locales.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.onLocaleChange(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundTaskCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val spacing = LocalEhSpacing.current

    var input by remember { mutableStateOf(state.backgroundTaskConcurrency.toString()) }

    LaunchedEffect(state.backgroundTaskConcurrency) {
        val current = state.backgroundTaskConcurrency.toString()
        if (input != current) {
            input = current
        }
    }

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
                text = stringResource(R.string.settings_background_task),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.settings_bg_task_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingsTextFieldRow(
                title = stringResource(R.string.settings_bg_concurrency),
                value = input,
                onValueChange = { value ->
                    val filtered = value.filter { it.isDigit() }
                    input = filtered
                    if (filtered.isBlank()) {
                        viewModel.onBackgroundTaskConcurrencyChange(2)
                        return@SettingsTextFieldRow
                    }
                    val parsed = filtered.toIntOrNull() ?: return@SettingsTextFieldRow
                    val clamped = parsed.coerceIn(1, 6)
                    if (clamped != state.backgroundTaskConcurrency) {
                        viewModel.onBackgroundTaskConcurrencyChange(clamped)
                    }
                },
                placeholder = "2",
                keyboardType = KeyboardType.Number,
                supportingText = stringResource(R.string.settings_bg_concurrency_hint)
            )
        }
    }
}

@Composable
private fun TtsCard(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onTtsDiagnostics: () -> Unit
) {
    val spacing = LocalEhSpacing.current

    val locales = listOf(
        "system" to stringResource(R.string.settings_tts_follow_system),
        "en-US" to stringResource(R.string.settings_tts_en_us),
        "en-GB" to stringResource(R.string.settings_tts_en_gb)
    )

    var prewarmConcurrencyInput by remember { mutableStateOf(state.ttsPrewarmConcurrency.toString()) }
    var prewarmRetryInput by remember { mutableStateOf(state.ttsPrewarmRetry.toString()) }

    LaunchedEffect(state.ttsPrewarmConcurrency) {
        val current = state.ttsPrewarmConcurrency.toString()
        if (prewarmConcurrencyInput != current) {
            prewarmConcurrencyInput = current
        }
    }
    LaunchedEffect(state.ttsPrewarmRetry) {
        val current = state.ttsPrewarmRetry.toString()
        if (prewarmRetryInput != current) {
            prewarmRetryInput = current
        }
    }

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
                text = stringResource(R.string.settings_tts),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.settings_tts_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingsSliderRow(
                title = stringResource(R.string.settings_tts_rate_value, "%.2f".format(state.ttsRate)),
                value = state.ttsRate,
                valueRange = 0.5f..2.0f,
                valueLabel = { "%.2f".format(it) },
                onValueChange = viewModel::onTtsRateChange
            )

            SettingsSliderRow(
                title = stringResource(R.string.settings_tts_pitch_value, "%.2f".format(state.ttsPitch)),
                value = state.ttsPitch,
                valueRange = 0.5f..2.0f,
                valueLabel = { "%.2f".format(it) },
                onValueChange = viewModel::onTtsPitchChange
            )

            SettingsChipRow(
                title = stringResource(R.string.settings_tts_locale),
                options = locales.map { (value, label) ->
                    ChipOption(
                        label = label,
                        selected = state.ttsLocale == value,
                        onClick = { viewModel.onTtsLocaleChange(value) }
                    )
                }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_tts_auto_study),
                description = stringResource(R.string.settings_tts_auto_study_desc),
                checked = state.ttsAutoStudy,
                onCheckedChange = viewModel::onTtsAutoStudyChange
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_tts_prewarm),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.settings_tts_prewarm_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingsTextFieldRow(
                title = stringResource(R.string.settings_tts_prewarm_concurrency),
                value = prewarmConcurrencyInput,
                onValueChange = { value ->
                    val filtered = value.filter { it.isDigit() }
                    prewarmConcurrencyInput = filtered
                    if (filtered.isBlank()) {
                        viewModel.onTtsPrewarmConcurrencyChange(2)
                        return@SettingsTextFieldRow
                    }
                    val parsed = filtered.toIntOrNull() ?: return@SettingsTextFieldRow
                    val clamped = parsed.coerceIn(1, 6)
                    if (clamped != state.ttsPrewarmConcurrency) {
                        viewModel.onTtsPrewarmConcurrencyChange(clamped)
                    }
                },
                placeholder = "2",
                keyboardType = KeyboardType.Number
            )

            SettingsTextFieldRow(
                title = stringResource(R.string.settings_tts_prewarm_retry),
                value = prewarmRetryInput,
                onValueChange = { value ->
                    val filtered = value.filter { it.isDigit() }
                    prewarmRetryInput = filtered
                    if (filtered.isBlank()) {
                        viewModel.onTtsPrewarmRetryChange(2)
                        return@SettingsTextFieldRow
                    }
                    val parsed = filtered.toIntOrNull() ?: return@SettingsTextFieldRow
                    val clamped = parsed.coerceIn(0, 5)
                    if (clamped != state.ttsPrewarmRetry) {
                        viewModel.onTtsPrewarmRetryChange(clamped)
                    }
                },
                placeholder = "2",
                keyboardType = KeyboardType.Number
            )

            Button(
                onClick = viewModel::playTtsSample,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_play_sample))
            }

            Button(
                onClick = onTtsDiagnostics,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_voice_diagnostics))
            }
        }
    }
}
