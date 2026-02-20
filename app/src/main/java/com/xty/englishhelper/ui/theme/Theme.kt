package com.xty.englishhelper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Gray99,
    primaryContainer = Blue90,
    onPrimaryContainer = DarkBlue40,
    secondary = Teal40,
    onSecondary = Gray99,
    background = Gray99,
    onBackground = Gray10,
    surface = Gray95,
    onSurface = Gray10,
    error = Red40,
    onError = Gray99
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Gray10,
    primaryContainer = DarkBlue40,
    onPrimaryContainer = Blue90,
    secondary = Teal80,
    onSecondary = Gray10,
    background = Gray10,
    onBackground = Gray90,
    surface = Gray20,
    onSurface = Gray90,
    error = Red80,
    onError = Gray10
)

@Composable
fun EnglishHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
