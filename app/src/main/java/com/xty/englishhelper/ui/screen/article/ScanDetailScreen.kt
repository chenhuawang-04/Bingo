package com.xty.englishhelper.ui.screen.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.ui.designsystem.components.EhMaxWidthContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDetailScreen(
    onBack: () -> Unit,
    viewModel: ScanDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val scanTask = uiState.scanTask
    val status = scanTask?.status
    val (statusLabelRes, statusColor) = when (status) {
        null -> R.string.scan_not_running to MaterialTheme.colorScheme.onSurfaceVariant
        BackgroundTaskStatus.PENDING -> R.string.scan_waiting to MaterialTheme.colorScheme.secondary
        BackgroundTaskStatus.RUNNING -> R.string.scan_running to MaterialTheme.colorScheme.primary
        BackgroundTaskStatus.PAUSED -> R.string.scan_paused to MaterialTheme.colorScheme.tertiary
        BackgroundTaskStatus.SUCCESS -> R.string.scan_completed to MaterialTheme.colorScheme.primary
        BackgroundTaskStatus.FAILED -> R.string.scan_failed to MaterialTheme.colorScheme.error
        BackgroundTaskStatus.CANCELED -> R.string.scan_stopped to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isActive = status == BackgroundTaskStatus.PENDING || status == BackgroundTaskStatus.RUNNING
    val isPaused = status == BackgroundTaskStatus.PAUSED
    val isTerminal = status == BackgroundTaskStatus.SUCCESS ||
        status == BackgroundTaskStatus.FAILED ||
        status == BackgroundTaskStatus.CANCELED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            maxWidth = 720.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                    color = statusColor.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = stringResource(statusLabelRes),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.scan_status),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }

                        if (isActive) {
                            val task = scanTask!!
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    progress = {
                                        if (task.progressTotal > 0) {
                                            task.progressCurrent.toFloat() / task.progressTotal
                                        } else 0f
                                    }
                                )
                                Text(
                                    text = stringResource(R.string.scan_progress_detail, task.progressCurrent, task.progressTotal),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val errorMsg = scanTask?.errorMessage
                        if (status == BackgroundTaskStatus.FAILED && errorMsg != null) {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 3
                            )
                        }

                        HorizontalDivider()

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (status == null || isTerminal) {
                                Button(onClick = viewModel::triggerScan) {
                                    Text(stringResource(R.string.scan_start))
                                }
                            }
                            if (isActive) {
                                Button(
                                    onClick = viewModel::cancelScan,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(stringResource(R.string.common_stop))
                                }
                            }
                            if (isPaused) {
                                Button(onClick = viewModel::resumeScan) {
                                    Text(stringResource(R.string.common_resume))
                                }
                                TextButton(onClick = viewModel::deleteScanTask) {
                                    Text(stringResource(R.string.common_clear))
                                }
                            }
                            if (isActive) {
                                TextButton(onClick = viewModel::pauseScan) {
                                    Text(stringResource(R.string.common_pause))
                                }
                            }
                            if (status == BackgroundTaskStatus.FAILED || status == BackgroundTaskStatus.CANCELED) {
                                TextButton(onClick = viewModel::deleteScanTask) {
                                    Text(stringResource(R.string.common_clear))
                                }
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.scan_config_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
