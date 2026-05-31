package com.xty.englishhelper.ui.screen.study

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun BrainstormGoalDialog(
    defaultTarget: Int,
    onConfirm: (Int) -> Unit
) {
    var targetText by remember { mutableStateOf(defaultTarget.toString()) }
    val target = targetText.toIntOrNull() ?: defaultTarget

    AlertDialog(
        onDismissRequest = { /* Cannot dismiss */ },
        title = {
            Text(
                text = "🧠 头脑风暴 · 今日目标",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "今天计划背多少个词？",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "（包括复习词和新词）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { newValue ->
                        // Only allow digits
                        if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                            targetText = newValue
                        }
                    },
                    label = { Text("目标数量") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "背完当前关联词组后才停止",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(target) },
                enabled = target > 0
            ) {
                Text("确认")
            }
        }
    )
}
