package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.GitHubApiService
import com.xty.englishhelper.domain.repository.AppUpdateInfo
import com.xty.englishhelper.domain.repository.AppUpdateRepository
import com.xty.englishhelper.domain.update.isNewerVersion
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val api: GitHubApiService
) : AppUpdateRepository {

    override suspend fun checkForUpdate(
        currentVersion: String,
        includePrereleases: Boolean
    ): AppUpdateInfo? {
        val response = api.getReleases(OWNER, REPOSITORY)
        if (!response.isSuccessful) {
            throw IOException("GitHub release request failed: HTTP ${response.code()}")
        }
        val release = response.body().orEmpty()
            .withIndex()
            .filter { (_, candidate) ->
                !candidate.draft &&
                    (includePrereleases || !candidate.prerelease) &&
                    candidate.tagName.isNotBlank()
            }
            .maxWithOrNull(
                compareBy<IndexedValue<com.xty.englishhelper.data.remote.dto.GitHubReleaseResponse>> {
                    it.value.publishedAt.orEmpty()
                }.thenBy { -it.index }
            )
            ?.value
            ?: return null
        return AppUpdateInfo(
            latestVersion = release.tagName,
            releaseName = release.name?.takeIf(String::isNotBlank) ?: release.tagName,
            releaseUrl = release.htmlUrl,
            prerelease = release.prerelease,
            updateAvailable = isNewerVersion(release.tagName, currentVersion)
        )
    }

    private companion object {
        const val OWNER = "chenhuawang-04"
        const val REPOSITORY = "Bingo"
    }
}
