package com.xty.englishhelper.ui.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ReaderColorScheme(
    val background: Color,
    val surface: Color,
    val title: Color,
    val body: Color,
    val meta: Color,
    val divider: Color,
    val highlight: Color,
    val highlightBorder: Color,
    val translationBg: Color,
    val translationBorder: Color,
    val speakingBg: Color,
    val quoteBar: Color
)

val LightReaderColors = ReaderColorScheme(
    background = Color(0xFFFAFBFC),
    surface = Color(0xFFFFFFFF),
    title = Color(0xFF111827),
    body = Color(0xFF374151),
    meta = Color(0xFF9CA3AF),
    divider = Color(0xFFE7EBEF),
    highlight = Color(0xFFDBEAFE),
    highlightBorder = Color(0xFF2563EB),
    translationBg = Color(0xFFF0FDF4),
    translationBorder = Color(0xFF0F766E),
    speakingBg = Color(0xFFFEF3C7),
    quoteBar = Color(0xFF0F766E)
)

val DarkReaderColors = ReaderColorScheme(
    background = Color(0xFF111827),
    surface = Color(0xFF1F2937),
    title = Color(0xFFF9FAFB),
    body = Color(0xFFD1D5DB),
    meta = Color(0xFF6B7280),
    divider = Color(0xFF374151),
    highlight = Color(0xFF1E3A5F),
    highlightBorder = Color(0xFF60A5FA),
    translationBg = Color(0xFF064E45),
    translationBorder = Color(0xFF5EEAD4),
    speakingBg = Color(0xFF422006),
    quoteBar = Color(0xFF5EEAD4)
)

val LocalReaderColors = staticCompositionLocalOf { LightReaderColors }
