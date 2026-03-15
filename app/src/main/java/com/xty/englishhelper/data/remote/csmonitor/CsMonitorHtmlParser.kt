package com.xty.englishhelper.data.remote.csmonitor

import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ParagraphType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject

data class CsMonitorArticlePreview(
    val title: String,
    val url: String,
    val trailText: String? = null,
    val thumbnailUrl: String? = null,
    val author: String? = null
)

data class CsMonitorArticleDetail(
    val title: String,
    val author: String,
    val summary: String,
    val coverImageUrl: String?,
    val paragraphs: List<ArticleParagraph>,
    val source: String,
    val sourceUrl: String
)

class CsMonitorHtmlParser {

    fun parseSectionPage(html: String): List<CsMonitorArticlePreview> {
        val doc = Jsoup.parse(html, BASE_URL)
        val results = mutableListOf<CsMonitorArticlePreview>()
        val seenUrls = mutableSetOf<String>()

        val headlineLinks = doc.select(
            "h3.story_headline a[href], h2.story_headline a[href], a.story_headline[href]," +
                " h3 a[href*='/20'], h2 a[href*='/20']"
        )
        for (link in headlineLinks) {
            val preview = extractPreviewFromLink(link, seenUrls) ?: continue
            results.add(preview)
        }

        if (results.isEmpty()) {
            val fallbackLinks = doc.select("a[href*='/20']")
            for (link in fallbackLinks) {
                val preview = extractPreviewFromLink(link, seenUrls) ?: continue
                results.add(preview)
            }
        }

        return results
    }

