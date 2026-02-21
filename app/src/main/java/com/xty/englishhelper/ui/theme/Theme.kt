package com.xty.englishhelper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.xty.englishhelper.ui.adaptive.currentWindowWidthClass
import com.xty.englishhelper.ui.designsystem.tokens.DarkSemanticColors
import com.xty.englishhelper.ui.designsystem.tokens.EhDarkColorScheme
import com.xty.englishhelper.ui.designsystem.tokens.EhLightColorScheme
import com.xty.englishhelper.ui.designsystem.tokens.EhSpacing
import com.xty.englishhelper.ui.designsystem.tokens.LightSemanticColors
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSemanticColors
import com.xty.englishhelper.ui.designsystem.tokens.LocalEhSpacing
import com.xty.englishhelper.ui.designsystem.tokens.adaptiveTypography

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
        darkTheme -> EhDarkColorScheme
        else -> EhLightColorScheme
    }

    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val windowWidthClass = currentWindowWidthClass()
    val typography = adaptiveTypography(windowWidthClass)

    CompositionLocalProvider(
        LocalEhSemanticColors provides semanticColors,
        LocalEhSpacing provides EhSpacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

object EhTheme {
    val semanticColors: com.xty.englishhelper.ui.designsystem.tokens.EhSemanticColors
        @Composable get() = LocalEhSemanticColors.current

    val spacing: EhSpacing
        @Composable get() = LocalEhSpacing.current
}
