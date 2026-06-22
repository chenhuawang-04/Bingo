package com.xty.englishhelper.ui.screen.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing

/**
 * Chip 选项数据类
 *
 * @param label 选项标签
 * @param selected 是否选中
 * @param onClick 点击回调
 */
data class ChipOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * FilterChip 行组件
 *
 * @param title 设置项标题
 * @param description 说明文字（可选）
 * @param options 选项列表
 * @param modifier 修饰符
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsChipRow(
    title: String,
    description: String? = null,
    options: List<ChipOption>,
    modifier: Modifier = Modifier
) {
    val spacing = LocalEhSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.selected,
                    onClick = option.onClick,
                    label = { Text(option.label) }
                )
            }
        }
    }
}
