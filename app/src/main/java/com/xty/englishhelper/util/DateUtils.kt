package com.xty.englishhelper.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    private val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatTimestamp(timestamp: Long): String {
        return displayFormat.format(Date(timestamp))
    }
}
