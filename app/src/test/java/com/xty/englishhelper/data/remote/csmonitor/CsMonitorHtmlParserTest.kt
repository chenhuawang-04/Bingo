package com.xty.englishhelper.data.remote.csmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsMonitorHtmlParserTest {

    private val parser = CsMonitorHtmlParser()

    @Test
    fun `extracts rssfull url from page metadata`() {
        val html = """
            <html>
              <head>
                <link rel="canonical" href="https://www.csmonitor.com/World/2026/0324/Test-story?foo=bar" />
                <meta name="csm.content.body" content="/layout/set/rssfull/World/2026/0324/Test-story" />
              </head>
            </html>
        """.trimIndent()

        val result = parser.extractRssFullUrl(
            html = html,
            articleUrl = "https://www.csmonitor.com/World/2026/0324/Test-story?foo=bar"
        )

        assertEquals(
            "https://www.csmonitor.com/layout/set/rssfull/World/2026/0324/Test-story",
            result
        )
    }

    @Test
    fun `uses rssfull body instead of truncated page body and filters promos`() {
        val html = """
            <html>
              <head>
                <meta property="article:section" content="World" />
                <meta name="csm.author" content="Page Author" />
                <meta name="csm.image.std" content="https://img/page.jpg" />
                <meta name="csm.content.body" content="/layout/set/rssfull/World/2026/0324/Test-story" />
              </head>
              <body>
                <h1 id="headline" class="eza-title">Test Story</h1>
                <div id="summary" class="eza-summary"><p>Page summary.</p></div>
                <div class="eza-body prem truncate-for-paywall">
                  <p>Truncated paragraph.</p>
                </div>
              </body>
            </html>
        """.trimIndent()

        val rss = """
            <rss xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <item>
                  <title>Test Story</title>
                  <dc:creator>RSS Author</dc:creator>
                  <description><![CDATA[
                    <p>Full paragraph one.</p>
                    <p>Read this story at csmonitor.com for extras.</p>
                    <p>Full paragraph two.</p>
                  ]]></description>
                  <media:content url="https://img/rss.jpg" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val result = parser.parseArticlePage(
            html = html,
            articleUrl = "https://www.csmonitor.com/World/2026/0324/Test-story",
            rssFullXml = rss
        )

        assertEquals("Test Story", result.title)
        assertEquals("RSS Author", result.author)
        assertEquals("Page summary.", result.summary)
        assertEquals("CSMonitor \u00B7 World", result.source)
        assertEquals("https://img/rss.jpg", result.coverImageUrl)
        assertEquals(
            "https://www.csmonitor.com/World/2026/0324/Test-story",
            result.sourceUrl
        )
        assertEquals(
            "https://www.csmonitor.com/World/2026/0324/Test-story",
            result.requestedUrl
        )
        assertEquals(listOf("Full paragraph one.", "Full paragraph two."), result.paragraphs.map { it.text })
        assertTrue(result.paragraphs.all { it.paragraphType.name == "TEXT" })
    }

    @Test
    fun `falls back to page paragraphs when rssfull is unavailable`() {
        val html = """
            <html>
              <head>
                <meta property="article:section" content="Editorials" />
              </head>
              <body>
                <h1 id="headline" class="eza-title">Fallback Story</h1>
                <div id="summary" class="eza-summary"><p>Fallback summary.</p></div>
                <div class="eza-body">
                  <p>Page paragraph one.</p>
                  <p class="promo_links">Become a part of the Monitor community.</p>
                  <p>Read this story at csmonitor.com for extras.</p>
                  <p>Page paragraph two.</p>
                </div>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseArticlePage(
            html = html,
            articleUrl = "https://www.csmonitor.com/Editorials/2026/0324/Fallback-story"
        )

        assertEquals(listOf("Page paragraph one.", "Page paragraph two."), result.paragraphs.map { it.text })
        assertEquals("Fallback summary.", result.summary)
        assertEquals("CSMonitor \u00B7 Editorials", result.source)
        assertEquals(
            "https://www.csmonitor.com/Editorials/2026/0324/Fallback-story",
            result.sourceUrl
        )
    }

    @Test
    fun `prefers canonical url as source url while preserving requested url`() {
        val html = """
            <html>
              <head>
                <link rel="canonical" href="https://www.csmonitor.com/World/2026/0324/Canonical-story?tracked=1" />
              </head>
              <body>
                <h1 id="headline" class="eza-title">Canonical Story</h1>
                <div class="eza-body">
                  <p>Body paragraph.</p>
                </div>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseArticlePage(
            html = html,
            articleUrl = "https://www.csmonitor.com/World/2026/0324/Canonical-story?utm_source=test"
        )

        assertEquals(
            "https://www.csmonitor.com/World/2026/0324/Canonical-story",
            result.sourceUrl
        )
        assertEquals(
            "https://www.csmonitor.com/World/2026/0324/Canonical-story?utm_source=test",
            result.requestedUrl
        )
    }

    @Test
    fun `resolves relative canonical url and trims trailing slash`() {
        val html = """
            <html>
              <head>
                <link rel="canonical" href="/World/2026/0324/Canonical-story/?tracked=1" />
                <meta name="csm.content.body" content="/layout/set/rssfull/World/2026/0324/Canonical-story/" />
              </head>
              <body>
                <h1 id="headline" class="eza-title">Canonical Story</h1>
                <div class="eza-body">
                  <p>Body paragraph.</p>
                </div>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseArticlePage(
            html = html,
            articleUrl = "https://www.csmonitor.com/World/2026/0324/Canonical-story/?utm_source=test"
        )

        assertEquals(
            "https://www.csmonitor.com/World/2026/0324/Canonical-story",
            result.sourceUrl
        )
        assertEquals(
            "https://www.csmonitor.com/layout/set/rssfull/World/2026/0324/Canonical-story",
            parser.extractRssFullUrl(
                html = html,
                articleUrl = "https://www.csmonitor.com/World/2026/0324/Canonical-story/?utm_source=test"
            )
        )
    }
}
