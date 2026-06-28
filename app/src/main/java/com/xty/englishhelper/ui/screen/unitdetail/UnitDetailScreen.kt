package com.xty.englishhelper.ui.screen.unitdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.ui.components.ConfirmDialog
import com.xty.englishhelper.ui.components.EmptyState
import com.xty.englishhelper.ui.components.LoadingIndicator
import com.xty.englishhelper.ui.components.WordListItem
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitDetailScreen(
    onBack: () -> Unit,
    onWordClick: (wordId: Long, dictionaryId: Long) -> Unit,
    viewModel: UnitDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    AppTopBarEffect(
        title = { Text(state.unit?.name ?: stringResource(R.string.unit_default_name)) },
        navigationIcon = { AppTopBarBackButton(onBack) },
        actions = {
            IconButton(onClick = viewModel::showAddWordsDialog) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.unit_manage_words))
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.unit_rename)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.unit_rename)) },
                    onClick = {
                        showMenu = false
                        viewModel.showRenameDialog()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.unit_set_repeat_count)) },
                    onClick = {
                        showMenu = false
                        viewModel.showRepeatCountDialog()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.unit_delete)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showMenu = false
                        viewModel.showDeleteConfirm()
                    }
                )
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            state.unit?.let { unit ->
                Text(
                    text = stringResource(R.string.unit_repeat_and_word_count, unit.defaultRepeatCount, state.wordsInUnit.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                state.isLoading -> LoadingIndicator()
                state.wordsInUnit.isEmpty() -> EmptyState(
                    message = stringResource(R.string.unit_empty_hint)
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(state.wordsInUnit, key = { it.id }) { word ->
                            WordListItem(
                                word = word,
                                onClick = {
                                    state.unit?.let {
                                        onWordClick(word.id, it.dictionaryId)
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    // Rename dialog
    if (state.showRenameDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameDialog,
            title = { Text(stringResource(R.string.unit_rename_title)) },
            text = {
                OutlinedTextField(
                    value = state.renameText,
                    onValueChange = viewModel::onRenameTextChange,
                    label = { Text(stringResource(R.string.unit_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRename,
                    enabled = state.renameText.isNotBlank()
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRenameDialog) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Repeat count dialog
    if (state.showRepeatCountDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRepeatCountDialog,
            title = { Text(stringResource(R.string.unit_set_repeat_count)) },
            text = {
                OutlinedTextField(
                    value = state.repeatCountText,
                    onValueChange = viewModel::onRepeatCountTextChange,
                    label = { Text(stringResource(R.string.unit_repeat_count)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRepeatCount,
                    enabled = (state.repeatCountText.toIntOrNull() ?: 0) >= 1
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRepeatCountDialog) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Add words dialog
    if (state.showAddWordsDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAddWordsDialog,
            title = { Text(stringResource(R.string.unit_manage_words)) },
            text = {
                if (state.allWordsInDictionary.isEmpty()) {
                    Text(stringResource(R.string.unit_no_words))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(state.allWordsInDictionary, key = { it.id }) { word ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleWordSelection(word.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = word.id in state.addWordsSelection,
                                    onCheckedChange = { viewModel.toggleWordSelection(word.id) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = word.spelling,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (word.meanings.isNotEmpty()) {
                                        Text(
                                            text = word.meanings.joinToString("；") { "${it.pos} ${it.definition}" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAddWords) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAddWordsDialog) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Remove word confirm
    state.removeWordTarget?.let { word ->
        ConfirmDialog(
            title = stringResource(R.string.unit_remove_word),
            message = stringResource(R.string.unit_remove_word_confirm, word.spelling),
            onConfirm = viewModel::confirmRemoveWord,
            onDismiss = viewModel::dismissRemoveWord
        )
    }

    // Delete unit confirm
    if (state.showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.unit_delete),
            message = stringResource(R.string.unit_delete_confirm, state.unit?.name ?: ""),
            onConfirm = { viewModel.confirmDeleteUnit(onBack) },
            onDismiss = viewModel::dismissDeleteConfirm
        )
    }
}
