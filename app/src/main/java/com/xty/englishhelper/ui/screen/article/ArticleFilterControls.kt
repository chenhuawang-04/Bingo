package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ArticleLengthFilter(
    val label: String,
    private val minInclusive: Int? = null,
    private val maxInclusive: Int? = null
) {
    ALL("长度：全部"),
    SHORT("长度：0-800", minInclusive = 0, maxInclusive = 800),
    MEDIUM("长度：801-1500", minInclusive = 801, maxInclusive = 1500),
    LONG("长度：1501-2500", minInclusive = 1501, maxInclusive = 2500),
    VERY_LONG("长度：2501+", minInclusive = 2501);

    fun matches(wordCount: Int?): Boolean {
        if (this == ALL) return true
        val count = wordCount ?: return false
        val minOk = minInclusive?.let { count >= it } ?: true
        val maxOk = maxInclusive?.let { count <= it } ?: true
        return minOk && maxOk
    }
}

enum class ArticleScoreFilter(
    val label: String,
    private val minInclusive: Int? = null,
    private val maxInclusive: Int? = null
) {
    ALL("评分：全部"),
    LOW("评分：0-59", minInclusive = 0, maxInclusive = 59),
    MID("评分：60-74", minInclusive = 60, maxInclusive = 74),
    HIGH("评分：75-89", minInclusive = 75, maxInclusive = 89),
    TOP("评分：90-100", minInclusive = 90, maxInclusive = 100);

    fun matches(score: Int?): Boolean {
        if (this == ALL) return true
        val value = score ?: return false
        val minOk = minInclusive?.let { value >= it } ?: true
        val maxOk = maxInclusive?.let { value <= it } ?: true
        return minOk && maxOk
    }
}

enum class ArticleSortOption(val label: String) {
    DEFAULT("默认排序"),
    LENGTH_ASC("长度升序"),
    LENGTH_DESC("长度降序"),
    SCORE_ASC("评分升序"),
    SCORE_DESC("评分降序")
}

fun <T> applyArticlePresentation(
    items: List<T>,
    filtersEnabled: Boolean,
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption,
    wordCountOf: (T) -> Int?,
    scoreOf: (T) -> Int?,
    titleOf: (T) -> String
): List<T> {
    val effectiveLengthFilter = if (filtersEnabled) lengthFilter else ArticleLengthFilter.ALL
    val effectiveScoreFilter = if (filtersEnabled) scoreFilter else ArticleScoreFilter.ALL
    val effectiveSortOption = if (filtersEnabled) sortOption else ArticleSortOption.DEFAULT

    val filtered = items.filter { item ->
        effectiveLengthFilter.matches(wordCountOf(item)) &&
            effectiveScoreFilter.matches(scoreOf(item))
    }
    val comparator = when (effectiveSortOption) {
        ArticleSortOption.DEFAULT -> null
        ArticleSortOption.LENGTH_ASC -> compareBy<T> { sortValueAscending(wordCountOf(it)) }
            .thenBy { titleOf(it) }
        ArticleSortOption.LENGTH_DESC -> compareByDescending<T> { sortValueDescending(wordCountOf(it)) }
            .thenBy { titleOf(it) }
        ArticleSortOption.SCORE_ASC -> compareBy<T> { sortValueAscending(scoreOf(it)) }
            .thenBy { titleOf(it) }
        ArticleSortOption.SCORE_DESC -> compareByDescending<T> { sortValueDescending(scoreOf(it)) }
            .thenBy { titleOf(it) }
    }
    return comparator?.let { filtered.sortedWith(it) } ?: filtered
}

fun hasArticleFilterConfig(
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption
): Boolean {
    return lengthFilter != ArticleLengthFilter.ALL ||
        scoreFilter != ArticleScoreFilter.ALL ||
        sortOption != ArticleSortOption.DEFAULT
}

fun isArticleFilterActive(
    filtersEnabled: Boolean,
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption
): Boolean {
    return filtersEnabled && hasArticleFilterConfig(lengthFilter, scoreFilter, sortOption)
}

fun resolveFilterEnabledAfterConfigChange(
    previousEnabled: Boolean,
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption
): Boolean {
    return previousEnabled && hasArticleFilterConfig(lengthFilter, scoreFilter, sortOption)
}

private fun sortValueAscending(value: Int?): Int = value ?: Int.MAX_VALUE

private fun sortValueDescending(value: Int?): Int = value ?: Int.MIN_VALUE

