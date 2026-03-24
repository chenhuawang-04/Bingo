package com.xty.englishhelper.data.remote.csmonitor

import com.xty.englishhelper.domain.article.OnlineArticleSourceUrl
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ParagraphType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

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
    val sourceUrl: String,
    val requestedUrl: String = ""
)

class CsMonitorHtmlParser {

    fun extractRssFullUrl(html: String, articleUrl: String): String? {
        val doc = Jsoup.parse(html, articleUrl)
        return extractResolvedRssFullUrl(doc, articleUrl)
    }

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

    fun parseArticlePage(
        html: String,
        articleUrl: String,
        rssFullXml: String? = null
    ): CsMonitorArticleDetail {
        val doc = Jsoup.parse(html, articleUrl)
        val pageMetadata = extractPageMetadata(doc, articleUrl)
        return buildArticleDetail(
            doc = doc,
            articleUrl = articleUrl,
            pageMetadata = pageMetadata,
            resolvedRssXml = rssFullXml
        )
    }

    suspend fun parseArticlePage(
        html: String,
        articleUrl: String,
        rssFullLoader: suspend (String) -> String?
    ): CsMonitorArticleDetail {
        val doc = Jsoup.parse(html, articleUrl)
        val pageMetadata = extractPageMetadata(doc, articleUrl)
        val resolvedRssXml = pageMetadata.rssFullUrl?.let { url -> rssFullLoader(url) }
        return buildArticleDetail(
            doc = doc,
            articleUrl = articleUrl,
            pageMetadata = pageMetadata,
            resolvedRssXml = resolvedRssXml
        )
    }

    private fun buildArticleDetail(
        doc: Document,
        articleUrl: String,
        pageMetadata: PageMetadata,
        resolvedRssXml: String?
    ): CsMonitorArticleDetail {
        val rssData = resolvedRssXml
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseRssFull)

        val title = rssData?.title?.takeIf { it.isNotBlank() } ?: pageMetadata.title
        val author = rssData?.author?.takeIf { it.isNotBlank() } ?: pageMetadata.author
        val paragraphs = rssData?.paragraphs?.takeIf { it.isNotEmpty() }
            ?: extractFallbackParagraphs(doc, pageMetadata.coverImageUrl)
        val coverImageUrl = rssData?.coverImageUrl?.takeIf { it.isNotBlank() } ?: pageMetadata.coverImageUrl
        val sourceLabel = buildSourceLabel(pageMetadata.section)

