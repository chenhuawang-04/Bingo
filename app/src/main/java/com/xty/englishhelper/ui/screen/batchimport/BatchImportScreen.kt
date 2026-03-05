package com.xty.englishhelper.ui.screen.batchimport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Image selection
            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.imageUris.isEmpty()) "选择图片" else "已选 ${state.imageUris.size} 张图片，点击添加更多")
            }

            // Show selected images with remove buttons
            if (state.imageUris.isNotEmpty()) {
                state.imageUris.forEachIndexed { index, _ ->
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
            }

            // Conditions input
            OutlinedTextField(
                value = state.conditions,
                onValueChange = viewModel::onConditionsChange,
                label = { Text("提取条件") },
                placeholder = { Text("如：蓝色字体、黑体、标题中的") },
                supportingText = { Text("描述需要提取的单词特征") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.availableUnits.isNotEmpty()) {
                UnitSelector(
                    state = state,
                    onToggleUnit = viewModel::toggleUnitSelection
                )
            }

            // Extract button
            Button(
                onClick = {
                    viewModel.extractWords { uri ->
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("无法读取图片")
                    }
                },
                enabled = !state.isExtracting && state.imageUris.isNotEmpty() && state.conditions.isNotBlank(),
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

            // Extracted words preview
            if (state.extractedWords.isNotEmpty()) {
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val checkedCount = state.extractedWords.count { it.checked }
                    Text(
                        "提取到 ${state.extractedWords.size} 个单词（已选 $checkedCount 个）",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(state.extractedWords) { index, word ->
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
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Import progress
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

                // Import button
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
