package com.xty.englishhelper.ui.screen.study

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BrainstormGoalReachedDialog(
    learned: Int,
    target: Int,
    dueLearned: Int,
    newLearned: Int,
    onContinue: () -> Unit,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Cannot dismiss */ },
        title = {
            Text(
                text = "🎉 今日目标已达成！",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "已背 $learned/$target 个词",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "（复习 $dueLearned + 新词 $newLearned）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onExit) {
                    Text("结束今日")
                }
                TextButton(onClick = onContinue) {
                    Text("继续背词")
                }
            }
        }
    )
}
