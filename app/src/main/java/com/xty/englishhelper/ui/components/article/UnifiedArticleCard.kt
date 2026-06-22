package com.xty.englishhelper.ui.components.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.ui.designsystem.tokens.ArticleShapes
import com.xty.englishhelper.ui.screen.article.EditorialThumbnail

/**
 * 统一的文章卡片组件，同时用于本地文章列表和在线文章浏览。
 *
 * 支持：
 * - 评分说明默认展开显示（无 reason 时隐藏）
 * - 本地文章：重新评分 / 移至分类 / 删除
 * - 在线文章：重新评分
 */
@Composable
fun UnifiedArticleCard(
    title: String,
    sourceLine: String,
    snippet: String,
    coverModel: Any?,
    placeholderSeed: String,
    wordCount: Int?,
    scoreText: String,
    suitabilityReason: String?,
    categoryName: String?,
    isEvaluating: Boolean,
    onRead: () -> Unit,
    onReevaluate: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onMoveCategory: ((Long) -> Unit)? = null,
    categories: List<ArticleCategory>? = null,
    categoryId: Long? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }

    Card(
        onClick = onRead,
        modifier = modifier.fillMaxWidth(),
        shape = ArticleShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            EditorialThumbnail(
                imageModel = coverModel,
                fallbackSeed = placeholderSeed,
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(0.76f)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (sourceLine.isNotBlank()) {
                    Text(
                        text = sourceLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (snippet.isNotBlank()) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (wordCount != null && wordCount > 0) {
                        Text(
                            text = stringResource(R.string.article_word_count_unit, wordCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = scoreText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!categoryName.isNullOrBlank()) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 评分说明区域：默认展开，无 reason 时隐藏
                if (!suitabilityReason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = suitabilityReason,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 三点菜单
            ArticleCardMenu(
                expanded = showMenu,
                onExpand = { showMenu = true },
                onDismiss = { showMenu = false },
                onReevaluate = onReevaluate,
                onMoveCategory = if (onMoveCategory != null) {
                    {
                        selectedCategoryId = categoryId
                        showMoveDialog = true
                    }
                } else null,
                onDelete = if (onDelete != null) {
                    { showDeleteConfirm = true }
                } else null
            )
        }
    }

    // 删除确认对话框（仅本地文章）
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.article_delete_title)) },
            text = { Text(stringResource(R.string.article_delete_confirm, title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 移动分类对话框（仅本地文章）
    if (showMoveDialog && onMoveCategory != null && categories != null) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text(stringResource(R.string.article_move_category)) },
            text = {
                Column {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategoryId == category.id,
                                onClick = { selectedCategoryId = category.id }
                            )
                            Text(
                                text = category.name,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedCategoryId?.let(onMoveCategory)
                        showMoveDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun ArticleCardMenu(
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onReevaluate: () -> Unit,
    onMoveCategory: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(onClick = onExpand) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.article_actions)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.article_re_evaluate)) },
                onClick = {
                    onDismiss()
                    onReevaluate()
                }
            )
            if (onMoveCategory != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.article_move_category)) },
                    onClick = {
                        onDismiss()
                        onMoveCategory()
                    }
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete)) },
                    onClick = {
                        onDismiss()
                        onDelete()
                    }
                )
            }
        }
    }
}
