package com.xty.englishhelper.data.remote.guardian

import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ParagraphType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class GuardianArticlePreview(
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val trailText: String? = null,
    val author: String? = null
)

data class GuardianArticleDetail(
    val title: String,
    val author: String,
    val summary: String,
    val coverImageUrl: String?,
    val paragraphs: List<ArticleParagraph>,
    val source: String,
    val sourceUrl: String
)

class GuardianHtmlParser {

    fun parseSectionPage(html: String): List<GuardianArticlePreview> {
        val doc = Jsoup.parse(html, BASE_URL)
        val results = mutableListOf<GuardianArticlePreview>()
        val seenUrls = mutableSetOf<String>()

        // Primary strategy: card-like containers with headline links
        val cards = doc.select("[data-link-name=article], .fc-item, .js-headline-text")
        if (cards.isNotEmpty()) {
            for (card in cards) {
                val preview = extractPreviewFromCard(card, seenUrls) ?: continue
                results.add(preview)
            }
        }

        // Fallback: look for h3 links within content area
        if (results.isEmpty()) {
            val headlineLinks = doc.select("h3 a[href], h2 a[href]")
            for (link in headlineLinks) {
                val preview = extractPreviewFromLink(link, seenUrls) ?: continue
                results.add(preview)
            }
        }

        // Last fallback: any article-like links in main content
        if (results.isEmpty()) {
            val contentLinks = doc.select("a[href*='/20']") // Guardian article URLs contain year
            for (link in contentLinks) {
                val preview = extractPreviewFromLink(link, seenUrls) ?: continue
                results.add(preview)
            }
        }

        return results
    }