        return CsMonitorArticleDetail(
            title = title,
            author = author,
            summary = pageMetadata.summary,
            coverImageUrl = coverImageUrl,
            paragraphs = paragraphs,
            source = sourceLabel,
            sourceUrl = pageMetadata.canonicalUrl,
            requestedUrl = articleUrl
        )
    }

    private fun extractPreviewFromLink(
        link: Element,
        seenUrls: MutableSet<String>
    ): CsMonitorArticlePreview? {
        val href = resolvePreviewUrl(link)
        if (!isArticleUrl(href) || !seenUrls.add(href)) return null

        val title = normalizeText(link.text())
        if (title.isBlank() || title.length < 5) return null

        val parent = link.closest("article, li, div")
        val thumbnail = parent?.selectFirst("img")?.let { img ->
            img.absUrl("src").ifBlank {
                img.attr("data-src").ifBlank {
                    img.attr("data-original").ifBlank {
                        img.attr("srcset").split(" ").firstOrNull().orEmpty()
                    }
                }
            }
        }?.takeIf { it.isNotBlank() }
        val trailText = parent?.selectFirst(
            "p, .story_summary, .story-summary, .story_deck, .story-deck, .summary, .deck"
        )?.text()?.let(::normalizeText)?.takeIf { it.isNotBlank() && it != title }
        val author = parent?.selectFirst(".byline, .author, .story_byline")?.text()
            ?.let(::normalizeText)
            ?.takeIf { it.isNotBlank() }

        return CsMonitorArticlePreview(
            title = title,
            url = href,
            trailText = trailText,
            thumbnailUrl = thumbnail,
            author = author
        )
    }

    private fun extractPageMetadata(doc: Document, articleUrl: String): PageMetadata {
        val jsonLd = extractJsonLd(doc)
        val pageMeta = extractJsonObjectScript(doc, "page-meta-data")
        val sections = extractSections(pageMeta)
        val canonicalUrl = extractCanonicalUrl(doc, articleUrl)
        val rssFullUrl = extractResolvedRssFullUrl(doc, articleUrl)

        val title = jsonLd?.optString("headline")?.takeIf { it.isNotBlank() }
            ?: extractTitle(doc)
        val summary = extractSummary(doc, jsonLd)
        val author = extractAuthors(doc, jsonLd, pageMeta)
        val section = sections.lastOrNull()
            ?: metaContent(doc, prop = "article:section").takeIf { it.isNotBlank() }
            ?: ""
        val coverImageUrl = extractCoverImage(doc, jsonLd)

        return PageMetadata(
            title = title,
            author = author,
            summary = summary,
            section = section,
            coverImageUrl = coverImageUrl,
            rssFullUrl = rssFullUrl,
            canonicalUrl = canonicalUrl
        )
    }

    private fun extractJsonLd(doc: Document): JSONObject? {
        val script = doc.selectFirst("script#meta-schema-org")
            ?: doc.selectFirst("script[type=application/ld+json]")
            ?: return null
        val raw = script.data().trim().ifBlank { script.html().trim() }
        return try {
            when {
                raw.startsWith("[") -> findNewsArticleInArray(JSONArray(raw))
                else -> {
                    val obj = JSONObject(raw)
                    when {
                        obj.has("@graph") -> findNewsArticleInArray(obj.getJSONArray("@graph"))
                        isNewsArticleType(obj) -> obj
                        else -> null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJsonObjectScript(doc: Document, scriptId: String): JSONObject? {
        val script = doc.selectFirst("script#$scriptId") ?: return null
        val raw = script.data().trim().ifBlank { script.html().trim() }
        return try {
            JSONObject(raw)
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

    private fun extractSections(pageMeta: JSONObject?): List<String> {
        val sections = pageMeta?.optJSONArray("sections") ?: return emptyList()
        return buildList {
            for (i in 0 until sections.length()) {
                val value = normalizeText(sections.optString(i))
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun extractAuthorFromJsonLd(jsonLd: JSONObject?): List<String> {
        jsonLd ?: return emptyList()
        return try {
            when (val author = jsonLd.opt("author")) {
                is JSONArray -> buildList {
                    for (i in 0 until author.length()) {
                        when (val item = author.opt(i)) {
                            is JSONObject -> normalizeText(item.optString("name"))
                                .takeIf { it.isNotBlank() }
                                ?.let(::add)
                            is String -> normalizeText(item)
                                .takeIf { it.isNotBlank() }
                                ?.let(::add)
                        }
                    }
                }
                is JSONObject -> listOfNotNull(
                    normalizeText(author.optString("name")).takeIf { it.isNotBlank() }
                )
                is String -> listOfNotNull(normalizeText(author).takeIf { it.isNotBlank() })
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractAuthors(doc: Document, jsonLd: JSONObject?, pageMeta: JSONObject?): String {
        val authors = linkedSetOf<String>()

        val rawAuthors = pageMeta?.optJSONArray("authors")
        if (rawAuthors != null) {
            for (i in 0 until rawAuthors.length()) {
                normalizeText(rawAuthors.optString(i)).takeIf { it.isNotBlank() }?.let(authors::add)
            }
        }

        extractAuthorFromJsonLd(jsonLd).forEach(authors::add)
        metaContent(doc, name = "csm.author").takeIf { it.isNotBlank() }?.let(authors::add)
        extractAuthor(doc).takeIf { it.isNotBlank() }?.let(authors::add)

        return authors.joinToString(", ")
    }

    private fun extractCoverImage(doc: Document, jsonLd: JSONObject?): String? {
        val jsonLdImage = try {
            when (val image = jsonLd?.opt("image")) {
                is JSONArray -> when (val first = image.opt(0)) {
                    is JSONObject -> first.optString("url").takeIf { it.isNotBlank() }
                    is String -> first.takeIf { it.isNotBlank() }
                    else -> null
                }
                is JSONObject -> image.optString("url").takeIf { it.isNotBlank() }
                is String -> image.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        if (!jsonLdImage.isNullOrBlank()) return jsonLdImage

        val standardImage = metaContent(doc, name = "csm.image.std")
        if (standardImage.isNotBlank()) return standardImage

        return doc.selectFirst("meta[property=og:image], meta[name=twitter:image]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractTitle(doc: Document): String {
        return doc.selectFirst("h1#headline.eza-title, h1#headline")?.text()?.let(::normalizeText)
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.let(::normalizeText)
                ?.takeIf { it.isNotBlank() }
            ?: normalizeText(doc.title())
    }

    private fun extractSummary(doc: Document, jsonLd: JSONObject?): String {
        val jsonDescription = jsonLd?.optString("description")?.let(::normalizeText)
        if (!jsonDescription.isNullOrBlank()) return jsonDescription

        val domSummary = doc.selectFirst("div#summary.eza-summary p, #summary p")?.text()?.let(::normalizeText)
        if (!domSummary.isNullOrBlank()) return domSummary

        return metaContent(doc, name = "description")
            .takeIf { it.isNotBlank() }
            ?: metaContent(doc, prop = "og:description")
    }

    private fun extractAuthor(doc: Document): String {
        return doc.selectFirst("ul.story-bylines li.author .staff-name, .story-bylines .staff-name")?.text()
            ?.let(::normalizeText)
            ?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun extractFallbackParagraphs(
        doc: Document,
        coverImageUrl: String?
    ): List<ArticleParagraph> {
        val body = doc.selectFirst("div.eza-body.prem.truncate-for-paywall")
            ?: doc.selectFirst("article#story-content div.eza-body")
            ?: doc.selectFirst(".eza-body:not(#summary)")
            ?: doc.selectFirst("article#story-content")
            ?: doc.body()

        val clone = body.clone()
        cleanupBody(clone)
        val extracted = extractParagraphs(clone)
            .filterNot { shouldSkipParagraph(normalizeText(it.text)) }
        val (_, finalParagraphs) = determineCoverImage(coverImageUrl, extracted)
        return finalParagraphs.mapIndexed { index, paragraph -> paragraph.copy(paragraphIndex = index) }
    }

    private fun cleanupBody(body: Element) {
        body.select(
            "aside.injection, .article-story-promo, .newsletter-email-signup, .story-half, " +
                "#paywall, #inbody-related-stories, .promo_links, .eza-callout, .ad, script, style, nav, footer"
        ).forEach { it.remove() }
    }

    private fun extractParagraphs(body: Element): List<ArticleParagraph> {
        val paragraphs = mutableListOf<ArticleParagraph>()
        var index = 0

        fun addParagraph(text: String, type: ParagraphType, imageUrl: String? = null) {
            if (text.isBlank() && imageUrl.isNullOrBlank()) return
            paragraphs.add(
                ArticleParagraph(
                    paragraphIndex = index++,
                    text = text,
                    imageUrl = imageUrl,
                    paragraphType = type
                )
            )
        }

        fun visit(node: Element) {
            if (node.tagName() in SKIPPED_TAGS) return
            when (node.tagName()) {
                "h2", "h3" -> addParagraph(normalizeText(node.text()), ParagraphType.HEADING)
                "blockquote" -> addParagraph(normalizeText(node.text()), ParagraphType.QUOTE)
                "figure" -> buildImageParagraph(node)?.let { imageParagraph ->
                    addParagraph(imageParagraph.text, ParagraphType.IMAGE, imageParagraph.imageUrl)
                }
                "ul", "ol" -> node.children()
                    .filter { it.tagName() == "li" }
                    .forEach { li -> addParagraph(normalizeText(li.text()), ParagraphType.LIST) }
                "p" -> addParagraph(normalizeText(node.text()), ParagraphType.TEXT)
                else -> node.children().forEach(::visit)
            }
        }

        body.children().forEach(::visit)
        return paragraphs
    }

    private fun buildImageParagraph(figure: Element): ArticleParagraph? {
        val image = figure.selectFirst("img") ?: return null
        val imageUrl = image.absUrl("src").ifBlank {
            image.attr("data-src").ifBlank {
                image.attr("srcset").split(" ").firstOrNull().orEmpty()
            }
        }.takeIf { it.isNotBlank() } ?: return null
        val caption = figure.selectFirst(".caption-bar .eza-caption, figcaption")
            ?.text()
            ?.let(::normalizeText)
            .orEmpty()
        return ArticleParagraph(
            text = caption,
            imageUrl = imageUrl,
            paragraphType = ParagraphType.IMAGE
        )
    }

    private fun determineCoverImage(
        coverImageUrl: String?,
        paragraphs: List<ArticleParagraph>
    ): Pair<String?, List<ArticleParagraph>> {
        if (coverImageUrl.isNullOrBlank()) return null to paragraphs
        val firstImageParagraph = paragraphs.firstOrNull { it.paragraphType == ParagraphType.IMAGE }
        return if (firstImageParagraph != null && firstImageParagraph.imageUrl == coverImageUrl) {
            coverImageUrl to paragraphs.filter { it !== firstImageParagraph }
        } else {
            coverImageUrl to paragraphs
        }
    }

    private fun parseRssFull(xml: String): RssFullData? {
        return runCatching {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val item = doc.selectFirst("channel > item") ?: return null
            val title = normalizeText(item.getElementsByTag("title").first()?.text())
            val author = normalizeText(item.getElementsByTag("dc:creator").first()?.text())
            val descriptionRaw = item.getElementsByTag("description").first()?.text().orEmpty()
            val paragraphs = parseRssDescription(descriptionRaw)
            val mediaContent = item.getElementsByTag("media:content").first()
            val enclosure = item.getElementsByTag("enclosure").first()
            val coverImageUrl = normalizeText(
                mediaContent?.attr("url").orEmpty().ifBlank {
                    enclosure?.attr("url").orEmpty()
                }
            ).ifBlank { null }

            RssFullData(
                title = title,
                author = author,
                coverImageUrl = coverImageUrl,
                paragraphs = paragraphs
            )
        }.getOrNull()
    }

    private fun parseRssDescription(descriptionHtml: String): List<ArticleParagraph> {
        if (descriptionHtml.isBlank()) return emptyList()
        val decoded = Parser.unescapeEntities(descriptionHtml, false)
        val fragment = Jsoup.parseBodyFragment(decoded)
        val paragraphs = mutableListOf<ArticleParagraph>()
        var index = 0

        for (node in fragment.body().children()) {
            if (node.tagName() != "p") continue
            if ("promo_links" in node.classNames()) continue
            val text = normalizeText(node.text())
            if (shouldSkipParagraph(text)) continue
            paragraphs.add(
                ArticleParagraph(
                    paragraphIndex = index++,
                    text = text,
                    paragraphType = ParagraphType.TEXT
                )
            )
        }

        return paragraphs
    }

    private fun resolvePreviewUrl(link: Element): String {
        val raw = link.absUrl("href").ifBlank { link.attr("href") }
        return normalizeArticleUrl(raw, BASE_URL)
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

    private fun metaContent(doc: Document, name: String? = null, prop: String? = null): String {
        val selector = when {
            !name.isNullOrBlank() -> "meta[name=\"$name\"]"
            !prop.isNullOrBlank() -> "meta[property=\"$prop\"]"
            else -> return ""
        }
        return normalizeText(doc.selectFirst(selector)?.attr("content"))
    }

    private fun normalizeText(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return Parser.unescapeEntities(value, false)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    private fun shouldSkipParagraph(text: String): Boolean {
        if (text.isBlank()) return true
        return PROMO_PREFIXES.any { prefix -> text.startsWith(prefix, ignoreCase = true) }
    }

    private fun extractResolvedRssFullUrl(doc: Document, articleUrl: String): String? {
        val rawRssFullUrl = metaContent(doc, name = "csm.content.body")
        if (rawRssFullUrl.isBlank()) return null
        val canonicalUrl = extractCanonicalUrl(doc, articleUrl)
        return normalizeArticleUrl(rawRssFullUrl, canonicalUrl.ifBlank { articleUrl })
            .takeIf { it.isNotBlank() }
    }

    private fun extractCanonicalUrl(doc: Document, articleUrl: String): String {
        val canonical = doc.selectFirst("link[rel=canonical]")
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?: articleUrl
        return normalizeArticleUrl(canonical, articleUrl)
    }

    private fun normalizeArticleUrl(url: String, baseUrl: String): String {
        if (url.isBlank()) return ""
        val resolved = resolveAbsoluteUrl(baseUrl, url)
        return OnlineArticleSourceUrl.normalize(resolved)
    }

    private fun resolveAbsoluteUrl(baseUrl: String, rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        return runCatching { URI(baseUrl).resolve(rawUrl).toString() }.getOrDefault(rawUrl)
    }

    private fun buildSourceLabel(section: String): String {
        return "CSMonitor" + if (section.isNotBlank()) " \u00B7 $section" else ""
    }

    private data class PageMetadata(
        val title: String,
        val author: String,
        val summary: String,
        val section: String,
        val coverImageUrl: String?,
        val rssFullUrl: String?,
        val canonicalUrl: String
    )

    private data class RssFullData(
        val title: String,
        val author: String,
        val coverImageUrl: String?,
        val paragraphs: List<ArticleParagraph>
    )

    companion object {
        private const val BASE_URL = "https://www.csmonitor.com"
        private val SKIPPED_TAGS = setOf("aside", "nav", "footer", "style", "script")
        private val PROMO_PREFIXES = listOf(
            "Read this story at csmonitor.com",
            "Become a part of the Monitor community"
        )
    }
}
