package com.xty.englishhelper.data.repository.pool

import android.util.Log
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.AnthropicApiClient
import com.xty.englishhelper.data.remote.OpenAiCompatibleApiClient
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.PoolRetryMode
import com.xty.englishhelper.domain.model.WordDetails
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class EdgeReviewerTest {

    @Before
    fun setUpLog() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDownLog() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `parseReviewUpdates keeps edge but drops confidence to zero for remove verdict`() {
        val edge = WordEdgeEntity(
            id = 7L,
            wordIdA = 101L,
            wordIdB = 102L,
            edgeType = "SEMANTIC_SYNONYM",
            dictionaryId = 1L,
            status = "warning",
            confidence = 0.42
        )
        val text = """
            [
              {"i":0,"verdict":"remove","note":"证据不足"},
              {"i":0,"verdict":"adjust","new_status":"core","new_confidence":0.9}
            ]
        """.trimIndent()

        val updates = parseReviewUpdates(text, listOf(edge))

        assertEquals(1, updates.size)
        assertEquals(7L, updates[0].edgeId)
        assertEquals("core", updates[0].newStatus)
        assertEquals(0.9, updates[0].newConfidence, 0.0)

        val removeOnly = parseReviewUpdates("""[{"i":0,"verdict":"remove"}]""", listOf(edge))
        assertEquals(1, removeOnly.size)
        assertEquals("warning", removeOnly[0].newStatus)
        assertEquals(0.0, removeOnly[0].newConfidence, 0.0)
    }

    @Test(expected = RetryableEdgeException::class)
    fun `parseReviewUpdates throws when no valid verdict can be parsed`() {
        val edge = WordEdgeEntity(
            id = 8L,
            wordIdA = 201L,
            wordIdB = 202L,
            edgeType = "SEMANTIC_SYNONYM",
            dictionaryId = 1L,
            status = "warning",
            confidence = 0.35
        )

        parseReviewUpdates("""[{"foo":"bar"}]""", listOf(edge))
    }

    @Test
    fun `reviewEdgesWithAi fails after retries instead of swallowing exhausted purification errors`() = runTest {
        val wordEdgeDao = mockk<WordEdgeDao>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>()
        val anthropicClient = mockk<AnthropicApiClient>(relaxed = true)
        val openAiClient = mockk<OpenAiCompatibleApiClient>()
        val provider = AiApiClientProvider(anthropicClient, openAiClient)
        val reviewer = EdgeReviewer(wordEdgeDao, provider, settingsDataStore)

        coEvery { settingsDataStore.getPoolWindowSize() } returns 1
        coEvery { settingsDataStore.getPoolMaxConcurrent() } returns 1
        coEvery { settingsDataStore.getPoolRequestsPerMinute() } returns 120
        coEvery { settingsDataStore.getPoolRetryMode() } returns PoolRetryMode.AGGRESSIVE
        coEvery { settingsDataStore.getAiResponseUnwrapEnabled() } returns false
        coEvery { settingsDataStore.getAiJsonRepairEnabled() } returns false
        coEvery { settingsDataStore.getAiConfig(AiSettingsScope.REVIEWER) } returns SettingsDataStore.AiConfig(
            providerName = "test-openai",
            provider = AiProvider.OPENAI_COMPATIBLE,
            apiKey = "key",
            model = "gpt-test",
            baseUrl = "https://example.com"
        )
        coEvery {
            openAiClient.sendMessage(any(), any(), any(), any(), any(), any())
        } returns "not-json"

        val edge = WordEdgeEntity(
            id = 9L,
            wordIdA = 301L,
            wordIdB = 302L,
            edgeType = "SEMANTIC_SYNONYM",
            dictionaryId = 1L,
            status = "warning",
            confidence = 0.15
        )
        val domains = listOf(
            WordDetails(id = 301L, dictionaryId = 1L, spelling = "abandon"),
            WordDetails(id = 302L, dictionaryId = 1L, spelling = "forsake")
        )

        val error = runCatching {
            reviewer.reviewEdgesWithAi(edges = listOf(edge), domains = domains)
        }.exceptionOrNull()

        assertTrue("Actual error: $error", error is IllegalStateException)
        assertTrue("Actual error message: ${error?.message}", error?.message?.contains("4 次尝试后仍失败") == true)
        coVerify(exactly = 4) {
            openAiClient.sendMessage(any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            wordEdgeDao.updateEdgeStatus(any(), any(), any())
        }
    }

    @Test
    fun `reviewEdgesWithAi audits all persisted edges including high confidence ones`() = runTest {
        val wordEdgeDao = mockk<WordEdgeDao>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>()
        val anthropicClient = mockk<AnthropicApiClient>(relaxed = true)
        val openAiClient = mockk<OpenAiCompatibleApiClient>()
        val provider = AiApiClientProvider(anthropicClient, openAiClient)
        val reviewer = EdgeReviewer(wordEdgeDao, provider, settingsDataStore)

        coEvery { settingsDataStore.getPoolWindowSize() } returns 1
        coEvery { settingsDataStore.getPoolMaxConcurrent() } returns 1
        coEvery { settingsDataStore.getPoolRequestsPerMinute() } returns 120
        coEvery { settingsDataStore.getPoolRetryMode() } returns PoolRetryMode.AGGRESSIVE
        coEvery { settingsDataStore.getAiResponseUnwrapEnabled() } returns false
        coEvery { settingsDataStore.getAiJsonRepairEnabled() } returns false
        coEvery { settingsDataStore.getAiConfig(AiSettingsScope.REVIEWER) } returns SettingsDataStore.AiConfig(
            providerName = "test-openai",
            provider = AiProvider.OPENAI_COMPATIBLE,
            apiKey = "key",
            model = "gpt-test",
            baseUrl = "https://example.com"
        )
        coEvery {
            openAiClient.sendMessage(any(), any(), any(), any(), any(), any())
        } returns """[{"i":0,"verdict":"keep","note":"关系成立"}]"""

        val edge = WordEdgeEntity(
            id = 10L,
            wordIdA = 311L,
            wordIdB = 312L,
            edgeType = "SEMANTIC_SYNONYM",
            dictionaryId = 1L,
            status = "core",
            confidence = 0.95
        )
        val domains = listOf(
            WordDetails(id = 311L, dictionaryId = 1L, spelling = "rapid"),
            WordDetails(id = 312L, dictionaryId = 1L, spelling = "fast")
        )
        var reviewStart: Pair<Int, Int>? = null
        val progress = mutableListOf<Pair<Int, Int>>()

        val modified = reviewer.reviewEdgesWithAi(
            edges = listOf(edge),
            domains = domains,
            onReviewStart = { totalEdges, totalBatches ->
                reviewStart = totalEdges to totalBatches
            },
            onProgress = { current, total, _ ->
                progress += current to total
            }
        )

        assertFalse(modified)
        assertEquals(1 to 1, reviewStart)
        assertEquals(listOf(0 to 1, 1 to 1), progress)
        coVerify(exactly = 1) {
            openAiClient.sendMessage(any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            wordEdgeDao.updateEdgeStatus(any(), any(), any())
        }
    }

    @Test
    fun `reviewEdgesWithAi persists successful batches before a later batch fails`() = runTest {
        val wordEdgeDao = mockk<WordEdgeDao>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>()
        val anthropicClient = mockk<AnthropicApiClient>(relaxed = true)
        val openAiClient = mockk<OpenAiCompatibleApiClient>()
        val provider = AiApiClientProvider(anthropicClient, openAiClient)
        val reviewer = EdgeReviewer(wordEdgeDao, provider, settingsDataStore)

        coEvery { settingsDataStore.getPoolWindowSize() } returns 1
        coEvery { settingsDataStore.getPoolMaxConcurrent() } returns 1
        coEvery { settingsDataStore.getPoolRequestsPerMinute() } returns 120
        coEvery { settingsDataStore.getPoolRetryMode() } returns PoolRetryMode.AGGRESSIVE
        coEvery { settingsDataStore.getAiResponseUnwrapEnabled() } returns false
        coEvery { settingsDataStore.getAiJsonRepairEnabled() } returns false
        coEvery { settingsDataStore.getAiConfig(AiSettingsScope.REVIEWER) } returns SettingsDataStore.AiConfig(
            providerName = "test-openai",
            provider = AiProvider.OPENAI_COMPATIBLE,
            apiKey = "key",
            model = "gpt-test",
            baseUrl = "https://example.com"
        )
        coEvery {
            openAiClient.sendMessage(any(), any(), any(), any(), any(), any())
        } returnsMany listOf(
            """[{"i":0,"verdict":"adjust","new_status":"core","new_confidence":0.91}]""",
            "not-json",
            "not-json",
            "not-json",
            "not-json"
        )

        val edges = listOf(
            WordEdgeEntity(
                id = 11L,
                wordIdA = 401L,
                wordIdB = 402L,
                edgeType = "SEMANTIC_SYNONYM",
                dictionaryId = 1L,
                status = "warning",
                confidence = 0.21
            ),
            WordEdgeEntity(
                id = 12L,
                wordIdA = 403L,
                wordIdB = 404L,
                edgeType = "LEARNING_CONFUSABLE",
                dictionaryId = 1L,
                status = "warning",
                confidence = 0.18
            )
        )
        val domains = listOf(
            WordDetails(id = 401L, dictionaryId = 1L, spelling = "abandon"),
            WordDetails(id = 402L, dictionaryId = 1L, spelling = "forsake"),
            WordDetails(id = 403L, dictionaryId = 1L, spelling = "adapt"),
            WordDetails(id = 404L, dictionaryId = 1L, spelling = "adopt")
        )

        val error = runCatching {
            reviewer.reviewEdgesWithAi(edges = edges, domains = domains)
        }.exceptionOrNull()

        assertTrue("Actual error: $error", error is IllegalStateException)
        coVerify(exactly = 5) {
            openAiClient.sendMessage(any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 1) {
            wordEdgeDao.updateEdgeStatus(11L, "core", 0.91)
        }
    }

    @Test
    fun `reviewDictionaryEdgesWithAi pages through stored edges without full load`() = runTest {
        val wordEdgeDao = mockk<WordEdgeDao>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>()
        val anthropicClient = mockk<AnthropicApiClient>(relaxed = true)
        val openAiClient = mockk<OpenAiCompatibleApiClient>()
        val provider = AiApiClientProvider(anthropicClient, openAiClient)
        val reviewer = EdgeReviewer(wordEdgeDao, provider, settingsDataStore)

        val edges = listOf(
            WordEdgeEntity(id = 1L, wordIdA = 501L, wordIdB = 502L, edgeType = "SEMANTIC_SYNONYM", dictionaryId = 1L),
            WordEdgeEntity(id = 2L, wordIdA = 503L, wordIdB = 504L, edgeType = "FORM_SPELLING", dictionaryId = 1L),
            WordEdgeEntity(id = 3L, wordIdA = 505L, wordIdB = 506L, edgeType = "LEARNING_CONFUSABLE", dictionaryId = 1L)
        )
        val wordSpellings = mapOf(
            501L to "abandon",
            502L to "forsake",
            503L to "adapt",
            504L to "adopt",
            505L to "affect",
            506L to "effect"
        )

        coEvery { settingsDataStore.getPoolWindowSize() } returns 1
        coEvery { settingsDataStore.getPoolMaxConcurrent() } returns 2
        coEvery { settingsDataStore.getPoolRequestsPerMinute() } returns 120
        coEvery { settingsDataStore.getPoolRetryMode() } returns PoolRetryMode.AGGRESSIVE
        coEvery { settingsDataStore.getAiResponseUnwrapEnabled() } returns false
        coEvery { settingsDataStore.getAiJsonRepairEnabled() } returns false
        coEvery { settingsDataStore.getAiConfig(AiSettingsScope.REVIEWER) } returns SettingsDataStore.AiConfig(
            providerName = "test-openai",
            provider = AiProvider.OPENAI_COMPATIBLE,
            apiKey = "key",
            model = "gpt-test",
            baseUrl = "https://example.com"
        )
        coEvery { wordEdgeDao.countEdges(1L) } returns 3
        coEvery { wordEdgeDao.getEdgesPageFull(1L, 0L, 2) } returns edges.subList(0, 2)
        coEvery { wordEdgeDao.getEdgesPageFull(1L, 2L, 2) } returns listOf(edges[2])
        coEvery {
            openAiClient.sendMessage(any(), any(), any(), any(), any(), any())
        } returns """[{"i":0,"verdict":"keep","note":"关系成立"}]"""

        var reviewStart: Pair<Int, Int>? = null
        val progress = mutableListOf<Pair<Int, Int>>()
        val modified = reviewer.reviewDictionaryEdgesWithAi(
            dictionaryId = 1L,
            wordSpellings = wordSpellings,
            onReviewStart = { totalEdges, totalBatches ->
                reviewStart = totalEdges to totalBatches
            },
            onProgress = { current, total, _ ->
                progress += current to total
            }
        )

        assertFalse(modified)
        assertEquals(3 to 3, reviewStart)
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), progress)
        coVerify(exactly = 1) { wordEdgeDao.countEdges(1L) }
        coVerify(exactly = 1) { wordEdgeDao.getEdgesPageFull(1L, 0L, 2) }
        coVerify(exactly = 1) { wordEdgeDao.getEdgesPageFull(1L, 2L, 2) }
        coVerify(exactly = 0) { wordEdgeDao.getAllEdgesFull(any()) }
        coVerify(exactly = 3) {
            openAiClient.sendMessage(any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            wordEdgeDao.updateEdgeStatus(any(), any(), any())
        }
    }

    @Test
    fun `reviewDictionaryEdgesWithAi persists successful early pages when a later page fails`() = runTest {
        val wordEdgeDao = mockk<WordEdgeDao>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>()
        val anthropicClient = mockk<AnthropicApiClient>(relaxed = true)
        val openAiClient = mockk<OpenAiCompatibleApiClient>()
        val provider = AiApiClientProvider(anthropicClient, openAiClient)
        val reviewer = EdgeReviewer(wordEdgeDao, provider, settingsDataStore)

        val edges = listOf(
            WordEdgeEntity(id = 11L, wordIdA = 601L, wordIdB = 602L, edgeType = "SEMANTIC_SYNONYM", dictionaryId = 1L),
            WordEdgeEntity(id = 12L, wordIdA = 603L, wordIdB = 604L, edgeType = "FORM_SPELLING", dictionaryId = 1L),
            WordEdgeEntity(id = 13L, wordIdA = 605L, wordIdB = 606L, edgeType = "LEARNING_CONFUSABLE", dictionaryId = 1L)
        )
        val wordSpellings = mapOf(
            601L to "abandon",
            602L to "forsake",
            603L to "adapt",
            604L to "adopt",
            605L to "affect",
            606L to "effect"
        )

        coEvery { settingsDataStore.getPoolWindowSize() } returns 1
        coEvery { settingsDataStore.getPoolMaxConcurrent() } returns 1
        coEvery { settingsDataStore.getPoolRequestsPerMinute() } returns 120
        coEvery { settingsDataStore.getPoolRetryMode() } returns PoolRetryMode.AGGRESSIVE
        coEvery { settingsDataStore.getAiResponseUnwrapEnabled() } returns false
        coEvery { settingsDataStore.getAiJsonRepairEnabled() } returns false
        coEvery { settingsDataStore.getAiConfig(AiSettingsScope.REVIEWER) } returns SettingsDataStore.AiConfig(
            providerName = "test-openai",
            provider = AiProvider.OPENAI_COMPATIBLE,
            apiKey = "key",
            model = "gpt-test",
            baseUrl = "https://example.com"
        )
        coEvery { wordEdgeDao.countEdges(1L) } returns 3
        coEvery { wordEdgeDao.getEdgesPageFull(1L, 0L, 1) } returns listOf(edges[0])
        coEvery { wordEdgeDao.getEdgesPageFull(1L, 11L, 1) } returns listOf(edges[1])
        coEvery { wordEdgeDao.getEdgesPageFull(1L, 12L, 1) } returns listOf(edges[2])
        coEvery {
            openAiClient.sendMessage(any(), any(), any(), any(), any(), any())
        } returnsMany listOf(
            """[{"i":0,"verdict":"adjust","new_status":"core","new_confidence":0.91}]""",
            """[{"i":0,"verdict":"keep","note":"关系成立"}]""",
            "not-json",
            "not-json",
            "not-json",
            "not-json"
        )

        val error = runCatching {
            reviewer.reviewDictionaryEdgesWithAi(
                dictionaryId = 1L,
                wordSpellings = wordSpellings
            )
        }.exceptionOrNull()

        assertTrue("Actual error: $error", error is IllegalStateException)
        coVerify(exactly = 1) { wordEdgeDao.getEdgesPageFull(1L, 0L, 1) }
        coVerify(exactly = 1) { wordEdgeDao.getEdgesPageFull(1L, 11L, 1) }
        coVerify(exactly = 1) { wordEdgeDao.getEdgesPageFull(1L, 12L, 1) }
        coVerify(exactly = 1) {
            wordEdgeDao.updateEdgeStatus(11L, "core", 0.91)
        }
    }
}
