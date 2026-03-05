package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.data.json.SyncManifest

data class SyncProgress(
    val phase: String,
    val detail: String,
    val current: Int,
    val total: Int
)

interface CloudSyncRepository {
    suspend fun sync(onProgress: (SyncProgress) -> Unit)
    suspend fun forceUpload(onProgress: (SyncProgress) -> Unit)
    suspend fun forceDownload(onProgress: (SyncProgress) -> Unit)
    suspend fun testConnection(): Boolean
    suspend fun getCloudManifest(): SyncManifest?
}
