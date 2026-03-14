package com.xty.englishhelper.ui.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiDebugDialogHost(viewModel: AiDebugViewModel = hiltViewModel()) {
    val queue by viewModel.queue.collectAsState()
    val current = queue.firstOrNull() ?: return

    val timeText = rememberDebugTimestamp(current.timestamp)
    AlertDialog(
        onDismissRequest = viewModel::dismissCurrent,
        title = { Text("AI 调试") },
        text = {
            Box(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .padding(top = 4.dp)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${current.method}  ${current.statusCode}  $timeText\n${current.url}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    DebugJsonBlock(title = "请求 JSON", json = current.requestJson)
                    DebugJsonBlock(title = "响应 JSON", json = current.responseJson)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::dismissCurrent) {
                Text("关闭")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::clearAll) {
                Text("清空队列")
            }
        }
    )
}

@Composable
private fun DebugJsonBlock(title: String, json: String) {
    val horizontalScroll = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        SelectionContainer {
            Text(
                text = if (json.isBlank()) "{}" else json,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScroll)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun rememberDebugTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
