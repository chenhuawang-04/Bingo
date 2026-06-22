package com.xty.englishhelper.ui.screen.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.ui.designsystem.components.EhCard
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing

/**
 * 可折叠的设置区块卡片
 *
 * @param icon 区块图标
 * @param title 区块标题
 * @param subtitle 区块副标题（可选）
 * @param searchQuery 搜索关键词，不匹配时隐藏整个区块
 * @param modifier 修饰符
 * @param content 区块内容
 */
@Composable
fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    searchQuery: String = "",
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // 搜索匹配逻辑：title 或 subtitle 包含关键词（忽略大小写）
    val isMatch = searchQuery.isBlank() ||
            title.contains(searchQuery, ignoreCase = true) ||
            subtitle?.contains(searchQuery, ignoreCase = true) == true

    if (!isMatch) return

    var expanded by rememberSaveable(key = title) {
        mutableStateOf(false) // 默认全部折叠
    }

    val spacing = LocalEhSpacing.current

    EhCard(modifier = modifier) {
        // 标题栏（可点击展开/收起）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开"
            )
        }

        // 内容区（动画展开/收起）
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(
                    start = spacing.md,
                    end = spacing.md,
                    bottom = spacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(spacing.xxs))
                content()
            }
        }
    }
}
