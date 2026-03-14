package com.xty.englishhelper.ui.screen.article

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer

@OptIn(ExperimentalMaterial3Api::class)
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

    // Per-paragraph image picker: store index to assign image
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
                        enabled = !state.isSaving && state.title.isNotBlank() &&
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
            maxWidth = 800.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                item {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Summary
                item {
                    OutlinedTextField(
                        value = state.summary,
                        onValueChange = viewModel::onSummaryChange,
                        label = { Text("梗概（可选）") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Author + Source row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.author,
                            onValueChange = viewModel::onAuthorChange,
                            label = { Text("作者") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.source,
                            onValueChange = viewModel::onSourceChange,
                            label = { Text("来源") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Domain
                item {
                    OutlinedTextField(
                        value = state.domain,
                        onValueChange = viewModel::onDomainChange,
                        label = { Text("领域（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Cover image
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { coverPickerLauncher.launch("image/*") }) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  封面图片")
                        }
                        if (state.coverImageUri != null) {
                            InputChip(
                                selected = false,
                                onClick = { viewModel.onCoverImageSelected(null) },
                                label = { Text("已选封面") },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }

                item { HorizontalDivider() }

                // Paragraphs
                itemsIndexed(state.paragraphs, key = { index, _ -> index }) { index, paragraph ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "段落 ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        paragraphImageIndex.intValue = index
                                        paragraphImagePicker.launch("image/*")
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = "插图", modifier = Modifier.size(18.dp))
                                }
                                if (state.paragraphs.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.removeParagraph(index) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "删除段落", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = paragraph.text,
                            onValueChange = { viewModel.onParagraphTextChange(index, it) },
                            placeholder = { Text("输入段落内容…") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (paragraph.imageUri != null) {
                            InputChip(
                                selected = false,
                                onClick = { viewModel.onParagraphImageSelected(index, null) },
                                label = { Text("段落插图") },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }

                // Add paragraph + Paste buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = viewModel::addParagraph) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  添加段落")
                        }
                        OutlinedButton(onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                viewModel.pasteFullText(clipText)
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  粘贴全文")
                        }
                    }
                }

                item { HorizontalDivider() }

                // OCR section
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                }

                item {
                    AnimatedVisibility(visible = state.isCompressing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
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
                }

                if (state.imageUris.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.imageUris.forEachIndexed { index, uri ->
                                InputChip(
                                    selected = false,
                                    onClick = { viewModel.removeImage(index) },
                                    label = { Text("图片 ${index + 1}") },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
