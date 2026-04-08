package com.xty.englishhelper.data.remote.cambridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CambridgeHtmlParserTest {

    private val parser = CambridgeHtmlParser()

    @Test
    fun `parseExamples removes translation noise and deduplicates same sentence`() {
        val html = """
            <html>
              <body>
                <div class="entry-body__el">
                  <div class="def-block">
                    <span class="examp dexamp">
                      We skidded on the ice and crashed.
                      <span class="trans dtrans">我们在冰上打滑并撞了（另一辆）车。</span>
                    </span>
                    <span class="eg">We skidded on the ice and crashed.</span>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val examples = parser.parseExamples(html, limit = 8)

        assertEquals(1, examples.size)
        assertEquals("We skidded on the ice and crashed.", examples.first())
    }

    @Test
    fun `parseExamples filters non sentence nodes`() {
        val html = """
            <html>
              <body>
                <div class="entry-body__el">
                  <div class="def-block">
                    <span class="eg">noun</span>
                    <span class="eg">标签</span>
                    <span class="eg">Her brother borrowed her motorbike and crashed it.</span>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val examples = parser.parseExamples(html, limit = 8)

        assertEquals(1, examples.size)
        assertTrue(examples.first().startsWith("Her brother"))
    }

    @Test
    fun `parseEntry prioritizes senses from selected entry root`() {
        val html = """
            <html>
              <body>
                <div class="entry-body__el">
                  <span class="hw">black</span>
                  <span class="pos dpos">adjective</span>
                  <div class="def-block">
                    <div class="def ddef_d">having the darkest colour there is</div>
                    <div class="trans dtrans">黑色的</div>
                  </div>
                </div>
                <div class="sidebar">
                  <div class="def">noise that should not be parsed as main sense</div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val entry = parser.parseEntry(
            html = html,
            fallbackWord = "fallback",
            sourceUrl = "https://dictionary.cambridge.org/dictionary/english-chinese-simplified/black"
        )

        assertEquals("black", entry.headword)
        assertEquals("adjective", entry.partOfSpeech)
        assertEquals(1, entry.senses.size)
        assertEquals("having the darkest colour there is", entry.senses.first().definition)
    }
}
