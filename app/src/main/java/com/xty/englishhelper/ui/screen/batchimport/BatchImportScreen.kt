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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect

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
            snackbarHostState.showSnackbar(context.getString(R.string.import_complete))
            onBack()
        }
    }

    AppTopBarEffect(
        title = { Text(stringResource(R.string.batch_import_title)) },
        navigationIcon = { AppTopBarBackButton(onBack) }
    )

    Scaffold(
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
                            stringResource(R.string.batch_import_select_images)
                        } else {
                            stringResource(R.string.batch_import_selected_images, state.imageUris.size)
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
                        text = stringResource(R.string.batch_import_image_number, index + 1),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeImage(index) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.batch_import_remove))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.conditions,
                    onValueChange = viewModel::onConditionsChange,
                    label = { Text(stringResource(R.string.batch_import_conditions)) },
                    placeholder = { Text(stringResource(R.string.batch_import_conditions_hint)) },
                    supportingText = {
                        Text(
                            if (state.scanMode == BatchScanMode.FULL_SCAN) {
                                stringResource(R.string.batch_import_full_scan_optional)
                            } else {
                                stringResource(R.string.batch_import_word_list_hint)
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
                        label = { Text(stringResource(R.string.batch_import_word_list)) }
                    )
                    FilterChip(
                        selected = state.scanMode == BatchScanMode.FULL_SCAN,
                        onClick = { viewModel.onScanModeChange(BatchScanMode.FULL_SCAN) },
                        label = { Text(stringResource(R.string.batch_import_full_scan)) }
                    )
                }
            }

            item {
                Text(
                    text = if (state.scanMode == BatchScanMode.FULL_SCAN) {
                        stringResource(R.string.batch_import_full_scan_desc)
                    } else {
                        stringResource(R.string.batch_import_word_list_desc)
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
                        Text("  ${stringResource(R.string.batch_import_ai_extracting)}")
                    } else {
                        Text(stringResource(R.string.batch_import_ai_extract))
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
                            stringResource(R.string.scan_compressing),
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
                        stringResource(R.string.batch_import_extract_result, state.extractedWords.size, checkedCount),
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
                                text = stringResource(R.string.batch_import_reference, word.references.joinToString("；")),
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
                            Text("  ${stringResource(R.string.batch_import_importing)}")
                        } else {
                            Text(stringResource(R.string.batch_import_all_import))
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
        Text(stringResource(R.string.batch_import_unit_title), style = MaterialTheme.typography.titleMedium)
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
