package com.xty.englishhelper.ui.screen.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.xty.englishhelper.domain.model.OnlineReadingSource
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing
import com.xty.englishhelper.ui.screen.settings.SettingsUiState
import com.xty.englishhelper.ui.screen.settings.SettingsViewModel
import com.xty.englishhelper.ui.screen.settings.components.ChipOption
import com.xty.englishhelper.ui.screen.settings.components.SettingsChipRow
import com.xty.englishhelper.ui.screen.settings.components.SettingsSwitchRow
import com.xty.englishhelper.ui.screen.settings.components.SettingsTextFieldRow

/**
 * 文章功能设置区块
 * 包含：在线阅读、自动扫描、图片压缩
 */
@Composable
internal fun ArticleSettingsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val spacing = LocalEhSpacing.current

    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        // 1. 在线阅读
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
                    text = stringResource(R.string.article_online_reading),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_online_reading_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsChipRow(
                    title = stringResource(R.string.settings_default_source),
                    options = OnlineReadingSource.values().map { source ->
                        ChipOption(
                            label = source.label,
                            selected = state.onlineReadingSource == source,
                            onClick = { viewModel.onOnlineReadingSourceChange(source) }
                        )
                    }
                )

                var concurrencyInput by remember { mutableStateOf(state.guardianDetailConcurrency.toString()) }
                LaunchedEffect(state.guardianDetailConcurrency) {
                    val current = state.guardianDetailConcurrency.toString()
                    if (concurrencyInput != current) {
                        concurrencyInput = current
                    }
                }

                SettingsTextFieldRow(
                    title = stringResource(R.string.settings_detail_concurrency),
                    value = concurrencyInput,
                    onValueChange = { value ->
                        val filtered = value.filter { it.isDigit() }
                        concurrencyInput = filtered
                        if (filtered.isBlank()) {
                            viewModel.onGuardianDetailConcurrencyChange(5)
                            return@SettingsTextFieldRow
                        }
                        val parsed = filtered.toIntOrNull() ?: return@SettingsTextFieldRow
                        val clamped = parsed.coerceIn(1, 10)
                        if (clamped != state.guardianDetailConcurrency) {
                            viewModel.onGuardianDetailConcurrencyChange(clamped)
                        }
                    },
                    placeholder = "5",
                    keyboardType = KeyboardType.Number
                )
            }
        }

        // 2. 自动扫描
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
                    text = stringResource(R.string.settings_auto_scan),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_auto_scan_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                var rescoreInput by remember { mutableStateOf(state.scanRescoreAfterHours.toString()) }
                LaunchedEffect(state.scanRescoreAfterHours) {
                    val current = state.scanRescoreAfterHours.toString()
                    if (rescoreInput != current) {
                        rescoreInput = current
                    }
                }

                SettingsTextFieldRow(
                    title = stringResource(R.string.settings_rescore_interval),
                    value = rescoreInput,
                    onValueChange = { value ->
                        val filtered = value.filter { it.isDigit() }
                        rescoreInput = filtered
                        if (filtered.isBlank()) {
                            viewModel.onScanRescoreAfterHoursChange(24)
                            return@SettingsTextFieldRow
                        }
                        val parsed = filtered.toIntOrNull() ?: return@SettingsTextFieldRow
                        val clamped = parsed.coerceIn(1, 720)
                        if (clamped != state.scanRescoreAfterHours) {
                            viewModel.onScanRescoreAfterHoursChange(clamped)
                        }
                    },
                    placeholder = "24",
                    keyboardType = KeyboardType.Number,
                    supportingText = stringResource(R.string.settings_rescore_hint)
                )
            }
        }

        // 3. 图片压缩
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
                    text = stringResource(R.string.settings_image_compression),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_image_compress_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_enable_image_compress),
                    description = stringResource(R.string.settings_image_compress_off_desc),
                    checked = state.imageCompressionEnabled,
                    onCheckedChange = viewModel::onImageCompressionEnabledChange
                )

                val targetKb = (state.imageCompressionTargetBytes / 1024).coerceAtLeast(1)
                var targetInput by remember { mutableStateOf(targetKb.toString()) }
                LaunchedEffect(state.imageCompressionTargetBytes) {
                    val current = targetKb.toString()
                    if (targetInput != current) {
                        targetInput = current
                    }
                }

                SettingsTextFieldRow(
                    title = stringResource(R.string.settings_target_size_kb),
                    value = targetInput,
                    onValueChange = { value ->
                        val filtered = value.filter { it.isDigit() }
                        targetInput = filtered
                        if (filtered.isBlank()) return@SettingsTextFieldRow
                        val kb = filtered.toIntOrNull() ?: return@SettingsTextFieldRow
                        val clampedKb = kb.coerceIn(200, 4096)
                        if (clampedKb * 1024 != state.imageCompressionTargetBytes) {
                            viewModel.onImageCompressionTargetBytesChange(clampedKb * 1024)
                        }
                    },
                    placeholder = "1024",
                    keyboardType = KeyboardType.Number,
                    supportingText = stringResource(R.string.settings_target_size_hint)
                )
            }
        }
    }
}
