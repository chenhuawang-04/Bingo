package com.xty.englishhelper.ui.screen.article

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArticleEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: ArticleEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        viewModel.addImages(uris)
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.onCoverImageSelected(uri)
    }

    val paragraphImageIndex = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val paragraphImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val idx = paragraphImageIndex.intValue
        if (idx >= 0) {
            viewModel.onParagraphImageSelected(idx, uri)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) {
            onSaved(state.savedArticleId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑文章" else "创建文章") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.isSaving &&
                            state.title.isNotBlank() &&
                            state.paragraphs.any { it.text.isNotBlank() }
                    ) {
                        Text("保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        EhMaxWidthContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            maxWidth = 860.dp
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compactLayout = maxWidth < 680.dp

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        EditorSectionCard(
                            title = "基本信息",
                            description = "先填写标题、来源和封面，便于后续列表展示与分类。"
                        ) {
                            OutlinedTextField(
                                value = state.title,
                                onValueChange = viewModel::onTitleChange,
                                label = { Text("标题") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = state.summary,
                                onValueChange = viewModel::onSummaryChange,
                                label = { Text("梗概（可选）") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )

                            ResponsiveFieldRow(
                                compact = compactLayout,
                                first = {
                                    OutlinedTextField(
                                        value = state.author,
                                        onValueChange = viewModel::onAuthorChange,
                                        label = { Text("作者") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                second = {
                                    OutlinedTextField(
                                        value = state.source,
                                        onValueChange = viewModel::onSourceChange,
                                        label = { Text("来源") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )

                            OutlinedTextField(
                                value = state.domain,
                                onValueChange = viewModel::onDomainChange,
                                label = { Text("领域（可选）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = { coverPickerLauncher.launch("image/*") }) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  选择封面")
                                }
                                if (state.coverImageUri != null) {
                                    InputChip(
                                        selected = false,
                                        onClick = { viewModel.onCoverImageSelected(null) },
                                        label = { Text("已选封面") },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "移除",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        EditorSectionCard(
                            title = "正文结构",
                            description = "段落会按当前顺序保存；可逐段插图，也可以直接粘贴全文后再细调。"
                        ) {
                            Text(
                                "正文段落将按下方顺序逐项渲染，长文也能保持流畅编辑。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(
                        count = state.paragraphs.size,
                        key = { index -> state.paragraphs[index].localId }
                    ) { index ->
                        val paragraph = state.paragraphs[index]
                        ParagraphEditorCard(
                            index = index,
                            paragraphText = paragraph.text,
                            hasImage = paragraph.imageUri != null,
                            onTextChange = { viewModel.onParagraphTextChange(index, it) },
                            onPickImage = {
                                paragraphImageIndex.intValue = index
                                paragraphImagePicker.launch("image/*")
                            },
                            onRemoveImage = { viewModel.onParagraphImageSelected(index, null) },
                            onRemoveParagraph = if (state.paragraphs.size > 1) {
                                { viewModel.removeParagraph(index) }
                            } else {
                                null
                            }
                        )
                    }

                    item {
                        EditorSectionActionsCard {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = viewModel::addParagraph) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  添加段落")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            viewModel.pasteFullText(clipText)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  粘贴全文")
                                }
                            }
                        }
                    }

                    item {
                        EditorSectionCard(
                            title = "图片 OCR",
                            description = "适合从试卷、截图或扫描件快速提取文章内容。"
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                    Text("  选择图片")
                                }
                                Button(
                                    onClick = {
                                        viewModel.extractWithAi { uri ->
                                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                                ?: ByteArray(0)
                                        }
                                    },
                                    enabled = !state.isOcrLoading && state.imageUris.isNotEmpty()
                                ) {
                                    if (state.isOcrLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text("  识别中…")
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                        Text("  AI 识别并填充")
                                    }
                                }
                            }

                            AnimatedVisibility(visible = state.isCompressing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "正在压缩图片…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (state.imageUris.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.imageUris.forEachIndexed { index, _ ->
                                        InputChip(
                                            selected = false,
                                            onClick = { viewModel.removeImage(index) },
                                            label = { Text("图片 ${index + 1}") },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "移除",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun EditorSectionActionsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun ResponsiveFieldRow(
    compact: Boolean,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            first()
            second()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) { first() }
            Column(modifier = Modifier.weight(1f)) { second() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParagraphEditorCard(
    index: Int,
    paragraphText: String,
    hasImage: Boolean,
    onTextChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onRemoveParagraph: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "段落 ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPickImage,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "插图", modifier = Modifier.size(18.dp))
                    }
                    if (onRemoveParagraph != null) {
                        IconButton(
                            onClick = onRemoveParagraph,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "删除段落", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = paragraphText,
                onValueChange = onTextChange,
                placeholder = { Text("输入段落内容…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (hasImage) {
                InputChip(
                    selected = false,
                    onClick = onRemoveImage,
                    label = { Text("已添加段落插图") },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
}
