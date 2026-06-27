package com.xty.englishhelper.ui.screen.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.PoolRetryMode
import com.xty.englishhelper.domain.model.WordReferenceSource
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing
import com.xty.englishhelper.ui.screen.settings.SettingsUiState
import com.xty.englishhelper.ui.screen.settings.SettingsViewModel
import com.xty.englishhelper.ui.screen.settings.components.ChipOption
import com.xty.englishhelper.ui.screen.settings.components.SettingsChipRow
import com.xty.englishhelper.ui.screen.settings.components.SettingsSliderRow
import com.xty.englishhelper.ui.screen.settings.components.SettingsSwitchRow
import java.util.Locale

/**
 * 学习功能设置区块
 * 包含：词条整理增强、词池设置、头脑风暴背词
 */
@Composable
internal fun StudySettingsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val spacing = LocalEhSpacing.current

    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        // 1. 词条整理增强
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
                    text = stringResource(R.string.settings_word_organize_enhance),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_word_organize_enhance_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_high_quality_mode),
                    description = stringResource(R.string.settings_high_quality_desc),
                    checked = state.wordOrganizeHighQualityEnabled,
                    onCheckedChange = viewModel::onWordOrganizeHighQualityEnabledChange
                )

                SettingsChipRow(
                    title = stringResource(R.string.settings_reference_source),
                    description = stringResource(R.string.settings_reference_source_desc),
                    options = listOf(
                        ChipOption(
                            label = stringResource(R.string.settings_fast_model),
                            selected = state.wordOrganizeReferenceSource == WordReferenceSource.FAST,
                            onClick = { viewModel.onWordOrganizeReferenceSourceChange(WordReferenceSource.FAST) }
                        ),
                        ChipOption(
                            label = stringResource(R.string.settings_search_model),
                            selected = state.wordOrganizeReferenceSource == WordReferenceSource.SEARCH,
                            onClick = { viewModel.onWordOrganizeReferenceSourceChange(WordReferenceSource.SEARCH) }
                        )
                    )
                )
            }
        }

        // 2. 词池设置
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
                    text = stringResource(R.string.settings_pool_organize),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_pool_organize_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsChipRow(
                    title = stringResource(R.string.settings_retry_mode),
                    description = when (state.poolRetryMode) {
                        PoolRetryMode.AGGRESSIVE -> stringResource(R.string.settings_retry_aggressive_desc)
                        PoolRetryMode.LENIENT -> stringResource(R.string.settings_retry_lenient_desc)
                    } + "\n" + stringResource(R.string.settings_retry_desc),
                    options = listOf(
                        ChipOption(
                            label = stringResource(R.string.settings_retry_aggressive),
                            selected = state.poolRetryMode == PoolRetryMode.AGGRESSIVE,
                            onClick = { viewModel.onPoolRetryModeChange(PoolRetryMode.AGGRESSIVE) }
                        ),
                        ChipOption(
                            label = stringResource(R.string.settings_retry_lenient),
                            selected = state.poolRetryMode == PoolRetryMode.LENIENT,
                            onClick = { viewModel.onPoolRetryModeChange(PoolRetryMode.LENIENT) }
                        )
                    )
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_managed_mode),
                    description = stringResource(R.string.settings_managed_mode_desc),
                    checked = state.poolManagedMode,
                    onCheckedChange = viewModel::onPoolManagedModeChange
                )

                SettingsSliderRow(
                    title = stringResource(R.string.settings_window_size, state.poolWindowSize),
                    description = stringResource(R.string.settings_window_size_desc),
                    value = state.poolWindowSize.toFloat(),
                    valueRange = 10f..200f,
                    steps = 18,
                    valueLabel = { "${it.toInt()}" },
                    onValueChange = { viewModel.onPoolWindowSizeChange(it.toInt()) }
                )

                SettingsSliderRow(
                    title = stringResource(R.string.settings_max_concurrent, state.poolMaxConcurrent),
                    description = stringResource(R.string.settings_max_concurrent_desc),
                    value = state.poolMaxConcurrent.toFloat(),
                    valueRange = 1f..10f,
                    steps = 8,
                    valueLabel = { "${it.toInt()}" },
                    onValueChange = { viewModel.onPoolMaxConcurrentChange(it.toInt()) }
                )

                SettingsSliderRow(
                    title = stringResource(R.string.settings_rpm_limit, state.poolRequestsPerMinute),
                    description = stringResource(R.string.settings_rpm_limit_desc),
                    value = state.poolRequestsPerMinute.toFloat(),
                    valueRange = 5f..120f,
                    steps = 22,
                    valueLabel = { "${it.toInt()}" },
                    onValueChange = { viewModel.onPoolRequestsPerMinuteChange(it.toInt()) }
                )
            }
        }

        // 3. 头脑风暴背词
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
                    text = stringResource(R.string.settings_brainstorm),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.settings_brainstorm_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_word_note),
                    description = stringResource(R.string.settings_word_note_desc),
                    checked = state.studyWordNoteEnabled,
                    onCheckedChange = viewModel::onStudyWordNoteEnabledChange
                )

                SettingsSliderRow(
                    title = stringResource(R.string.settings_cluster_size, state.brainstormClusterSize),
                    description = stringResource(R.string.settings_cluster_size_desc),
                    value = state.brainstormClusterSize.toFloat(),
                    valueRange = 2f..12f,
                    steps = 9,
                    valueLabel = { "${it.toInt()}" },
                    onValueChange = { viewModel.onBrainstormClusterSizeChange(it.toInt()) }
                )

                SettingsSliderRow(
                    title = stringResource(
                        R.string.settings_quality_threshold,
                        String.format(Locale.getDefault(), "%.2f", state.brainstormQualityMinConfidence)
                    ),
                    description = stringResource(R.string.settings_quality_threshold_desc),
                    value = state.brainstormQualityMinConfidence,
                    valueRange = 0f..0.9f,
                    steps = 17,
                    valueLabel = { String.format(Locale.getDefault(), "%.2f", it) },
                    onValueChange = viewModel::onBrainstormQualityMinConfidenceChange
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_active_recall),
                    description = stringResource(R.string.settings_active_recall_desc),
                    checked = state.brainstormActiveRecall,
                    onCheckedChange = viewModel::onBrainstormActiveRecallChange
                )
            }
        }
    }
}
