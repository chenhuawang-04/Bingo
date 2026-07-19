package com.xty.englishhelper.domain.repository

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseName: String,
    val releaseUrl: String,
    val prerelease: Boolean,
    val updateAvailable: Boolean
)

interface AppUpdateRepository {
    suspend fun checkForUpdate(currentVersion: String, includePrereleases: Boolean): AppUpdateInfo?
}
