package com.xty.englishhelper.data.debug

data class AiDebugEvent(
    val url: String,
    val method: String,
    val statusCode: Int,
    val requestJson: String,
    val responseJson: String,
    val timestamp: Long = System.currentTimeMillis()
)