@Composable
fun ArticleFilterActionButton(
    filterEnabled: Boolean,
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption,
    onFilterEnabledChange: (Boolean) -> Unit,
    onLengthFilterChange: (ArticleLengthFilter) -> Unit,
    onScoreFilterChange: (ArticleScoreFilter) -> Unit,
    onSortOptionChange: (ArticleSortOption) -> Unit,
    onReset: () -> Unit,
    helperText: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val hasConfig = remember(lengthFilter, scoreFilter, sortOption) {
        hasArticleFilterConfig(lengthFilter, scoreFilter, sortOption)
    }
    val isActive = remember(filterEnabled, lengthFilter, scoreFilter, sortOption) {
        isArticleFilterActive(filterEnabled, lengthFilter, scoreFilter, sortOption)
    }
    val configuredCount = remember(lengthFilter, scoreFilter, sortOption) {
        listOf(
            lengthFilter != ArticleLengthFilter.ALL,
            scoreFilter != ArticleScoreFilter.ALL,
            sortOption != ArticleSortOption.DEFAULT
        ).count { it }
    }
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        hasConfig -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        hasConfig -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = modifier) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "配置筛选与排序"
                    )
                }
                if (configuredCount > 0) {
                    Text(
                        text = configuredCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier
                        .height(18.dp)
                        .width(1.dp),
                    color = contentColor.copy(alpha = 0.25f)
                )
                IconButton(
                    onClick = {
                        if (hasConfig) {
                            onFilterEnabledChange(!filterEnabled)
                        } else {
                            expanded = true
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isActive -> Icons.Default.CheckCircle
                            hasConfig -> Icons.Default.RadioButtonUnchecked
                            else -> Icons.Default.ArrowDropDown
                        },
                        contentDescription = when {
                            isActive -> "关闭筛选"
                            hasConfig -> "启用筛选"
                            else -> "展开筛选菜单"
                        }
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 292.dp, max = 348.dp)
        ) {
            ArticleFilterMenuContent(
                filterEnabled = filterEnabled,
                lengthFilter = lengthFilter,
                scoreFilter = scoreFilter,
                sortOption = sortOption,
                onFilterEnabledChange = onFilterEnabledChange,
                onLengthFilterChange = onLengthFilterChange,
                onScoreFilterChange = onScoreFilterChange,
                onSortOptionChange = onSortOptionChange,
                helperText = helperText,
                onReset = onReset,
                onDone = { expanded = false }
            )
        }
    }
}

@Composable
private fun ArticleFilterMenuContent(
    filterEnabled: Boolean,
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption,
    onFilterEnabledChange: (Boolean) -> Unit,
    onLengthFilterChange: (ArticleLengthFilter) -> Unit,
    onScoreFilterChange: (ArticleScoreFilter) -> Unit,
    onSortOptionChange: (ArticleSortOption) -> Unit,
    helperText: String?,
    onReset: () -> Unit,
    onDone: () -> Unit,
    helperColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val hasConfig = hasArticleFilterConfig(lengthFilter, scoreFilter, sortOption)
    val configuredCount = listOf(
        lengthFilter != ArticleLengthFilter.ALL,
        scoreFilter != ArticleScoreFilter.ALL,
        sortOption != ArticleSortOption.DEFAULT
    ).count { it }

    Column(
        modifier = Modifier
            .widthIn(min = 292.dp, max = 348.dp)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("筛选与排序", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    !hasConfig -> "先配置条件，再用右侧按钮决定是否启用。"
                    filterEnabled -> "当前筛选已生效，只影响当前可见文章。"
                    else -> "条件已保存，但暂未生效。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = helperColor
            )
        }

        FilterSection(
            title = "长度范围",
            values = ArticleLengthFilter.entries,
            selected = lengthFilter,
            labelOf = { it.label },
            onSelected = onLengthFilterChange
        )

        FilterSection(
            title = "评分范围",
            values = ArticleScoreFilter.entries,
            selected = scoreFilter,
            labelOf = { it.label },
            onSelected = onScoreFilterChange
        )

        FilterSection(
            title = "排序方式",
            values = ArticleSortOption.entries,
            selected = sortOption,
            labelOf = { it.label },
            onSelected = onSortOptionChange
        )

        if (!helperText.isNullOrBlank()) {
            Text(
                helperText,
                style = MaterialTheme.typography.bodySmall,
                color = helperColor
            )
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onReset) {
                Text("重置")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (configuredCount > 0) {
                    Text(
                        text = if (filterEnabled) "已启用 $configuredCount 项" else "已配置 $configuredCount 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (filterEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(
                    onClick = { onFilterEnabledChange(!filterEnabled && hasConfig) },
                    enabled = hasConfig
                ) {
                    Text(if (filterEnabled) "暂停应用" else "启用筛选")
                }
                TextButton(onClick = onDone) {
                    Text("完成")
                }
            }
        }
    }
}

@Composable
private fun <T> FilterSection(
    title: String,
    values: Iterable<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit
) {
    val items = remember(values) { values.toList() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            FilterChip(
                                selected = item == selected,
                                onClick = { onSelected(item) },
                                label = { Text(labelOf(item)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    repeat(2 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
