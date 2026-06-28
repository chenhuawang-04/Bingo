package com.xty.englishhelper.ui.screen.importexport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.ui.components.topbar.AppTopBarBackButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    onBack: () -> Unit,
    viewModel: ImportExportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var exportTarget by remember { mutableStateOf<Dictionary?>(null) }

    val importDictionaryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFromUri(context, it) }
    }

    val exportDictionaryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { target ->
            exportTarget?.let { dict ->
                viewModel.exportDictionary(context, dict, target)
                exportTarget = null
            }
        }
    }

    val importPlanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importPlanFromUri(context, it) }
    }

    val exportPlanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportPlanToUri(context, it) }
    }

    LaunchedEffect(state.message, state.error) {
        (state.message ?: state.error)?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    AppTopBarEffect(
        title = { Text(stringResource(R.string.import_export_title)) },
        navigationIcon = { AppTopBarBackButton(onBack) }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        EhMaxWidthContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            maxWidth = 560.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(stringResource(R.string.import_dict_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.import_dict_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { importDictionaryLauncher.launch("application/json") },
                                    enabled = !state.isLoading
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.common_import))
                                    Text("  " + stringResource(R.string.import_dict_select))
                                }
                            }
                        }
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(stringResource(R.string.import_plan_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.import_plan_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { importPlanLauncher.launch("application/json") },
                                    enabled = !state.isLoading
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.common_import))
                                    Text("  " + stringResource(R.string.import_plan_select))
                                }
                            }
                        }
                    }

                    item {
                        Text(stringResource(R.string.export_dict_title), style = MaterialTheme.typography.titleMedium)
                        if (state.dictionaries.isEmpty()) {
                            Text(
                                stringResource(R.string.export_no_dict),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(state.dictionaries) { dict ->
                        ListItem(
                            headlineContent = { Text(dict.name) },
                            supportingContent = { Text(stringResource(R.string.home_word_count, dict.wordCount)) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        exportTarget = dict
                                        exportDictionaryLauncher.launch("${dict.name}.json")
                                    },
                                    enabled = !state.isLoading
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.common_export))
                                }
                            },
                            modifier = Modifier.clickable(enabled = !state.isLoading) {
                                exportTarget = dict
                                exportDictionaryLauncher.launch("${dict.name}.json")
                            }
                        )
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(stringResource(R.string.export_plan_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.export_plan_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { exportPlanLauncher.launch("plan_backup.json") },
                                    enabled = !state.isLoading
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.common_export))
                                    Text("  " + stringResource(R.string.export_plan_button))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
