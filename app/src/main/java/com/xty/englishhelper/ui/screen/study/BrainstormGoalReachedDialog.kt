package com.xty.englishhelper.ui.screen.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.R
import kotlin.math.max

@Composable
fun BrainstormGoalReachedDialog(
    learned: Int,
    target: Int,
    dueLearned: Int,
    newLearned: Int,
    onContinue: () -> Unit,
    onExit: () -> Unit
) {
    val ratio = (learned.toFloat() / max(target, 1)).coerceIn(0f, 1f)

    AlertDialog(
        onDismissRequest = { /* Cannot dismiss */ },
        title = {
            Text(
                text = stringResource(R.string.study_goal_reached_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.size(120.dp),
                        strokeWidth = 8.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$learned",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.study_goal_target_label, target),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GoalStat(label = stringResource(R.string.study_goal_review), value = dueLearned)
                    GoalStat(label = stringResource(R.string.study_goal_new), value = newLearned)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onExit) {
                    Text(stringResource(R.string.study_goal_end_today))
                }
                TextButton(onClick = onContinue) {
                    Text(stringResource(R.string.study_goal_continue))
                }
            }
        }
    )
}

@Composable
private fun GoalStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