    private fun extractPreviewFromCard(
        card: Element,
        seenUrls: MutableSet<String>
    ): GuardianArticlePreview? {
        val link = if (card.tagName() == "a") card else card.selectFirst("a[href]") ?: return null
        val href = resolveUrl(link)
        if (!isArticleUrl(href) || !seenUrls.add(href)) return null

        val title = card.selectFirst("h3, .fc-item__title, .js-headline-text")?.text()
            ?: link.text()
        if (title.isBlank() || isCommentTitle(title)) return null

        val thumbnail = card.selectFirst("img")?.let { img ->
            img.absUrl("src").ifBlank { img.attr("srcset").split(" ").firstOrNull() }
        }

        val trailText = card.selectFirst(".fc-item__standfirst, .trail__body, [data-link-name=trail-text]")?.text()

        return GuardianArticlePreview(
            title = title.trim(),
            url = href,
            thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() },
            trailText = trailText?.takeIf { it.isNotBlank() }
        )
    }

    private fun extractPreviewFromLink(
        link: Element,
        seenUrls: MutableSet<String>
    ): GuardianArticlePreview? {
        val href = resolveUrl(link)
        if (!isArticleUrl(href) || !seenUrls.add(href)) return null

        val title = link.text().trim()
        if (title.isBlank() || title.length < 5 || isCommentTitle(title)) return null

        // Look for sibling/parent thumbnail
        val parent = link.closest("li, div, article, section")
        val thumbnail = parent?.selectFirst("img")?.let { img ->
            img.absUrl("src").ifBlank { img.attr("srcset").split(" ").firstOrNull() }
        }

        val trailText = parent?.selectFirst("p, .trail-text, .standfirst")?.text()

        return GuardianArticlePreview(
            title = title,
            url = href,
            thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() },
            trailText = trailText?.takeIf { it.isNotBlank() && it != title }
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
        if (lower.contains("#comments") || lower.contains("/discussion") || lower.contains("/comments")) return false
        if (lower.contains("/live/")) return false
        // Must be a Guardian URL with a date pattern (day can be 1 or 2 digits)
        return lower.contains("theguardian.com") && lower.matches(Regex(".*\\d{4}/[a-z]{3}/\\d{1,2}/.*"))
    }

    private fun isCommentTitle(title: String): Boolean {
        val lower = title.lowercase().trim()
        // Only filter pure "comments" or "N comments" patterns, not editorial columns like "Comment is free"
        if (lower == "comments") return true
        if (Regex("^\\d+\\s+comments?$").matches(lower)) return true
        if (lower.startsWith("comments (")) return true
        return false
    }

    fun parseArticlePage(html: String, articleUrl: String): GuardianArticleDetail {
        val doc = Jsoup.parse(html, articleUrl)

        // Try JSON-LD for reliable metadata
        val jsonLd = extractJsonLd(doc)

        val title = jsonLd?.optString("headline")?.takeIf { it.isNotBlank() }
            ?: extractTitle(doc)
        val author = extractAuthorFromJsonLd(jsonLd)
            ?: extractAuthor(doc)
        val summary = extractSummary(doc)
        val ogImage = extractCoverImage(doc, jsonLd)
        val section = extractSection(doc)
        val paragraphs = extractParagraphs(doc, ogImage)
        val (coverImage, finalParagraphs) = determineCoverImage(ogImage, paragraphs)

        return GuardianArticleDetail(
            title = title,
            author = author,
            summary = summary,
            coverImageUrl = coverImage,
            paragraphs = finalParagraphs,
            source = "The Guardian" + if (section.isNotBlank()) " \u00B7 $section" else "",
            sourceUrl = articleUrl
        )
    }

    private fun extractJsonLd(doc: Document): org.json.JSONObject? {
        return try {
            val scripts = doc.select("script[type=application/ld+json]")
            for (script in scripts) {
                val json = org.json.JSONObject(script.data())
                val type = json.optString("@type", "")
                if (type in listOf("NewsArticle", "Article", "ReportageNewsArticle")) {
                    return json
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAuthorFromJsonLd(jsonLd: org.json.JSONObject?): String? {
        jsonLd ?: return null
        return try {
            val author = jsonLd.opt("author") ?: return null
            when (author) {
                is org.json.JSONArray -> {
                    (0 until author.length()).mapNotNull { i ->
                        author.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
                    }.joinToString(", ").takeIf { it.isNotBlank() }
                }
                is org.json.JSONObject -> author.optString("name")?.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCoverImage(doc: Document, jsonLd: org.json.JSONObject?): String? {
        // Priority: JSON-LD image -> og:image -> twitter:image
        val jsonLdImage = try {
            val img = jsonLd?.opt("image")
            when (img) {
                is org.json.JSONArray -> img.optString(0)?.takeIf { it.isNotBlank() }
                is String -> img.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (jsonLdImage != null) return jsonLdImage

        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
        if (ogImage != null) return ogImage

        return doc.selectFirst("meta[name=twitter:image]")?.attr("content")?.takeIf { it.isNotBlank() }
    }

    private fun extractTitle(doc: Document): String {
        return doc.selectFirst("[data-gu-name=headline] h1")?.text()?.trim()
            ?: doc.selectFirst("article h1")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().trim()
    }

    private fun extractAuthor(doc: Document): String {
        // Try stable attribute first (per format guide)
        val bylineAddr = doc.selectFirst("address[data-gu-name=byline] a")?.text()?.trim()
        if (!bylineAddr.isNullOrBlank()) return bylineAddr

        val address = doc.selectFirst("address a[rel=author], address")?.text()?.trim()
        if (!address.isNullOrBlank()) return address

        val metaAuthor = doc.selectFirst("meta[name=author], meta[property=article:author]")?.attr("content")?.trim()
        if (!metaAuthor.isNullOrBlank()) return metaAuthor

        val byline = doc.selectFirst("[data-gu-name=meta] a, .byline, .contributor")?.text()?.trim()
        if (!byline.isNullOrBlank()) return byline

        return ""
    }

    private fun extractSummary(doc: Document): String {
        val standfirst = doc.selectFirst("[data-gu-name=standfirst], .standfirst, [data-component=standfirst]")?.text()?.trim()
        if (!standfirst.isNullOrBlank()) return standfirst

        val metaDesc = doc.selectFirst("meta[name=description], meta[property=og:description]")?.attr("content")?.trim()
        return metaDesc ?: ""
    }

    private fun extractSection(doc: Document): String {
        val section = doc.selectFirst("meta[property=article:section]")?.attr("content")?.trim()
        if (!section.isNullOrBlank()) return section.replaceFirstChar { it.uppercase() }

        val breadcrumb = doc.selectFirst("[data-component=section-label] a, .content__section-label a")?.text()?.trim()
        return breadcrumb ?: ""
    }

    private fun extractParagraphs(doc: Document, ogImage: String?): List<ArticleParagraph> {
        // Use the most specific body selector first (per format guide), then fall back
        val articleBody = doc.selectFirst("div.article-body-commercial-selector")
            ?: doc.selectFirst("[data-gu-name=body]")
            ?: doc.selectFirst(".content__article-body")
            ?: doc.selectFirst("article")
            ?: doc.body()

        val paragraphs = mutableListOf<ArticleParagraph>()
        var index = 0
        val firstImageUrl = mutableListOf<String>() // track first image

        for (child in articleBody.children()) {
            // Skip navigation, aside, footer elements
            if (child.tagName() in listOf("aside", "nav", "footer", "style", "script")) continue
            // Skip elements inside those containers
            if (child.closest("aside, nav, footer") != null && child.closest("[data-gu-name=body]") == null) continue

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
                    val caption = child.selectFirst("figcaption")?.text()?.trim() ?: ""
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
                    // Recursively handle divs that contain article content
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
                    val caption = child.selectFirst("figcaption")?.text()?.trim() ?: ""
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

    private fun determineCoverImage(ogImage: String?, paragraphs: List<ArticleParagraph>): Pair<String?, List<ArticleParagraph>> {
        if (ogImage == null) return null to paragraphs

        // Always use og:image as cover. If the first inline image is the same, remove it to avoid duplication.
        val firstImageParagraph = paragraphs.firstOrNull { it.paragraphType == ParagraphType.IMAGE }
        return if (firstImageParagraph != null && firstImageParagraph.imageUrl == ogImage) {
            ogImage to paragraphs.filter { it !== firstImageParagraph }
        } else {
            ogImage to paragraphs
        }
    }

    companion object {
        private const val BASE_URL = "https://www.theguardian.com"
    }
}
