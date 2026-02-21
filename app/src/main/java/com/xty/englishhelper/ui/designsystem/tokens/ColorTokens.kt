package com.xty.englishhelper.ui.designsystem.tokens

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Primary ──
val InkBlueLt = Color(0xFF2563EB)
val InkBlueDk = Color(0xFF60A5FA)

// ── Secondary ──
val MintTealLt = Color(0xFF0F766E)
val MintTealDk = Color(0xFF5EEAD4)

// ── Accent ──
val AccentWarnLt = Color(0xFFF59E0B)
val AccentWarnDk = Color(0xFFFBBF24)
val AccentErrorLt = Color(0xFFDC2626)
val AccentErrorDk = Color(0xFFF87171)

// ── Neutral ──
val Neutral50 = Color(0xFFFAFBFC)
val Neutral100 = Color(0xFFF3F5F7)
val Neutral200 = Color(0xFFE7EBEF)
val Neutral600 = Color(0xFF4B5563)
val Neutral900 = Color(0xFF111827)

val Neutral50Dk = Color(0xFF111827)
val Neutral100Dk = Color(0xFF1F2937)
val Neutral200Dk = Color(0xFF374151)
val Neutral600Dk = Color(0xFF9CA3AF)
val Neutral900Dk = Color(0xFFF3F5F7)

// ── Study rating colors ──
val StudyAgainLt = Color(0xFFDC2626)
val StudyAgainDk = Color(0xFFF87171)
val StudyHardLt = Color(0xFFD97706)
val StudyHardDk = Color(0xFFFBBF24)
val StudyGoodLt = Color(0xFF2563EB)
val StudyGoodDk = Color(0xFF60A5FA)
val StudyEasyLt = Color(0xFF059669)
val StudyEasyDk = Color(0xFF34D399)

// ── Retention indicator colors ──
val RetentionHighLt = Color(0xFF4CAF50)
val RetentionHighDk = Color(0xFF66BB6A)
val RetentionMidLt = Color(0xFFFF9800)
val RetentionMidDk = Color(0xFFFFA726)
val RetentionLowLt = Color(0xFFF44336)
val RetentionLowDk = Color(0xFFEF5350)

// ── Material 3 Color Schemes ──

val EhLightColorScheme = lightColorScheme(
    primary = InkBlueLt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE6FE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = MintTealLt,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF064E45),
    error = AccentErrorLt,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = Neutral50,
    onBackground = Neutral900,
    surface = Neutral100,
    onSurface = Neutral900,
    surfaceVariant = Neutral200,
    onSurfaceVariant = Neutral600,
    outline = Neutral200,
    outlineVariant = Color(0xFFD1D5DB)
)

val EhDarkColorScheme = darkColorScheme(
    primary = InkBlueDk,
    onPrimary = Color(0xFF1E3A5F),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBE6FE),
    secondary = MintTealDk,
    onSecondary = Color(0xFF064E45),
    secondaryContainer = Color(0xFF0F766E),
    onSecondaryContainer = Color(0xFFCCFBF1),
    error = AccentErrorDk,
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Neutral50Dk,
    onBackground = Neutral900Dk,
    surface = Neutral100Dk,
    onSurface = Neutral900Dk,
    surfaceVariant = Neutral200Dk,
    onSurfaceVariant = Neutral600Dk,
    outline = Neutral200Dk,
    outlineVariant = Color(0xFF4B5563)
)

// ── Semantic colors (non-Material slots) ──

@Immutable
data class EhSemanticColors(
    val studyAgain: Color,
    val studyHard: Color,
    val studyGood: Color,
    val studyEasy: Color,
    val retentionHigh: Color,
    val retentionMid: Color,
    val retentionLow: Color,
    val accentWarn: Color
)

val LightSemanticColors = EhSemanticColors(
    studyAgain = StudyAgainLt,
    studyHard = StudyHardLt,
    studyGood = StudyGoodLt,
    studyEasy = StudyEasyLt,
    retentionHigh = RetentionHighLt,
    retentionMid = RetentionMidLt,
    retentionLow = RetentionLowLt,
    accentWarn = AccentWarnLt
)

val DarkSemanticColors = EhSemanticColors(
    studyAgain = StudyAgainDk,
    studyHard = StudyHardDk,
    studyGood = StudyGoodDk,
    studyEasy = StudyEasyDk,
    retentionHigh = RetentionHighDk,
    retentionMid = RetentionMidDk,
    retentionLow = RetentionLowDk,
    accentWarn = AccentWarnDk
)

val LocalEhSemanticColors = staticCompositionLocalOf { LightSemanticColors }
