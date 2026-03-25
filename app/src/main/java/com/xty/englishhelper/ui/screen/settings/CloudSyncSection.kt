package com.xty.englishhelper.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSyncSection(
    state: CloudSyncState,
    onOwnerChange: (String) -> Unit,
    onRepoChange: (String) -> Unit,
    onPatChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSync: () -> Unit,
    onForceUpload: () -> Unit,
    onForceDownload: () -> Unit,
    onClearError: () -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var confirmDialog by remember { mutableStateOf<ConfirmAction?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text("云同步 (GitHub)", style = MaterialTheme.typography.titleMedium)
        }

        OutlinedTextField(
            value = state.githubOwner,
            onValueChange = onOwnerChange,
            label = { Text("GitHub 用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.githubRepo,
            onValueChange = onRepoChange,
            label = { Text("仓库名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.pat,
            onValueChange = onPatChange,
            label = { Text("Access Token") },
            placeholder = { Text("ghp_...") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Test connection
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onTestConnection) {
                Text("测试连接")
            }
            if (state.connectionTestResult != null) {
                Text(
                    text = state.connectionTestResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.connectionTestResult.contains("成功"))
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        }

        // Cloud manifest info
        if (state.cloudManifest != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "云端数据",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.cloudManifest.dictionaryCount} 本辞书" +
                                if (state.cloudManifest.hasArticles) ", 含文章" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.cloudManifest.syncedAt > 0) {
                        Text(
                            "上次同步: ${formatTime(state.cloudManifest.syncedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.cloudManifest.deviceName.isNotBlank()) {
                        Text(
                            "设备: ${state.cloudManifest.deviceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Last local sync time
        if (state.lastSyncAt > 0) {
            Text(
                "本机上次同步: ${formatTime(state.lastSyncAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Sync button
        Button(
            onClick = { confirmDialog = ConfirmAction.SYNC },
            enabled = !state.isSyncing && state.pat.isNotBlank() && state.githubOwner.isNotBlank() && state.githubRepo.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("智能同步")
        }

        // Progress
        if (state.isSyncing && state.syncProgress != null) {
            Column {
                Text(
                    "${state.syncProgress.phase}: ${state.syncProgress.detail}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                if (state.syncProgress.total > 0) {
                    LinearProgressIndicator(
                        progress = { state.syncProgress.current.toFloat() / state.syncProgress.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        } else if (state.isSyncing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("同步中...", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Error
        if (state.error != null) {
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Advanced options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("高级选项", style = MaterialTheme.typography.bodyMedium)
        }

        AnimatedVisibility(visible = showAdvanced) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { confirmDialog = ConfirmAction.FORCE_UPLOAD },
                    enabled = !state.isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("强制上传")
                }
                OutlinedButton(
                    onClick = { confirmDialog = ConfirmAction.FORCE_DOWNLOAD },
                    enabled = !state.isSyncing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("强制下载")
                }
            }
        }
    }

    // Confirmation dialogs
    confirmDialog?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDialog = null
                    when (action) {
                        ConfirmAction.SYNC -> onSync()
                        ConfirmAction.FORCE_UPLOAD -> onForceUpload()
                        ConfirmAction.FORCE_DOWNLOAD -> onForceDownload()
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

private enum class ConfirmAction(val title: String, val message: String) {
    SYNC(
        "智能同步",
        "将与云端数据智能合并（新增的词汇/文章会被添加，修改过的内容保留更新的版本）。确定继续？"
    ),
    FORCE_UPLOAD(
        "强制上传",
        "将用本地数据完全覆盖云端。确定？"
    ),
    FORCE_DOWNLOAD(
        "强制下载",
        "将用云端数据完全覆盖本地，本地独有的数据会丢失。确定？"
    )
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return "未知"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
