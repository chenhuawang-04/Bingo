package com.xty.englishhelper.data.remote.oed

import org.jsoup.Jsoup

data class OedSearchItem(
    val headword: String,
    val variant: String,
    val dateRange: String,
    val snippet: String,
    val relativeUrl: String
)

data class OedEntryDetail(
    val headword: String,
    val variant: String,
    val pronunciation: String?,
    val etymologySummary: String?,
    val tags: List<String>,
    val senses: List<String>
)

class OedHtmlParser {

    fun parseSearchResults(html: String): List<OedSearchItem> {
        val doc = Jsoup.parse(html)
        val items = doc.select(".resultsSetItem")
        val out = mutableListOf<OedSearchItem>()
        for (item in items) {
            val link = item.selectFirst("a.viewEntry.resultLink") ?: continue
            val href = link.attr("href").trim()
            if (href.isBlank()) continue

            val title = link.attr("title").trim()
            val titleNode = item.selectFirst(".resultTitle .hw")?.text()?.trim().orEmpty()
            val pos = item.selectFirst(".resultTitle .ps")?.text()?.trim().orEmpty()
            val combined = title.ifBlank { titleNode }

            val headword = extractHeadword(combined.ifBlank { titleNode })
            val variant = pos.ifBlank { extractVariant(combined) }
            val dateRange = item.selectFirst(".dateRange")?.text()?.trim().orEmpty()
            val snippet = item.selectFirst(".snippet")?.text()?.trim().orEmpty()

            out.add(
                OedSearchItem(
                    headword = headword,
                    variant = variant,
                    dateRange = dateRange,
                    snippet = snippet,
                    relativeUrl = href
                )
            )
        }
        return out.distinctBy { it.relativeUrl }.take(6)
    }

    fun parseEntryDetail(html: String, fallbackWord: String): OedEntryDetail {
        val doc = Jsoup.parse(html)
        val contentRoot = doc.selectFirst("main#maincontainer, .entryPage, .entryMainContent, .main") ?: doc
        val title = doc.selectFirst("title")?.text()?.trim().orEmpty()
        val h1 = contentRoot.selectFirst("h1")?.text()?.trim().orEmpty()

        val heading = h1.ifBlank { title.substringBefore(" meanings").trim() }
        val headword = extractHeadword(heading).ifBlank { fallbackWord }
        val variant = extractVariant(heading)

        val pronunciation = doc.select(
            ".pronunciation, .pron, .ipa, [class*=pron]"
        )
            .map { it.text().trim() }
            .firstOrNull { it.isNotBlank() }

        val tags = contentRoot.select(".tags .tag, a.tag")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

        val etymologySummary = contentRoot.select(
            ".etymology .definition, .etymology p, #etymology .definition, #etymology p"
        )
            .map { it.text().trim() }
            .firstOrNull { it.isNotBlank() }

        val senses = contentRoot.select(".item.sense .definition, .sense .definition, .definition")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(14)

        return OedEntryDetail(
            headword = headword,
            variant = variant,
            pronunciation = pronunciation,
            etymologySummary = etymologySummary,
            tags = tags,
            senses = senses
        )
    }

    fun parseExamples(html: String, limit: Int = 8): List<String> {
        val doc = Jsoup.parse(html)
        val root = doc.selectFirst("main#maincontainer, .entryPage, .entryMainContent, .main") ?: doc
        return root.select(
            ".quotation .quote, .quotation .quoteText, .quotation .quotationText, .sense .example, .examples li, .quote"
        )
            .map { it.text().trim() }
            .map { it.replace(Regex("\\s+"), " ") }
            .filter { it.length >= 8 }
            .distinct()
            .take(limit)
    }

    private fun extractHeadword(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        return clean.substringBefore(",").trim().ifBlank { clean }
    }

    private fun extractVariant(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (!clean.contains(",")) return ""
        return clean.substringAfter(",").trim()
    }
}
