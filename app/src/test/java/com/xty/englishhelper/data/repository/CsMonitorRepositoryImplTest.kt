package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.csmonitor.CsMonitorArticleDetail
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorHtmlParser
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorService
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleSourceTypeV2
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.usecase.article.ParseArticleUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CsMonitorRepositoryImplTest {

    private lateinit var service: CsMonitorService
    private lateinit var articleRepository: ArticleRepository
    private lateinit var parseArticleUseCase: ParseArticleUseCase

    @Before
    fun setUp() {
        service = mockk(relaxed = true)
        articleRepository = mockk(relaxed = true)
        parseArticleUseCase = mockk(relaxed = true)
    }

    @Test
    fun `getArticleDetail loads rssfull body through raw url loader`() = runTest {
        val articleUrl = "https://www.csmonitor.com/World/2026/0324/Test-story"
        val rssUrl = "https://www.csmonitor.com/layout/set/rssfull/World/2026/0324/Test-story"
        val html = """
            <html>
              <head>
                <meta property="article:section" content="World" />
                <meta name="csm.content.body" content="/layout/set/rssfull/World/2026/0324/Test-story" />
              </head>
              <body>
                <h1 id="headline" class="eza-title">Test Story</h1>
                <div class="eza-body prem truncate-for-paywall">
                  <p>Truncated paragraph.</p>
                </div>
              </body>
            </html>
        """.trimIndent()
        val rss = """
            <rss xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <item>
                  <title>Test Story</title>
                  <dc:creator>RSS Author</dc:creator>
                  <description><![CDATA[
                    <p>Full paragraph one.</p>
                    <p>Full paragraph two.</p>
                  ]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        coEvery { service.fetchArticleHtml(articleUrl) } returns html
        coEvery { service.fetchRawUrl(rssUrl) } returns rss

        val repository = CsMonitorRepositoryImpl(
            service = service,
            htmlParser = CsMonitorHtmlParser(),
            articleRepository = articleRepository,
            parseArticleUseCase = parseArticleUseCase
        )

        val result = repository.getArticleDetail(articleUrl)

        assertEquals(listOf("Full paragraph one.", "Full paragraph two."), result.paragraphs.map { it.text })
        assertEquals(articleUrl, result.sourceUrl)
        coVerify(exactly = 1) { service.fetchArticleHtml(articleUrl) }
        coVerify(exactly = 1) { service.fetchRawUrl(rssUrl) }
    }

    @Test
    fun `createTemporaryArticle refreshes fuller saved article and reparses saved data`() = runTest {
        val canonicalUrl = "https://www.csmonitor.com/World/2026/0324/Test-story"
        val existing = Article(
            id = 7,
            articleUid = "uid-7",
            title = "Test Story",
            content = "Old paragraph only.",
            domain = canonicalUrl,
            summary = "Old summary",
            author = "Old Author",
            source = "CSMonitor · World",
            wordCount = 3,
            isSaved = true,
            sourceTypeV2 = ArticleSourceTypeV2.ONLINE
        )
        val incomingParagraphs = listOf(
            ArticleParagraph(text = "Full paragraph one.", paragraphType = ParagraphType.TEXT),
            ArticleParagraph(text = "Full paragraph two.", paragraphType = ParagraphType.TEXT)
        )
        val detail = CsMonitorArticleDetail(
            title = "Test Story",
            author = "New Author",
            summary = "New summary",
            coverImageUrl = "https://img/rss.jpg",
            paragraphs = incomingParagraphs,
            source = "CSMonitor · World",
            sourceUrl = canonicalUrl,
            requestedUrl = canonicalUrl
        )

        coEvery { articleRepository.getArticleBySourceUrl(canonicalUrl) } returns existing
        coEvery { articleRepository.getParagraphs(7) } returns listOf(
            ArticleParagraph(articleId = 7, paragraphIndex = 0, text = "Old paragraph only.", paragraphType = ParagraphType.TEXT)
        )
        coEvery { articleRepository.upsertArticle(any()) } returns 7L
        coEvery { articleRepository.deleteParagraphsByArticle(7) } returns Unit
        coEvery { articleRepository.insertParagraphs(any()) } returns Unit
        coEvery { parseArticleUseCase(7) } returns Unit

        val repository = CsMonitorRepositoryImpl(
            service = service,
            htmlParser = mockk(),
            articleRepository = articleRepository,
            parseArticleUseCase = parseArticleUseCase
        )

        val articleId = repository.createTemporaryArticle(detail)

        assertEquals(7L, articleId)
        coVerify(exactly = 1) {
            articleRepository.upsertArticle(match {
                it.id == 7L &&
                    it.title == "Test Story" &&
                    it.content == "Full paragraph one.\n\nFull paragraph two." &&
                    it.summary == "New summary" &&
                    it.author == "New Author" &&
                    it.coverImageUrl == "https://img/rss.jpg" &&
                    it.domain == canonicalUrl &&
                    it.wordCount == 6 &&
                    it.isSaved
            })
        }
        coVerify(exactly = 1) { articleRepository.deleteParagraphsByArticle(7) }
        coVerify(exactly = 1) {
            articleRepository.insertParagraphs(match { paragraphs ->
                paragraphs.size == 2 &&
                    paragraphs[0].articleId == 7L &&
                    paragraphs[0].paragraphIndex == 0 &&
                    paragraphs[1].articleId == 7L &&
                    paragraphs[1].paragraphIndex == 1
            })
        }
        coVerify(exactly = 1) { parseArticleUseCase(7) }
    }

    @Test
    fun `createTemporaryArticle deduplicates by requested url and rewrites domain to canonical`() = runTest {
        val canonicalUrl = "https://www.csmonitor.com/World/2026/0324/Test-story"
        val requestedUrl = "$canonicalUrl?utm_source=test"
        val storedParagraphs = listOf(
            ArticleParagraph(articleId = 9, paragraphIndex = 0, text = "Stable paragraph.", paragraphType = ParagraphType.TEXT)
        )
        val existing = Article(
            id = 9,
            articleUid = "uid-9",
            title = "Test Story",
            content = "Stable paragraph.",
            domain = requestedUrl,
            summary = "Summary",
            author = "Author",
            source = "CSMonitor · World",
            wordCount = 2,
            isSaved = false,
            sourceTypeV2 = ArticleSourceTypeV2.ONLINE
        )
        val detail = CsMonitorArticleDetail(
            title = "Test Story",
            author = "Author",
            summary = "Summary",
            coverImageUrl = null,
            paragraphs = listOf(
                ArticleParagraph(text = "Stable paragraph.", paragraphType = ParagraphType.TEXT)
            ),
            source = "CSMonitor · World",
            sourceUrl = canonicalUrl,
            requestedUrl = requestedUrl
        )

        coEvery { articleRepository.getArticleBySourceUrl(canonicalUrl) } returns null
        coEvery { articleRepository.getArticleBySourceUrl(requestedUrl) } returns existing
        coEvery { articleRepository.getParagraphs(9) } returns storedParagraphs
        coEvery { articleRepository.upsertArticle(any()) } returns 9L

        val repository = CsMonitorRepositoryImpl(
            service = service,
            htmlParser = mockk(),
            articleRepository = articleRepository,
            parseArticleUseCase = parseArticleUseCase
        )

        val articleId = repository.createTemporaryArticle(detail)

        assertEquals(9L, articleId)
        coVerify(exactly = 1) { articleRepository.getArticleBySourceUrl(canonicalUrl) }
        coVerify(exactly = 1) { articleRepository.getArticleBySourceUrl(requestedUrl) }
        coVerify(exactly = 1) {
            articleRepository.upsertArticle(match {
                it.id == 9L &&
                    it.domain == canonicalUrl &&
                    it.content == "Stable paragraph." &&
                    !it.isSaved
            })
        }
        coVerify(exactly = 0) { articleRepository.deleteParagraphsByArticle(any()) }
        coVerify(exactly = 0) { articleRepository.insertParagraphs(any()) }
        coVerify(exactly = 0) { parseArticleUseCase(any()) }
    }

    @Test
    fun `createTemporaryArticle updates metadata without replacing paragraphs or reparsing`() = runTest {
        val canonicalUrl = "https://www.csmonitor.com/World/2026/0324/Test-story"
        val storedParagraphs = listOf(
            ArticleParagraph(articleId = 11, paragraphIndex = 0, text = "Stable paragraph.", paragraphType = ParagraphType.TEXT)
        )
        val existing = Article(
            id = 11,
            articleUid = "uid-11",
            title = "Test Story",
            content = "Stable paragraph.",
            domain = canonicalUrl,
            summary = "Old summary",
            author = "Old Author",
            source = "CSMonitor · World",
            coverImageUrl = null,
            wordCount = 2,
            isSaved = true,
            sourceTypeV2 = ArticleSourceTypeV2.ONLINE
        )
        val detail = CsMonitorArticleDetail(
            title = "Test Story",
            author = "New Author",
            summary = "New summary",
            coverImageUrl = "https://img/new.jpg",
            paragraphs = listOf(
                ArticleParagraph(text = "Stable paragraph.", paragraphType = ParagraphType.TEXT)
            ),
            source = "CSMonitor · World",
            sourceUrl = canonicalUrl,
            requestedUrl = canonicalUrl
        )

        coEvery { articleRepository.getArticleBySourceUrl(canonicalUrl) } returns existing
        coEvery { articleRepository.getParagraphs(11) } returns storedParagraphs
        coEvery { articleRepository.upsertArticle(any()) } returns 11L

        val repository = CsMonitorRepositoryImpl(
            service = service,
            htmlParser = mockk(),
            articleRepository = articleRepository,
            parseArticleUseCase = parseArticleUseCase
        )

        val articleId = repository.createTemporaryArticle(detail)

        assertEquals(11L, articleId)
        coVerify(exactly = 1) {
            articleRepository.upsertArticle(match {
                it.id == 11L &&
                    it.summary == "New summary" &&
                    it.author == "New Author" &&
                    it.coverImageUrl == "https://img/new.jpg" &&
                    it.content == "Stable paragraph." &&
                    it.domain == canonicalUrl
            })
        }
        coVerify(exactly = 0) { articleRepository.deleteParagraphsByArticle(any()) }
        coVerify(exactly = 0) { articleRepository.insertParagraphs(any()) }
        coVerify(exactly = 0) { parseArticleUseCase(any()) }
    }

    @Test
    fun `createTemporaryArticle matches historical trailing slash source url`() = runTest {
        val canonicalUrl = "https://www.csmonitor.com/World/2026/0324/Test-story"
        val historicalUrl = "$canonicalUrl/"
        val existing = Article(
            id = 13,
            articleUid = "uid-13",
            title = "Test Story",
            content = "Stable paragraph.",
            domain = historicalUrl,
            summary = "Summary",
            author = "Author",
            source = "CSMonitor · World",
            wordCount = 2,
            isSaved = false,
            sourceTypeV2 = ArticleSourceTypeV2.ONLINE
        )
        val detail = CsMonitorArticleDetail(
            title = "Test Story",
            author = "Author",
            summary = "Summary",
            coverImageUrl = null,
            paragraphs = listOf(
                ArticleParagraph(text = "Stable paragraph.", paragraphType = ParagraphType.TEXT)
            ),
            source = "CSMonitor · World",
            sourceUrl = canonicalUrl,
            requestedUrl = canonicalUrl
        )

        coEvery { articleRepository.getArticleBySourceUrl(canonicalUrl) } returns existing
        coEvery { articleRepository.upsertArticle(any()) } returns 13L
        coEvery { articleRepository.getParagraphs(13) } returns listOf(
            ArticleParagraph(articleId = 13, paragraphIndex = 0, text = "Stable paragraph.", paragraphType = ParagraphType.TEXT)
        )

        val repository = CsMonitorRepositoryImpl(
            service = service,
            htmlParser = mockk(),
            articleRepository = articleRepository,
            parseArticleUseCase = parseArticleUseCase
        )

        val articleId = repository.createTemporaryArticle(detail)

        assertEquals(13L, articleId)
        coVerify(exactly = 1) { articleRepository.getArticleBySourceUrl(canonicalUrl) }
        coVerify(exactly = 0) { articleRepository.getArticleBySourceUrl("$canonicalUrl/") }
        coVerify(exactly = 1) {
            articleRepository.upsertArticle(match {
                it.id == 13L && it.domain == canonicalUrl
            })
        }
    }
}
