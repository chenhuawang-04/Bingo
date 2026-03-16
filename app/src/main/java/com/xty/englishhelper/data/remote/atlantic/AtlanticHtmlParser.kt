package com.xty.englishhelper.data.remote.atlantic

import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ParagraphType
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class AtlanticArticlePreview(
    val title: String,
    val url: String,
    val trailText: String? = null,
    val thumbnailUrl: String? = null,
    val author: String? = null
)

data class AtlanticArticleDetail(
    val title: String,
    val author: String,
    val summary: String,
    val coverImageUrl: String?,
    val paragraphs: List<ArticleParagraph>,
    val source: String,
    val sourceUrl: String
)

class AtlanticHtmlParser {

    fun parseSectionPage(html: String): List<AtlanticArticlePreview> {
        val doc = Jsoup.parse(html, BASE_URL)
        val results = mutableListOf<AtlanticArticlePreview>()
        val seenUrls = mutableSetOf<String>()

        val container = doc.selectFirst("main") ?: doc.body()
        val links = container.select("a[href]")
        for (link in links) {
            val preview = extractPreviewFromLink(link, seenUrls) ?: continue
            results.add(preview)
        }

        return results
    }

    fun parseArticlePage(html: String, articleUrl: String): AtlanticArticleDetail {
        val doc = Jsoup.parse(html, articleUrl)
        val jsonLd = extractJsonLd(doc)
        val articleJson = extractArticleJson(doc)
            ?: throw AtlanticParseException("Missing article JSON")

        val title = articleJson?.optString("title")?.takeIf { it.isNotBlank() }
            ?: jsonLd?.optString("headline")?.takeIf { it.isNotBlank() }
            ?: jsonLd?.optString("alternativeHeadline")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()

        val author = extractAuthor(articleJson, jsonLd).ifBlank {
            doc.selectFirst("meta[name=author]")?.attr("content")?.trim().orEmpty()
        }
        val summary = articleJson?.optString("dek")?.takeIf { it.isNotBlank() }
            ?: jsonLd?.optString("description")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()

        val section = articleJson
            ?.optJSONObject("primaryChannel")
            ?.optString("displayName")
            ?.takeIf { it.isNotBlank() }
            ?: jsonLd?.optString("articleSection").orEmpty()

        val coverImage = extractLeadImage(articleJson)
            ?: extractJsonLdImage(jsonLd)
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }

        val paragraphs = buildParagraphs(articleJson)
        if (paragraphs.isEmpty()) {
            throw AtlanticParseException("Empty article content")
        }
        val sourceLabel = if (section.isNotBlank()) "The Atlantic \u00B7 $section" else "The Atlantic"

