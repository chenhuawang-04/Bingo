package com.xty.englishhelper.data.sync

import android.util.Log
import com.xty.englishhelper.data.repository.GitHubSyncRepositoryImpl
import com.xty.englishhelper.domain.repository.SyncProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    private val syncRepository: GitHubSyncRepositoryImpl,
    private val progressTracker: SyncProgressTracker
) {
    companion object {
        private const val TAG = "SyncEngine"
    }

    private val _result = MutableStateFlow<SyncResult?>(null)
    val result: StateFlow<SyncResult?> = _result.asStateFlow()

    suspend fun sync(
        mode: SyncMode,
        onProgress: (SyncProgress) -> Unit = {}
    ): SyncResult {
        progressTracker.reset()
        _result.value = null

        return try {
            when (mode) {
                SyncMode.SMART -> syncRepository.sync { progress ->
                    onProgress(progress)
                }
                SyncMode.FORCE_UPLOAD -> syncRepository.forceUpload { progress ->
                    onProgress(progress)
                }
                SyncMode.FORCE_DOWNLOAD -> syncRepository.forceDownload { progress ->
                    onProgress(progress)
                }
            }

            val syncResult = SyncResult(success = true)
            _result.value = syncResult
            syncResult
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            val syncResult = SyncResult(
                success = false,
                error = e.message
            )
            _result.value = syncResult
            syncResult
        }
    }
}
