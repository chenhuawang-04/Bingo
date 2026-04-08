package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.oed.OedHtmlParser
import com.xty.englishhelper.data.remote.oed.OedService
import com.xty.englishhelper.domain.model.QuickDictionaryEntry
import com.xty.englishhelper.domain.model.QuickDictionarySource
import com.xty.englishhelper.domain.repository.OedDictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OedDictionaryRepositoryImpl @Inject constructor(
    private val service: OedService,
    private val parser: OedHtmlParser
) : OedDictionaryRepository {

    override suspend fun lookupWord(word: String): List<QuickDictionaryEntry> {
        val query = word.trim()
        if (query.isBlank()) return emptyList()

        val searchHtml = service.fetchSearchHtml(query)
        val searchItems = parser.parseSearchResults(searchHtml)
        if (searchItems.isEmpty()) return emptyList()

        return searchItems.take(3).mapNotNull { item ->
            runCatching {
                val entryHtml = service.fetchEntryHtml(item.relativeUrl)
                val detail = parser.parseEntryDetail(entryHtml, item.headword.ifBlank { query })
                QuickDictionaryEntry(
                    source = QuickDictionarySource.OED,
                    sourceLabel = "OED",
                    headword = detail.headword.ifBlank { item.headword.ifBlank { query } },
                    variant = detail.variant.ifBlank { item.variant },
                    pronunciation = detail.pronunciation,
                    timeRange = item.dateRange.takeIf { it.isNotBlank() },
                    tags = detail.tags,
                    etymologySummary = detail.etymologySummary,
                    summary = detail.senses.firstOrNull().orEmpty().ifBlank { item.snippet },
                    senses = detail.senses.ifEmpty { listOfNotNull(item.snippet.takeIf { it.isNotBlank() }) },
                    sourceUrl = toAbsoluteUrl(item.relativeUrl)
                )
            }.getOrNull()
        }
    }

    private fun toAbsoluteUrl(pathOrUrl: String): String {
        return if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl
        } else {
            "https://www.oed.com${pathOrUrl.trim()}"
        }
    }
}
