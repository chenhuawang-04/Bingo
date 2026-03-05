package com.xty.englishhelper.domain.usecase.sync

import com.xty.englishhelper.data.json.SyncManifest
import com.xty.englishhelper.domain.repository.CloudSyncRepository
import com.xty.englishhelper.domain.repository.SyncProgress
import javax.inject.Inject

class SyncUseCase @Inject constructor(
    private val repository: CloudSyncRepository
) {
    suspend operator fun invoke(onProgress: (SyncProgress) -> Unit) {
        repository.sync(onProgress)
    }
}

class ForceUploadUseCase @Inject constructor(
    private val repository: CloudSyncRepository
) {
    suspend operator fun invoke(onProgress: (SyncProgress) -> Unit) {
        repository.forceUpload(onProgress)
    }
}

class ForceDownloadUseCase @Inject constructor(
    private val repository: CloudSyncRepository
) {
    suspend operator fun invoke(onProgress: (SyncProgress) -> Unit) {
        repository.forceDownload(onProgress)
    }
}

class TestSyncConnectionUseCase @Inject constructor(
    private val repository: CloudSyncRepository
) {
    suspend operator fun invoke(): Boolean {
        return repository.testConnection()
    }
}

class GetCloudManifestUseCase @Inject constructor(
    private val repository: CloudSyncRepository
) {
    suspend operator fun invoke(): SyncManifest? {
        return repository.getCloudManifest()
    }
}
