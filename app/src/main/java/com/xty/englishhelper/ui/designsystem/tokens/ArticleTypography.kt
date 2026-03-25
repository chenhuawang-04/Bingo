package com.xty.englishhelper.ui.designsystem.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ArticleTypography {
    val ReaderBody = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 18.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Normal
    )

    val ReaderTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold
    )

    val ReaderMeta = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    )

    val ReaderQuote = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 17.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
    )

    val ReaderHeading = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold
    )

    val ParagraphSpacing = 24.dp
    val HorizontalPadding = 18.dp
}
