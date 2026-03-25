package com.xty.englishhelper.ui.designsystem.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ArticleTypography {
    val ReadingEnglish = FontFamily.Serif

    val ReaderBody = TextStyle(
        fontFamily = ReadingEnglish,
        fontSize = 18.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp
    )

    val ReaderTitle = TextStyle(
        fontFamily = ReadingEnglish,
        fontSize = 30.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    )

    val ReaderMeta = TextStyle(
        fontFamily = ReadingEnglish,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    )

    val ReaderQuote = TextStyle(
        fontFamily = ReadingEnglish,
        fontSize = 17.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        letterSpacing = 0.1.sp
    )

    val ReaderHeading = TextStyle(
        fontFamily = ReadingEnglish,
        fontSize = 22.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp
    )

    val QuestionStem = TextStyle(
        fontFamily = ReadingEnglish,
        fontSize = 17.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.05.sp
    )

    val QuestionOption = TextStyle(
        fontFamily = ReadingEnglish,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.05.sp
    )

    val QuestionSupport = TextStyle(
        fontFamily = ReadingEnglish,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.04.sp
    )

    val ParagraphSpacing = 24.dp
    val HorizontalPadding = 18.dp
}