        return AtlanticArticleDetail(
            title = title,
            author = author,
            summary = summary,
            coverImageUrl = coverImage,
            paragraphs = paragraphs,
            source = sourceLabel,
            sourceUrl = articleUrl
        )
    }

    private fun extractPreviewFromLink(
        link: Element,
        seenUrls: MutableSet<String>
    ): AtlanticArticlePreview? {
        val href = resolveUrl(link)
        if (!isArticleUrl(href) || !seenUrls.add(href)) return null

        val title = link.selectFirst("h3, h2, h1")?.text()?.trim()
            ?: link.text().trim()
        if (title.isBlank() || title.length < 5) return null

        val parent = link.closest("article, li, div, section")
        val thumbnail = parent?.selectFirst("img")?.let { img ->
            img.absUrl("src").ifBlank {
                img.attr("data-src").ifBlank {
                    img.attr("data-original").ifBlank {
                        img.attr("srcset").split(" ").firstOrNull()
                    }
                }
            }
        }
        val trailText = parent?.selectFirst("p")?.text()?.trim()
        val author = parent?.selectFirst(
            ".byline, [data-event-element=byline], [data-testid=byline], [class*=Byline]"
        )?.text()?.trim()

        return AtlanticArticlePreview(
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
        if (!lower.contains("theatlantic.com")) return false

        val clean = lower.substringBefore('#').substringBefore('?')
        if (clean.contains("/author/") || clean.contains("/tag/") || clean.contains("/category/")) return false
        if (clean.contains("/newsletters/") || clean.contains("/events/")) return false

        return Regex(".*theatlantic\\.com/.*/\\d{4}/\\d{1,2}/[^/]+/\\d+/?")
            .matches(clean)
    }

    private fun extractJsonLd(doc: Document): JSONObject? {
        val scripts = doc.select("script[type=application/ld+json]")
        for (script in scripts) {
            val raw = script.data().trim()
            if (raw.isBlank()) continue
            try {
                val parsed = JSONObject(raw)
                val type = parsed.optString("@type")
                if (type.contains("NewsArticle", ignoreCase = true) ||
                    type.contains("Article", ignoreCase = true)
                ) {
                    return parsed
                }
            } catch (_: Exception) {
                // Ignore invalid JSON-LD blocks
            }
        }
        return null
    }

    private fun extractArticleJson(doc: Document): JSONObject? {
        val nextData = doc.selectFirst("script#__NEXT_DATA__")?.data()?.trim().orEmpty()
        if (nextData.isBlank()) return null
        val root = try {
            JSONObject(nextData)
        } catch (_: Exception) {
            return null
        }
        val urqlState = root.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("urqlState")
            ?: return null

        val keys = urqlState.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = urqlState.optJSONObject(key) ?: continue
            val dataStr = entry.optString("data")
            if (dataStr.isBlank() || !dataStr.contains("\"article\"")) continue
            val dataJson = try {
                JSONObject(dataStr)
            } catch (_: Exception) {
                continue
            }
            val article = dataJson.optJSONObject("article") ?: continue
            val content = article.optJSONArray("content")
            if (content != null) {
                return article
            }
        }
        return null
    }

    private fun extractAuthor(articleJson: JSONObject?, jsonLd: JSONObject?): String {
        val fromArticle = articleJson?.optJSONArray("authors")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.optString("displayName")?.takeIf { it.isNotBlank() }
            }.joinToString(", ").takeIf { it.isNotBlank() }
        }
        if (!fromArticle.isNullOrBlank()) return fromArticle

        val author = jsonLd?.opt("author") ?: return ""
        return when (author) {
            is JSONArray -> {
                (0 until author.length()).mapNotNull { i ->
                    author.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
                }.joinToString(", ")
            }
            is JSONObject -> author.optString("name")?.takeIf { it.isNotBlank() }.orEmpty()
            else -> ""
        }
    }

    private fun extractLeadImage(articleJson: JSONObject?): String? {
        val leadArt = articleJson?.optJSONObject("leadArt") ?: return null
        val image = leadArt.optJSONObject("image") ?: return null
        return image.optString("url").takeIf { it.isNotBlank() }
    }

    private fun extractJsonLdImage(jsonLd: JSONObject?): String? {
        jsonLd ?: return null
        return try {
            val image = jsonLd.opt("image")
            when (image) {
                is JSONArray -> {
                    val first = image.opt(0)
                    when (first) {
                        is JSONObject -> first.optString("url").takeIf { it.isNotBlank() }
                        is String -> first.takeIf { it.isNotBlank() }
                        else -> null
                    }
                }
                is JSONObject -> image.optString("url").takeIf { it.isNotBlank() }
                is String -> image.takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildParagraphs(articleJson: JSONObject?): List<ArticleParagraph> {
        val content = articleJson?.optJSONArray("content") ?: return emptyList()
        val paragraphs = mutableListOf<ArticleParagraph>()
        var index = 0

        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            if (isWhyWeWroteThis(item)) continue

            val type = item.optString("__typename")
            if (type != "ArticleParagraphContent") continue

            val html = item.optString("innerHtml")
            val text = Jsoup.parse(html).text().trim()
            if (text.isBlank()) continue

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

    private fun isWhyWeWroteThis(item: JSONObject): Boolean {
        val type = item.optString("__typename")
        if (type.contains("WhyWeWroteThis", ignoreCase = true)) return true

        val subtype = item.optString("subtype")
        if (subtype.contains("WHY_WE_WROTE_THIS", ignoreCase = true)) return true

        val idAttr = item.optString("idAttr")
        if (idAttr.equals("why-we-wrote-this", ignoreCase = true) ||
            idAttr.startsWith("why-we-wrote-this", ignoreCase = true)
        ) {
            return true
        }

        val contextType = item.optString("contextType")
        if (contextType.equals("WHY_WE_WROTE_THIS", ignoreCase = true)) return true

        val slug = item.optString("slug")
        if (slug.equals("why-we-wrote-this", ignoreCase = true)) return true

        val label = item.optString("label")
        if (label.equals("Why We Wrote This", ignoreCase = true)) return true

        return false
    }

    companion object {
        private const val BASE_URL = "https://www.theatlantic.com"
    }
}

class AtlanticParseException(message: String) : Exception(message)