    private fun extractPreviewFromLink(
        link: Element,
        seenUrls: MutableSet<String>
    ): CsMonitorArticlePreview? {
        val href = resolveUrl(link)
        if (!isArticleUrl(href) || !seenUrls.add(href)) return null

        val title = link.text().trim()
        if (title.isBlank() || title.length < 5) return null

        val parent = link.closest("article, li, div")
        val thumbnail = parent?.selectFirst("img")?.let { img ->
            img.absUrl("src").ifBlank {
                img.attr("data-src").ifBlank {
                    img.attr("data-original").ifBlank {
                        img.attr("srcset").split(" ").firstOrNull()
                    }
                }
            }
        }
        val trailText = parent?.selectFirst(
            "p, .story_summary, .story-summary, .story_deck, .story-deck, .summary, .deck"
        )?.text()?.trim()
        val author = parent?.selectFirst(".byline, .author, .story_byline")?.text()?.trim()

        return CsMonitorArticlePreview(
            title = title,
            url = href,
            trailText = trailText?.takeIf { it.isNotBlank() && it != title },
            thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() },
            author = author?.takeIf { it.isNotBlank() }
        )
    }

    private fun resolveUrl(link: Element): String {
        val abs = link.absUrl("href")
        if (abs.isNotBlank()) return abs
        val raw = link.attr("href")
        if (raw.isBlank()) return ""
        return if (raw.startsWith("/")) "$BASE_URL$raw" else raw
    }

    private fun isArticleUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (!lower.contains("csmonitor.com")) return false
        if (lower.contains("/podcasts")) {
            return lower.matches(Regex(".*\\/podcasts\\/\\d{4}\\/\\d{2}\\/\\d{2}\\/.*"))
        }
        return lower.matches(Regex(".*\\/\\d{4}\\/\\d{4}\\/.*")) ||
            lower.matches(Regex(".*\\/\\d{4}\\/\\d{2}\\/\\d{2}\\/.*"))
    }

    fun parseArticlePage(html: String, articleUrl: String): CsMonitorArticleDetail {
        val doc = Jsoup.parse(html, articleUrl)
        val jsonLd = extractJsonLd(doc)

        val title = jsonLd?.optString("headline")?.takeIf { it.isNotBlank() }
            ?: extractTitle(doc)
        val author = extractAuthorFromJsonLd(jsonLd)
            ?: extractAuthor(doc)
        val summary = extractSummary(doc, jsonLd)
        val section = extractSection(doc)
        val coverImage = extractCoverImage(doc, jsonLd)

        val body = doc.selectFirst("div.eza-body.prem.truncate-for-paywall")
            ?: doc.selectFirst("article#story-content div.eza-body")
            ?: doc.selectFirst("article#story-content")
            ?: doc.body()

        cleanupBody(body)
        val paragraphs = extractParagraphs(body)
        val (finalCover, finalParagraphs) = determineCoverImage(coverImage, paragraphs)

        val sourceLabel = "CSMonitor" + if (section.isNotBlank()) " · $section" else ""

        return CsMonitorArticleDetail(
            title = title,
            author = author,
            summary = summary,
            coverImageUrl = finalCover,
            paragraphs = finalParagraphs,
            source = sourceLabel,
            sourceUrl = articleUrl
        )
    }

    private fun extractJsonLd(doc: Document): JSONObject? {
        val script = doc.selectFirst("script#meta-schema-org")
            ?: doc.selectFirst("script[type=application/ld+json]")
            ?: return null
        val raw = script.data().trim()
        return try {
            when {
                raw.startsWith("[") -> {
                    val array = JSONArray(raw)
                    findNewsArticleInArray(array)
                }
                else -> {
                    val obj = JSONObject(raw)
                    if (obj.has("@graph")) {
                        findNewsArticleInArray(obj.getJSONArray("@graph"))
                    } else if (isNewsArticleType(obj)) {
                        obj
                    } else {
                        null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findNewsArticleInArray(array: JSONArray): JSONObject? {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (isNewsArticleType(obj)) return obj
        }
        return null
    }

    private fun isNewsArticleType(obj: JSONObject): Boolean {
        val type = obj.optString("@type", "")
        if (type.isBlank()) return false
        return type.contains("NewsArticle", ignoreCase = true) ||
            type.contains("Article", ignoreCase = true)
    }

    private fun extractAuthorFromJsonLd(jsonLd: JSONObject?): String? {
        jsonLd ?: return null
        return try {
            val author = jsonLd.opt("author") ?: return null
            when (author) {
                is JSONArray -> {
                    (0 until author.length()).mapNotNull { i ->
                        author.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
                    }.joinToString(", ").takeIf { it.isNotBlank() }
                }
                is JSONObject -> author.optString("name")?.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCoverImage(doc: Document, jsonLd: JSONObject?): String? {
        val jsonLdImage = try {
            val img = jsonLd?.opt("image")
            when (img) {
                is JSONArray -> {
                    val first = img.opt(0)
                    when (first) {
                        is JSONObject -> first.optString("url").takeIf { it.isNotBlank() }
                        is String -> first.takeIf { it.isNotBlank() }
                        else -> null
                    }
                }
                is JSONObject -> img.optString("url").takeIf { it.isNotBlank() }
                is String -> img.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (!jsonLdImage.isNullOrBlank()) return jsonLdImage

        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
        if (!ogImage.isNullOrBlank()) return ogImage

        return doc.selectFirst("meta[name=twitter:image]")?.attr("content")?.takeIf { it.isNotBlank() }
    }

    private fun extractTitle(doc: Document): String {
        return doc.selectFirst("h1#headline.eza-title")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: doc.title().trim()
    }

    private fun extractSummary(doc: Document, jsonLd: JSONObject?): String {
        val jsonDesc = jsonLd?.optString("description")?.takeIf { it.isNotBlank() }
        if (!jsonDesc.isNullOrBlank()) return jsonDesc
        val domSummary = doc.selectFirst("div#summary.eza-summary p")?.text()?.trim()
        if (!domSummary.isNullOrBlank()) return domSummary
        val metaDesc = doc.selectFirst("meta[name=description], meta[property=og:description]")?.attr("content")?.trim()
        return metaDesc ?: ""
    }

    private fun extractAuthor(doc: Document): String {
        val byline = doc.selectFirst("ul.story-bylines li.author .staff-name")?.text()?.trim()
        if (!byline.isNullOrBlank()) return byline
        val metaAuthor = doc.selectFirst("meta[name=csm.author]")?.attr("content")?.trim()
        if (!metaAuthor.isNullOrBlank()) return metaAuthor
        return ""
    }

    private fun extractSection(doc: Document): String {
        val section = doc.selectFirst("meta[property=article:section]")?.attr("content")?.trim()
        if (!section.isNullOrBlank()) return section

        val pageMeta = doc.selectFirst("script#page-meta-data")?.data()?.trim()
        if (!pageMeta.isNullOrBlank()) {
            try {
                val obj = JSONObject(pageMeta)
                val sections = obj.optJSONArray("sections")
                if (sections != null && sections.length() > 0) {
                    return sections.optString(0)?.takeIf { it.isNotBlank() } ?: ""
                }
            } catch (_: Exception) {
            }
        }
        return ""
    }

    private fun cleanupBody(body: Element) {
        body.select("aside.injection, .article-story-promo, .newsletter-email-signup, .story-half, #paywall, #inbody-related-stories")
            .forEach { it.remove() }
    }

    private fun extractParagraphs(body: Element): List<ArticleParagraph> {
        val paragraphs = mutableListOf<ArticleParagraph>()
        var index = 0
        val firstImageUrl = mutableListOf<String>()

        for (child in body.children()) {
            if (child.tagName() in listOf("aside", "nav", "footer", "style", "script")) continue
            when (child.tagName()) {
                "h2", "h3" -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) {
                        paragraphs.add(ArticleParagraph(paragraphIndex = index++, text = text, paragraphType = ParagraphType.HEADING))
                    }
                }
                "blockquote" -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) {
                        paragraphs.add(ArticleParagraph(paragraphIndex = index++, text = text, paragraphType = ParagraphType.QUOTE))
                    }
                }
                "figure" -> {
                    val img = child.selectFirst("img")
                    val imgUrl = img?.absUrl("src")?.ifBlank { img.attr("srcset").split(" ").firstOrNull() }
                    val caption = child.selectFirst(".caption-bar .eza-caption, figcaption")?.text()?.trim() ?: ""
                    if (!imgUrl.isNullOrBlank()) {
                        if (firstImageUrl.isEmpty()) firstImageUrl.add(imgUrl)
                        paragraphs.add(ArticleParagraph(
                            paragraphIndex = index++,
                            text = caption,
                            imageUrl = imgUrl,
                            paragraphType = ParagraphType.IMAGE
                        ))
                    }
                }
                "ul", "ol" -> {
                    for (li in child.select("li")) {
                        val text = li.text().trim()
                        if (text.isNotBlank()) {
                            paragraphs.add(ArticleParagraph(paragraphIndex = index++, text = text, paragraphType = ParagraphType.LIST))
                        }
                    }
                }
                "p" -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) {
                        paragraphs.add(ArticleParagraph(paragraphIndex = index++, text = text, paragraphType = ParagraphType.TEXT))
                    }
                }
                "div" -> {
                    extractFromDiv(child, paragraphs, firstImageUrl) { index++ }
                }
            }
        }

        return paragraphs
    }

    private fun extractFromDiv(
        div: Element,
        paragraphs: MutableList<ArticleParagraph>,
        firstImageUrl: MutableList<String>,
        nextIndex: () -> Int
    ) {
        for (child in div.children()) {
            if (child.tagName() in listOf("aside", "nav", "footer", "style", "script")) continue

            when (child.tagName()) {
                "h2", "h3" -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) {
                        paragraphs.add(ArticleParagraph(paragraphIndex = nextIndex(), text = text, paragraphType = ParagraphType.HEADING))
                    }
                }
                "blockquote" -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) {
                        paragraphs.add(ArticleParagraph(paragraphIndex = nextIndex(), text = text, paragraphType = ParagraphType.QUOTE))
                    }
                }
                "figure" -> {
                    val img = child.selectFirst("img")
                    val imgUrl = img?.absUrl("src")?.ifBlank { img.attr("srcset").split(" ").firstOrNull() }
                    val caption = child.selectFirst(".caption-bar .eza-caption, figcaption")?.text()?.trim() ?: ""
                    if (!imgUrl.isNullOrBlank()) {
                        if (firstImageUrl.isEmpty()) firstImageUrl.add(imgUrl)
                        paragraphs.add(ArticleParagraph(
                            paragraphIndex = nextIndex(),
                            text = caption,
                            imageUrl = imgUrl,
                            paragraphType = ParagraphType.IMAGE
                        ))
                    }
                }
                "ul", "ol" -> {
                    for (li in child.select("li")) {
                        val text = li.text().trim()
                        if (text.isNotBlank()) {
                            paragraphs.add(ArticleParagraph(paragraphIndex = nextIndex(), text = text, paragraphType = ParagraphType.LIST))
                        }
                    }
                }
                "p" -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) {
                        paragraphs.add(ArticleParagraph(paragraphIndex = nextIndex(), text = text, paragraphType = ParagraphType.TEXT))
                    }
                }
                "div" -> {
                    extractFromDiv(child, paragraphs, firstImageUrl, nextIndex)
                }
            }
        }
    }

    private fun determineCoverImage(
        ogImage: String?,
        paragraphs: List<ArticleParagraph>
    ): Pair<String?, List<ArticleParagraph>> {
        if (ogImage == null) return null to paragraphs
        val firstImageParagraph = paragraphs.firstOrNull { it.paragraphType == ParagraphType.IMAGE }
        return if (firstImageParagraph != null && firstImageParagraph.imageUrl == ogImage) {
            ogImage to paragraphs.filter { it !== firstImageParagraph }
        } else {
            ogImage to paragraphs
        }
    }

    companion object {
        private const val BASE_URL = "https://www.csmonitor.com"
    }
}
