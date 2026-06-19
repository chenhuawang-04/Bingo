package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.R
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes

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
    val configuration = LocalConfiguration.current
    val popupMaxHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp - 112.dp).coerceIn(240.dp, 520.dp)
    }
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
            shape = ArticleShapes.Control
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.article_filter_config)
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
                            isActive -> stringResource(R.string.article_filter_disable)
                            hasConfig -> stringResource(R.string.article_filter_enable)
                            else -> stringResource(R.string.article_filter_expand)
                        }
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Box(modifier = Modifier.widthIn(min = 260.dp, max = 348.dp)) {
                ArticleFilterPopupContent(
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
                    onDone = { expanded = false },
                    maxHeight = popupMaxHeight
                )
            }
        }
    }
}

@Composable
private fun ArticleFilterPopupContent(
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
    maxHeight: Dp,
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
            .heightIn(max = maxHeight)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.article_filter_title), style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    !hasConfig -> stringResource(R.string.article_filter_hint_not_configured)
                    filterEnabled -> stringResource(R.string.article_filter_hint_active)
                    else -> stringResource(R.string.article_filter_hint_saved)
                },
                style = MaterialTheme.typography.bodySmall,
                color = helperColor
            )
        }

        FilterSection(
            title = stringResource(R.string.article_filter_length_range),
            values = ArticleLengthFilter.entries,
            selected = lengthFilter,
            labelOf = { it.label },
            onSelected = onLengthFilterChange
        )

        FilterSection(
            title = stringResource(R.string.article_filter_score_range),
            values = ArticleScoreFilter.entries,
            selected = scoreFilter,
            labelOf = { it.label },
            onSelected = onScoreFilterChange
        )

        FilterSection(
            title = stringResource(R.string.article_filter_sort_method),
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
                Text(stringResource(R.string.common_reset))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (configuredCount > 0) {
                    Text(
                        text = if (filterEnabled) stringResource(R.string.article_filter_enabled_count, configuredCount) else stringResource(R.string.article_filter_configured_count, configuredCount),
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
                    Text(if (filterEnabled) stringResource(R.string.article_filter_pause_apply) else stringResource(R.string.article_filter_enable))
                }
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.common_done))
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
                                shape = ArticleShapes.Control,
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
