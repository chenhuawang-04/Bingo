package com.xty.englishhelper.ui.screen.guardian

import android.util.Log
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.guardian.GuardianArticleDetail
import com.xty.englishhelper.data.remote.guardian.GuardianArticlePreview
import com.xty.englishhelper.domain.model.AiProvider
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleSourceTypeV2
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.ArticleSuitabilityResult
import com.xty.englishhelper.domain.repository.AtlanticRepository
import com.xty.englishhelper.domain.repository.CsMonitorRepository
import com.xty.englishhelper.domain.repository.GuardianRepository
import com.xty.englishhelper.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardianBrowseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var guardianRepository: GuardianRepository
    private lateinit var csMonitorRepository: CsMonitorRepository
    private lateinit var atlanticRepository: AtlanticRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var articleRepository: ArticleRepository
    private lateinit var articleAiRepository: ArticleAiRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        guardianRepository = mockk(relaxed = true)
        csMonitorRepository = mockk(relaxed = true)
        atlanticRepository = mockk(relaxed = true)
        settingsDataStore = mockk(relaxed = true)
        articleRepository = mockk(relaxed = true)
        articleAiRepository = mockk(relaxed = true)

        every { settingsDataStore.onlineReadingSource } returns emptyFlow()
        every { settingsDataStore.guardianDetailConcurrency } returns flowOf(2)
        coEvery { settingsDataStore.getFastAiConfig() } returns SettingsDataStore.AiConfig(
            providerName = "fast",
            provider = AiProvider.OPENAI_COMPATIBLE,
            apiKey = "key",
            model = "model",
            baseUrl = "https://example.com/v1"
        )
        coEvery { articleRepository.getArticleBySourceUrl(any()) } returns null
        coEvery { articleRepository.updateSuitabilityBySourceUrl(any(), any(), any(), any(), any()) } returns 1
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `auto evaluation only handles visible articles after length filter`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val shortUrl = "https://example.com/short"
        val longUrl = "https://example.com/long"

        coEvery { guardianRepository.getSectionArticles("international") } returns listOf(
            preview(title = "Short Article", url = shortUrl),
            preview(title = "Long Article", url = longUrl)
        )
        coEvery { guardianRepository.getArticleDetail(shortUrl) } returns detail(
            title = "Short Article",
            url = shortUrl,
            wordCount = 650
        )
        coEvery { guardianRepository.getArticleDetail(longUrl) } returns detail(
            title = "Long Article",
            url = longUrl,
            wordCount = 1800
        )

        val evaluatedUrls = mutableListOf<String>()
        coEvery {
            articleAiRepository.evaluateArticleSuitability(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } coAnswers {
            evaluatedUrls += requireNotNull(arg<String?>(6))
            ArticleSuitabilityResult(score = 87, reason = "fit")
        }

        val viewModel = createViewModel()

        viewModel.setLengthFilter(com.xty.englishhelper.ui.screen.article.ArticleLengthFilter.SHORT)
        viewModel.setFilterEnabled(true)
        viewModel.loadSection("international")
        advanceUntilIdle()

        assertEquals(listOf(shortUrl), evaluatedUrls)
        assertEquals(listOf(shortUrl), viewModel.uiState.value.articles.map { it.url })
    }

    @Test
    fun `hidden article does not persist suitability when filter changes during evaluation`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val shortUrl = "https://example.com/short"
        val longUrl = "https://example.com/long"

        coEvery { guardianRepository.getSectionArticles("international") } returns listOf(
            preview(title = "Short Article", url = shortUrl),
            preview(title = "Long Article", url = longUrl)
        )
        coEvery { guardianRepository.getArticleDetail(shortUrl) } returns detail(
            title = "Short Article",
            url = shortUrl,
            wordCount = 650
        )
        coEvery { guardianRepository.getArticleDetail(longUrl) } returns detail(
            title = "Long Article",
            url = longUrl,
            wordCount = 1800
        )

        val resultsByUrl = mapOf(
            shortUrl to CompletableDeferred(ArticleSuitabilityResult(score = 88, reason = "short ok")),
            longUrl to CompletableDeferred<ArticleSuitabilityResult>()
        )
        coEvery {
            articleAiRepository.evaluateArticleSuitability(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } coAnswers {
            val url = requireNotNull(arg<String?>(6))
            resultsByUrl.getValue(url).await()
        }

        val viewModel = createViewModel()

        viewModel.loadSection("international")
        runCurrent()
        runCurrent()
        assertTrue(viewModel.uiState.value.allArticles.any { it.isEvaluating })

        viewModel.setLengthFilter(com.xty.englishhelper.ui.screen.article.ArticleLengthFilter.SHORT)
        viewModel.setFilterEnabled(true)
        runCurrent()

        resultsByUrl.getValue(longUrl).complete(ArticleSuitabilityResult(score = 52, reason = "should be ignored"))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            articleRepository.updateSuitabilityBySourceUrl(eq(shortUrl), any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            articleRepository.updateSuitabilityBySourceUrl(eq(longUrl), any(), any(), any(), any())
        }

        val longItem = viewModel.uiState.value.allArticles.first { it.url == longUrl }
        assertNull(longItem.suitabilityScore)
    }

    @Test
    fun `already scored article is not auto reevaluated`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val url = "https://example.com/scored"

        coEvery { guardianRepository.getSectionArticles("international") } returns listOf(
            preview(title = "Scored Article", url = url)
        )
        coEvery { guardianRepository.getArticleDetail(url) } returns detail(
            title = "Scored Article",
            url = url,
            wordCount = 900
        )
        coEvery { articleRepository.getArticleBySourceUrl(url) } returns Article(
            id = 9,
            articleUid = "uid-9",
            title = "Scored Article",
            content = "",
            domain = url,
            wordCount = 900,
            source = "卫报",
            sourceTypeV2 = ArticleSourceTypeV2.ONLINE,
            suitabilityScore = 91,
            suitabilityReason = "existing reason"
        )

        val viewModel = createViewModel()

        viewModel.loadSection("international")
        advanceUntilIdle()

        coVerify(exactly = 0) {
            articleAiRepository.evaluateArticleSuitability(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
        assertEquals(91, viewModel.uiState.value.allArticles.single().suitabilityScore)
        assertEquals("existing reason", viewModel.uiState.value.allArticles.single().suitabilityReason)
    }

    @Test
    fun `placeholder article created by auto evaluation stores normalized source url`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val rawUrl = "https://www.csmonitor.com/World/2026/0324/Test-story/?utm_source=test"
        val normalizedUrl = "https://www.csmonitor.com/World/2026/0324/Test-story"

        coEvery { guardianRepository.getSectionArticles("international") } returns listOf(
            preview(title = "CSMonitor Style Article", url = rawUrl)
        )
        coEvery { guardianRepository.getArticleDetail(rawUrl) } returns detail(
            title = "CSMonitor Style Article",
            url = rawUrl,
            wordCount = 900
        )
        coEvery {
            articleAiRepository.evaluateArticleSuitability(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns ArticleSuitabilityResult(score = 88, reason = "Good fit")
        coEvery { articleRepository.updateSuitabilityBySourceUrl(rawUrl, any(), any(), any(), any()) } returns 0
        coEvery { articleRepository.upsertArticle(any()) } returns 21L

        val viewModel = createViewModel()

        viewModel.loadSection("international")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            articleRepository.upsertArticle(match {
                it.domain == normalizedUrl &&
                    it.suitabilityScore == 88 &&
                    it.suitabilityReason == "Good fit"
            })
        }
    }

    private fun createViewModel(): GuardianBrowseViewModel {
        return GuardianBrowseViewModel(
            guardianRepository = guardianRepository,
            csMonitorRepository = csMonitorRepository,
            atlanticRepository = atlanticRepository,
            settingsDataStore = settingsDataStore,
            articleRepository = articleRepository,
            articleAiRepository = articleAiRepository
        )
    }

    private fun preview(title: String, url: String): GuardianArticlePreview {
        return GuardianArticlePreview(
            title = title,
            url = url,
            trailText = "$title summary",
            author = "Author"
        )
    }

    private fun detail(title: String, url: String, wordCount: Int): GuardianArticleDetail {
        return GuardianArticleDetail(
            title = title,
            author = "Author",
            summary = "$title summary",
            coverImageUrl = null,
            paragraphs = listOf(
                ArticleParagraph(
                    paragraphIndex = 0,
                    text = buildWords(wordCount),
                    paragraphType = ParagraphType.TEXT
                )
            ),
            source = "卫报",
            sourceUrl = url
        )
    }

    private fun buildWords(wordCount: Int): String {
        return List(wordCount) { "word$it" }.joinToString(" ")
    }
}
