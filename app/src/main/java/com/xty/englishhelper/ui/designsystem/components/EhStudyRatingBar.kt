package com.xty.englishhelper.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing
import com.xty.englishhelper.ui.theme.EhTheme

data class RatingOption(
    val label: String,
    val intervalText: String?,
    val color: Color,
    val onContentColor: Color = Color.White
)

@Composable
fun EhStudyRatingBar(
    options: List<RatingOption>,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalEhSpacing.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        options.forEachIndexed { index, option ->
            Button(
                onClick = { onRate(index) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = option.color,
                    contentColor = option.onContentColor
                ),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (option.intervalText != null) {
                        Text(
                            text = option.intervalText,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun defaultRatingOptions(
    previewIntervals: Map<Int, String> = emptyMap()
): List<RatingOption> {
    val semantic = EhTheme.semanticColors
    return listOf(
        RatingOption("重来", previewIntervals[0], semantic.studyAgain),
        RatingOption("困难", previewIntervals[1], semantic.studyHard),
        RatingOption("良好", previewIntervals[2], semantic.studyGood),
        RatingOption("简单", previewIntervals[3], semantic.studyEasy)
    )
}
