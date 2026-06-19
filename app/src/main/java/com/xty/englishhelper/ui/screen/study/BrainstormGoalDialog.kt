package com.xty.englishhelper.ui.screen.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.R

@Composable
fun BrainstormGoalDialog(
    defaultTarget: Int,
    onConfirm: (Int) -> Unit
) {
    var targetText by remember { mutableStateOf(defaultTarget.toString()) }
    val target = targetText.toIntOrNull() ?: defaultTarget
    val presets = listOf(50, 100, 200, 300)

    AlertDialog(
        onDismissRequest = { /* Cannot dismiss */ },
        title = {
            Text(
                text = stringResource(R.string.study_goal_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.study_goal_question),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.study_goal_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 快捷目标
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = target == preset,
                            onClick = { targetText = preset.toString() },
                            label = { Text("$preset") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                            targetText = newValue
                        }
                    },
                    label = { Text(stringResource(R.string.study_goal_target)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.study_goal_reached_hint),
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
                Text(stringResource(R.string.common_confirm))
            }
        }
    )
}
