package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.GitHubApiService
import com.xty.englishhelper.data.remote.dto.GitHubReleaseResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AppUpdateRepositoryImplTest {

    private val api = mockk<GitHubApiService>()
    private val repository = AppUpdateRepositoryImpl(api)

    @Test
    fun `stable checks skip drafts and prereleases`() = runTest {
        coEvery { api.getReleases(any(), any(), any()) } returns Response.success(
            listOf(
                release("v9.0.0-internal", publishedAt = "2026-07-20T00:00:00Z", draft = true),
                release("v8.2.0-beta.1", publishedAt = "2026-07-19T00:00:00Z", prerelease = true),
                release("v8.1.5", publishedAt = "2026-07-18T00:00:00Z")
            )
        )

        val result = repository.checkForUpdate("8.1.4", includePrereleases = false)

        assertEquals("v8.1.5", result?.latestVersion)
        assertTrue(result?.updateAvailable == true)
        assertFalse(result?.prerelease == true)
    }

    @Test
    fun `prerelease checks select the newest non-draft release`() = runTest {
        coEvery { api.getReleases(any(), any(), any()) } returns Response.success(
            listOf(
                release("v8.1.5", publishedAt = "2026-07-18T00:00:00Z"),
                release("v8.2.0-rc.1", publishedAt = "2026-07-19T00:00:00Z", prerelease = true)
            )
        )

        val result = repository.checkForUpdate("8.1.4", includePrereleases = true)

        assertEquals("v8.2.0-rc.1", result?.latestVersion)
        assertTrue(result?.prerelease == true)
    }

    private fun release(
        tag: String,
        publishedAt: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false
    ) = GitHubReleaseResponse(
        tagName = tag,
        name = tag,
        htmlUrl = "https://github.com/chenhuawang-04/Bingo/releases/tag/$tag",
        draft = draft,
        prerelease = prerelease,
        publishedAt = publishedAt
    )
}
