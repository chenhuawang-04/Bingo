package com.xty.englishhelper.ui.screen.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing

/**
 * 滑块行组件
 *
 * @param title 设置项标题
 * @param description 说明文字（可选）
 * @param value 当前值
 * @param valueRange 取值范围
 * @param steps 步进数量（0 表示连续）
 * @param valueLabel 值的格式化显示
 * @param onValueChange 值变化回调
 * @param modifier 修饰符
 */
@Composable
fun SettingsSliderRow(
    title: String,
    description: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: @Composable (Float) -> String = { "%.1f".format(it) },
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalEhSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}
