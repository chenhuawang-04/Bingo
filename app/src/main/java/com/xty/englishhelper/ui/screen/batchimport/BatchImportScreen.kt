package com.xty.englishhelper.ui.screen.batchimport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportScreen(
    onBack: () -> Unit,
    viewModel: BatchImportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.importDone) {
        if (state.importDone) {
            snackbarHostState.showSnackbar("导入完成，后台整理已启动")
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照批量导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (state.imageUris.isEmpty()) {
                            "选择图片"
                        } else {
                            "已选 ${state.imageUris.size} 张图片，点击添加更多"
                        }
                    )
                }
            }

            itemsIndexed(state.imageUris, key = { index, uri -> "${uri}#$index" }) { index, _ ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "图片 ${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeImage(index) }) {
                        Icon(Icons.Default.Close, contentDescription = "移除")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.conditions,
                    onValueChange = viewModel::onConditionsChange,
                    label = { Text("提取条件（识别单词模式必填）") },
                    placeholder = { Text("如：蓝色字体、黑体、标题中的词") },
                    supportingText = {
                        Text(
                            if (state.scanMode == BatchScanMode.FULL_SCAN) {
                                "可选。留空时将扫描全部可见英文单词。"
                            } else {
                                "描述需要提取的单词特征。"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.scanMode == BatchScanMode.WORD_LIST,
                        onClick = { viewModel.onScanModeChange(BatchScanMode.WORD_LIST) },
                        label = { Text("识别单词") }
                    )
                    FilterChip(
                        selected = state.scanMode == BatchScanMode.FULL_SCAN,
                        onClick = { viewModel.onScanModeChange(BatchScanMode.FULL_SCAN) },
                        label = { Text("完全扫描") }
                    )
                }
            }

            item {
                Text(
                    text = if (state.scanMode == BatchScanMode.FULL_SCAN) {
                        "完全扫描会额外提取每个词在图片中的关联片段，并在后台整理时作为参考。"
                    } else {
                        "识别单词仅返回词列表，速度更快。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.availableUnits.isNotEmpty()) {
                item {
                    UnitSelector(
                        state = state,
                        onToggleUnit = viewModel::toggleUnitSelection
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.extractWords { uri ->
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: throw IllegalStateException("无法读取图片")
                        }
                    },
                    enabled = !state.isExtracting &&
                        state.imageUris.isNotEmpty() &&
                        (state.scanMode == BatchScanMode.FULL_SCAN || state.conditions.isNotBlank()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isExtracting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("  AI 提取中…")
                    } else {
                        Text("AI 提取")
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
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "正在压缩图片…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.extractedWords.isNotEmpty()) {
                item {
                    HorizontalDivider()
                }

                item {
                    val checkedCount = state.extractedWords.count { it.checked }
                    Text(
                        "提取到 ${state.extractedWords.size} 个单词（已选 $checkedCount 个）",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                itemsIndexed(
                    items = state.extractedWords,
                    key = { index, _ -> "word_$index" }
                ) { index, word ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = word.checked,
                                onCheckedChange = { viewModel.toggleWord(index) }
                            )
                            OutlinedTextField(
                                value = word.spelling,
                                onValueChange = { viewModel.editWord(index, it) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (word.references.isNotEmpty()) {
                            Text(
                                text = "参考：${word.references.joinToString("；")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 48.dp, end = 4.dp, bottom = 6.dp)
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    if (state.isImporting) {
                        val progress = state.importProgress
                        if (progress != null && progress.second > 0) {
                            LinearProgressIndicator(
                                progress = { progress.first.toFloat() / progress.second },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${progress.first}/${progress.second}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = viewModel::importWords,
                        enabled = !state.isImporting && state.extractedWords.any { it.checked },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text("  导入中…")
                        } else {
                            Text("全部导入")
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitSelector(
    state: BatchImportUiState,
    onToggleUnit: (Long) -> Unit
) {
    Column {
        Text("新增单词所属单元", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            state.availableUnits.forEach { unit ->
                FilterChip(
                    selected = unit.id in state.selectedUnitIds,
                    onClick = { onToggleUnit(unit.id) },
                    label = { Text(unit.name) }
                )
            }
        }
    }
}
