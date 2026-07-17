package com.xty.englishhelper.data.repository

import android.util.Base64
import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.json.SettingsSyncJsonModel
import com.xty.englishhelper.data.local.dao.ArticleDao
import com.xty.englishhelper.data.local.dao.QuestionBankDao
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.GitHubApiService
import com.xty.englishhelper.data.remote.dto.GitHubContentResponse
import com.xty.englishhelper.data.remote.dto.GitHubPutRequest
import com.xty.englishhelper.data.remote.dto.GitHubPutResponse
import com.xty.englishhelper.data.sync.DictionaryShardAssembler
import com.xty.englishhelper.data.sync.DictionaryWordMergePlanner
import com.xty.englishhelper.data.sync.DictionaryWordEdgeMergePlanner
import com.xty.englishhelper.data.sync.DictionaryWordPoolMergePlanner
import com.xty.englishhelper.data.sync.DictionaryWordUidNormalizer
import com.xty.englishhelper.data.sync.DictionaryWordUpdatePlanner
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.DictionaryImportExporter
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.PlanRepository
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.usecase.importexport.ExportDictionaryUseCase
import com.xty.englishhelper.domain.usecase.word.EnsureDictionaryWordUidsUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class GitHubSyncRepositoryImplConfigSyncTest {

    @Test
    fun `downloadRequiredSettingsSnapshot throws when config repo is missing settings file`() = runTest {
        val gitHubApi = mockk<GitHubApiService>()
        val settingsDataStore = mockk<SettingsDataStore>()
        val repository = subject(
            gitHubApi = gitHubApi,
            settingsDataStore = settingsDataStore
        )

        coEvery { settingsDataStore.getGitHubPat() } returns "token"
        coEvery {
            gitHubApi.getContent("Bearer token", "xty", "config-repo", "settings.json")
        } returns Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType())
        )

        val error = expectIllegalState {
            repository.downloadRequiredSettingsSnapshot(
                GitHubRepoTarget(owner = "xty", repo = "config-repo")
            )
        }

        assertTrue(error.message.orEmpty().contains("缺少 settings.json"))
    }

    @Test
    fun `syncSettingsSmart uploads local snapshot when cloud snapshot is older`() = runTest {
        mockkStatic(Base64::class)
        try {
            val gitHubApi = mockk<GitHubApiService>()
            val settingsDataStore = mockk<SettingsDataStore>()
            val repository = subject(
                gitHubApi = gitHubApi,
                settingsDataStore = settingsDataStore
            )
            val target = GitHubRepoTarget(owner = "xty", repo = "config-repo")
            val localSnapshot = SettingsSyncJsonModel(exportedAt = 200L)
            val cloudSnapshot = SettingsSyncJsonModel(exportedAt = 100L)
            val cloudJson = Moshi.Builder().build()
                .adapter(SettingsSyncJsonModel::class.java)
                .toJson(cloudSnapshot)
            val putRequest = slot<GitHubPutRequest>()

            every { Base64.encodeToString(any(), Base64.NO_WRAP) } returns "encoded-settings-json"
            coEvery { settingsDataStore.getGitHubPat() } returns "token"
            coEvery { settingsDataStore.exportSyncSnapshot(any(), any()) } returns localSnapshot
            coEvery {
                gitHubApi.getContent("Bearer token", "xty", "config-repo", "settings.json")
            } returnsMany listOf(
                Response.success(
                    GitHubContentResponse(
                        name = "settings.json",
                        path = "settings.json",
                        sha = "cloud-sha",
                        content = cloudJson
                    )
                ),
                Response.success(
                    GitHubContentResponse(
                        name = "settings.json",
                        path = "settings.json",
                        sha = "cloud-sha"
                    )
                )
            )
            coEvery {
                gitHubApi.putContent(
                    "Bearer token",
                    "xty",
                    "config-repo",
                    "settings.json",
                    capture(putRequest)
                )
            } returns Response.success(GitHubPutResponse())

            repository.syncSettingsSmart(target)

            assertEquals("cloud-sha", putRequest.captured.sha)
            assertEquals("encoded-settings-json", putRequest.captured.content)
            coVerify(exactly = 0) { settingsDataStore.importSyncSnapshot(any()) }
        } finally {
            unmockkStatic(Base64::class)
        }
    }

    @Test
    fun `syncSettingsSmart imports cloud snapshot when cloud is newer`() = runTest {
        val gitHubApi = mockk<GitHubApiService>()
        val settingsDataStore = mockk<SettingsDataStore>()
        val repository = subject(
            gitHubApi = gitHubApi,
            settingsDataStore = settingsDataStore
        )
        val target = GitHubRepoTarget(owner = "xty", repo = "config-repo")
        val localSnapshot = SettingsSyncJsonModel(exportedAt = 100L)
        val cloudSnapshot = SettingsSyncJsonModel(exportedAt = 200L)
        val cloudJson = Moshi.Builder().build()
            .adapter(SettingsSyncJsonModel::class.java)
            .toJson(cloudSnapshot)
        val importedSnapshot = slot<SettingsSyncJsonModel>()

        coEvery { settingsDataStore.getGitHubPat() } returns "token"
        coEvery { settingsDataStore.exportSyncSnapshot(any(), any()) } returns localSnapshot
        coJustRun { settingsDataStore.importSyncSnapshot(capture(importedSnapshot)) }
        coEvery {
            gitHubApi.getContent("Bearer token", "xty", "config-repo", "settings.json")
        } returns Response.success(
            GitHubContentResponse(
                name = "settings.json",
                path = "settings.json",
                sha = "cloud-sha",
                content = cloudJson
            )
        )

        repository.syncSettingsSmart(target)

        assertEquals(200L, importedSnapshot.captured.exportedAt)
        coVerify(exactly = 1) { settingsDataStore.importSyncSnapshot(any()) }
        coVerify(exactly = 0) { gitHubApi.putContent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `syncSettingsSmart imports cloud snapshot when local snapshot is uninitialized`() = runTest {
        val gitHubApi = mockk<GitHubApiService>()
        val settingsDataStore = mockk<SettingsDataStore>()
        val repository = subject(
            gitHubApi = gitHubApi,
            settingsDataStore = settingsDataStore
        )
        val target = GitHubRepoTarget(owner = "xty", repo = "config-repo")
        val localSnapshot = SettingsSyncJsonModel(exportedAt = 0L)
        val cloudSnapshot = SettingsSyncJsonModel(exportedAt = 100L)
        val cloudJson = Moshi.Builder().build()
            .adapter(SettingsSyncJsonModel::class.java)
            .toJson(cloudSnapshot)

        coEvery { settingsDataStore.getGitHubPat() } returns "token"
        coEvery { settingsDataStore.exportSyncSnapshot(any(), any()) } returns localSnapshot
        coJustRun { settingsDataStore.importSyncSnapshot(any()) }
        coEvery {
            gitHubApi.getContent("Bearer token", "xty", "config-repo", "settings.json")
        } returns Response.success(
            GitHubContentResponse(
                name = "settings.json",
                path = "settings.json",
                sha = "cloud-sha",
                content = cloudJson
            )
        )

        repository.syncSettingsSmart(target)

        coVerify(exactly = 1) { settingsDataStore.importSyncSnapshot(match { it.exportedAt == 100L }) }
        coVerify(exactly = 0) { settingsDataStore.markSettingsSyncUpdatedAt(any()) }
        coVerify(exactly = 0) { gitHubApi.putContent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `syncSettingsSmart stamps and uploads local snapshot when cloud snapshot is missing`() = runTest {
        mockkStatic(Base64::class)
        try {
            val gitHubApi = mockk<GitHubApiService>()
            val settingsDataStore = mockk<SettingsDataStore>()
            val repository = subject(
                gitHubApi = gitHubApi,
                settingsDataStore = settingsDataStore
            )
            val target = GitHubRepoTarget(owner = "xty", repo = "config-repo")
            val initialSnapshot = SettingsSyncJsonModel(exportedAt = 0L)
            val stampedSnapshot = SettingsSyncJsonModel(exportedAt = 300L)
            val putRequest = slot<GitHubPutRequest>()

            every { Base64.encodeToString(any(), Base64.NO_WRAP) } returns "encoded-settings-json"
            coEvery { settingsDataStore.getGitHubPat() } returns "token"
            coEvery { settingsDataStore.exportSyncSnapshot(any(), any()) } returnsMany listOf(
                initialSnapshot,
                stampedSnapshot
            )
            coJustRun { settingsDataStore.markSettingsSyncUpdatedAt(any()) }
            coEvery {
                gitHubApi.getContent("Bearer token", "xty", "config-repo", "settings.json")
            } returnsMany listOf(
                Response.error(404, "{}".toResponseBody("application/json".toMediaType())),
                Response.error(404, "{}".toResponseBody("application/json".toMediaType()))
            )
            coEvery {
                gitHubApi.putContent(
                    "Bearer token",
                    "xty",
                    "config-repo",
                    "settings.json",
                    capture(putRequest)
                )
            } returns Response.success(GitHubPutResponse())

            repository.syncSettingsSmart(target)

            coVerify(exactly = 1) { settingsDataStore.markSettingsSyncUpdatedAt(any()) }
            assertEquals("encoded-settings-json", putRequest.captured.content)
        } finally {
            unmockkStatic(Base64::class)
        }
    }

    private fun subject(
        gitHubApi: GitHubApiService = mockk(),
        settingsDataStore: SettingsDataStore = mockk()
    ): GitHubSyncRepositoryImpl = GitHubSyncRepositoryImpl(
        gitHubApi = gitHubApi,
        settingsDataStore = settingsDataStore,
        dictionaryRepository = mockk<DictionaryRepository>(relaxed = true),
        wordRepository = mockk<WordRepository>(relaxed = true),
        unitRepository = mockk<UnitRepository>(relaxed = true),
        studyRepository = mockk<StudyRepository>(relaxed = true),
        articleRepository = mockk<ArticleRepository>(relaxed = true),
        articleDao = mockk<ArticleDao>(relaxed = true),
        wordPoolRepository = mockk<WordPoolRepository>(relaxed = true),
        wordPoolDao = mockk<WordPoolDao>(relaxed = true),
        wordEdgeDao = mockk<WordEdgeDao>(relaxed = true),
        questionBankDao = mockk<QuestionBankDao>(relaxed = true),
        planRepository = mockk<PlanRepository>(relaxed = true),
        importExporter = mockk<DictionaryImportExporter>(relaxed = true),
        exportDictionary = mockk<ExportDictionaryUseCase>(relaxed = true),
        transactionRunner = mockk<TransactionRunner>(relaxed = true),
        moshi = Moshi.Builder().build(),
        dictionaryShardAssembler = mockk<DictionaryShardAssembler>(relaxed = true),
        dictionaryWordMergePlanner = mockk<DictionaryWordMergePlanner>(relaxed = true),
        dictionaryWordEdgeMergePlanner = mockk<DictionaryWordEdgeMergePlanner>(relaxed = true),
        dictionaryWordUpdatePlanner = mockk<DictionaryWordUpdatePlanner>(relaxed = true),
        dictionaryWordPoolMergePlanner = mockk<DictionaryWordPoolMergePlanner>(relaxed = true),
        dictionaryWordUidNormalizer = mockk<DictionaryWordUidNormalizer>(relaxed = true),
        ensureDictionaryWordUids = mockk<EnsureDictionaryWordUidsUseCase>(relaxed = true),
        wordPhraseRepository = mockk(relaxed = true)
    )

    private suspend fun expectIllegalState(block: suspend () -> Unit): IllegalStateException {
        return try {
            block()
            throw AssertionError("Expected IllegalStateException")
        } catch (error: IllegalStateException) {
            error
        }
    }
}
