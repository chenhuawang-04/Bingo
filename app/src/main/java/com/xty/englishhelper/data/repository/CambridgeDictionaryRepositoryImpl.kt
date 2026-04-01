package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.cambridge.CambridgeHtmlParser
import com.xty.englishhelper.data.remote.cambridge.CambridgeService
import com.xty.englishhelper.domain.model.CambridgeEntry
import com.xty.englishhelper.domain.repository.CambridgeDictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CambridgeDictionaryRepositoryImpl @Inject constructor(
    private val service: CambridgeService,
    private val htmlParser: CambridgeHtmlParser
) : CambridgeDictionaryRepository {

    private val suggestionCache = LruCache<String, List<String>>(50)
    private val entryCache = LruCache<String, CambridgeEntry>(50)

    override suspend fun searchSuggestions(query: String): List<String> {
        val normalized = normalizeQuery(query)
        if (normalized.isBlank()) return emptyList()
        suggestionCache[normalized]?.let { return it }

        return withContext(Dispatchers.IO) {
            val json = service.fetchAutocompleteJson(normalized)
            val suggestions = parseSuggestions(json)
            suggestionCache[normalized] = suggestions
            suggestions
        }
    }

    override suspend fun fetchEntry(word: String): CambridgeEntry {
        val normalized = normalizeQuery(word)
        if (normalized.isBlank()) {
            throw IllegalArgumentException("Empty word")
        }
        entryCache[normalized]?.let { return it }

        val slug = buildEntrySlug(normalized)
        val html = service.fetchEntryHtml(slug)
        val url = "https://dictionary.cambridge.org/dictionary/english-chinese-simplified/$slug"
        val entry = htmlParser.parseEntry(html, normalized, url)
        entryCache[normalized] = entry
        return entry
    }

    private fun normalizeQuery(query: String): String {
        return query.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private fun buildEntrySlug(word: String): String {
        val dash = word.replace(" ", "-")
        val encoded = URLEncoder.encode(dash, "UTF-8")
        return encoded.replace("+", "%20")
    }

    private fun parseSuggestions(json: String): List<String> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val terms = root.optJSONArray("terms")
            ?: root.optJSONArray("results")
            ?: root.optJSONArray("entries")
            ?: root.optJSONArray("suggestions")

        val suggestions = mutableListOf<String>()
        if (terms != null) {
            for (i in 0 until terms.length()) {
                val item = terms.opt(i)
                when (item) {
                    is JSONObject -> extractSuggestion(item)?.let { suggestions.add(it) }
                    is String -> if (item.isNotBlank()) suggestions.add(item)
                }
            }
        } else {
            root.optJSONArray("items")?.let { arr ->
                suggestions.addAll(extractStringArray(arr))
            }
        }

        return suggestions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
    }

    private fun extractSuggestion(obj: JSONObject): String? {
        val keys = listOf("value", "search", "word", "term", "text")
        for (key in keys) {
            obj.optString(key)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun extractStringArray(arr: JSONArray): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i)
            if (item.isNotBlank()) out.add(item)
        }
        return out
    }

    private class LruCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }
}
