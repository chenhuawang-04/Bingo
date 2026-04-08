package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.cambridge.CambridgeHtmlParser
import com.xty.englishhelper.data.remote.cambridge.CambridgeService
import com.xty.englishhelper.data.remote.oed.OedHtmlParser
import com.xty.englishhelper.data.remote.oed.OedService
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.CloudWordExample
import com.xty.englishhelper.domain.repository.CloudWordExampleRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudWordExampleRepositoryImpl @Inject constructor(
    private val cambridgeService: CambridgeService,
    private val cambridgeParser: CambridgeHtmlParser,
    private val oedService: OedService,
    private val oedParser: OedHtmlParser
) : CloudWordExampleRepository {

    private val cache = LruCache<String, List<CloudWordExample>>(120)
    private val cacheMutex = Mutex()

    override suspend fun getExamples(
        word: String,
        source: CloudExampleSource,
        limit: Int
    ): List<CloudWordExample> {
        val normalized = normalize(word)
        if (normalized.isBlank()) return emptyList()
        val cacheKey = "${source.name}:$normalized:$limit"
        cacheMutex.withLock {
            cache[cacheKey]?.let { return it }
        }

        val result = when (source) {
            CloudExampleSource.CAMBRIDGE -> fetchFromCambridge(normalized, limit)
            CloudExampleSource.OED -> fetchFromOed(normalized, limit)
        }

        cacheMutex.withLock {
            cache[cacheKey] = result
        }
        return result
    }

    private suspend fun fetchFromCambridge(word: String, limit: Int): List<CloudWordExample> {
        val slug = buildEntrySlug(word)
        val html = cambridgeService.fetchEntryHtml(slug)
        val sourceUrl = "https://dictionary.cambridge.org/dictionary/english/$slug"
        return cambridgeParser.parseExamples(html, limit).map { sentence ->
            CloudWordExample(
                sentence = sentence,
                source = CloudExampleSource.CAMBRIDGE,
                sourceLabel = "Cambridge",
                sourceUrl = sourceUrl
            )
        }
    }

    private suspend fun fetchFromOed(word: String, limit: Int): List<CloudWordExample> {
        val searchHtml = oedService.fetchSearchHtml(word)
        val searchItems = oedParser.parseSearchResults(searchHtml)
        if (searchItems.isEmpty()) return emptyList()

        val output = mutableListOf<CloudWordExample>()
        for (item in searchItems.take(2)) {
            if (output.size >= limit) break
            val sourceUrl = toAbsoluteUrl(item.relativeUrl)
            val html = runCatching { oedService.fetchEntryHtml(item.relativeUrl) }.getOrNull() ?: continue
            val parsed = oedParser.parseExamples(html, limit = limit - output.size)
            if (parsed.isEmpty()) continue
            output += parsed.map { sentence ->
                CloudWordExample(
                    sentence = sentence,
                    source = CloudExampleSource.OED,
                    sourceLabel = "Oxford (OED)",
                    sourceUrl = sourceUrl
                )
            }
        }
        return output.distinctBy { it.sentence.lowercase() }.take(limit)
    }

    private fun normalize(word: String): String {
        return word.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private fun buildEntrySlug(word: String): String {
        val dash = word.replace(" ", "-")
        val encoded = URLEncoder.encode(dash, "UTF-8")
        return encoded.replace("+", "%20")
    }

    private fun toAbsoluteUrl(pathOrUrl: String): String {
        return if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl
        } else {
            "https://www.oed.com${pathOrUrl.trim()}"
        }
    }

    private class LruCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }
}
