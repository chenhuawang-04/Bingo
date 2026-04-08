package com.xty.englishhelper.data.remote.oed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OedHtmlParserTest {

    private val parser = OedHtmlParser()

    @Test
    fun `parseSearchResults extracts headword and url`() {
        val html = """
            <div class="resultsSet">
              <div class="resultsSetItem">
                <div class="resultsSetItemHead"><span class="dateRange">Old English–</span></div>
                <div class="resultsSetItemBody">
                  <h3 class="resultTitle">
                    <span class="hw"><span class="hw">black, </span><span class="ps">adj. & n.</span></span>
                  </h3>
                  <div class="snippet">Of the darkest colour possible.</div>
                  <a class="viewEntry resultLink" href="/dictionary/black_adj?tab=meaning_and_use#19397955" title="black, adj. &amp; n.">View entry</a>
                </div>
              </div>
            </div>
        """.trimIndent()

        val items = parser.parseSearchResults(html)

        assertEquals(1, items.size)
        assertEquals("black", items.first().headword)
        assertTrue(items.first().relativeUrl.contains("/dictionary/black_adj"))
    }

    @Test
    fun `parseExamples extracts quotation blockquote text`() {
        val html = """
            <main id="maincontainer">
              <li class="quotation">
                <blockquote class="quotation-text">
                  Were you a White and for the people, or a <mark class="quotation-keyword">Black</mark> and for the nobles?
                </blockquote>
              </li>
              <li class="quotation">
                <blockquote class="quotation-text">
                  Were you a White and for the people, or a <mark class="quotation-keyword">Black</mark> and for the nobles?
                </blockquote>
              </li>
            </main>
        """.trimIndent()

        val examples = parser.parseExamples(html, limit = 8)

        assertEquals(1, examples.size)
        assertEquals("Were you a White and for the people, or a Black and for the nobles?", examples.first())
    }
}

