package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption,
    wordCountOf: (T) -> Int?,
    scoreOf: (T) -> Int?,
    titleOf: (T) -> String
): List<T> {
    val filtered = items.filter { item ->
        lengthFilter.matches(wordCountOf(item)) && scoreFilter.matches(scoreOf(item))
    }
    val comparator = when (sortOption) {
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

private fun sortValueAscending(value: Int?): Int = value ?: Int.MAX_VALUE

private fun sortValueDescending(value: Int?): Int = value ?: Int.MIN_VALUE

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArticleFilterPanel(
    lengthFilter: ArticleLengthFilter,
    scoreFilter: ArticleScoreFilter,
    sortOption: ArticleSortOption,
    onLengthFilterChange: (ArticleLengthFilter) -> Unit,
    onScoreFilterChange: (ArticleScoreFilter) -> Unit,
    onSortOptionChange: (ArticleSortOption) -> Unit,
    helperText: String? = null,
    helperColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("筛选与排序", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArticleLengthFilter.values().forEach { item ->
                FilterChip(
                    selected = item == lengthFilter,
                    onClick = { onLengthFilterChange(item) },
                    label = { Text(item.label) }
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArticleScoreFilter.values().forEach { item ->
                FilterChip(
                    selected = item == scoreFilter,
                    onClick = { onScoreFilterChange(item) },
                    label = { Text(item.label) }
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArticleSortOption.values().forEach { item ->
                FilterChip(
                    selected = item == sortOption,
                    onClick = { onSortOptionChange(item) },
                    label = { Text(item.label) }
                )
            }
        }
        if (!helperText.isNullOrBlank()) {
            Text(
                helperText,
                style = MaterialTheme.typography.bodySmall,
                color = helperColor
            )
        }
    }
}
