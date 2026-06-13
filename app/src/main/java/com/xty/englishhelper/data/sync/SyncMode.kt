package com.xty.englishhelper.data.sync

enum class SyncMode {
    SMART,
    FORCE_UPLOAD,
    FORCE_DOWNLOAD
}

data class SyncResult(
    val success: Boolean,
    val added: Int = 0,
    val updated: Int = 0,
    val error: String? = null
)
