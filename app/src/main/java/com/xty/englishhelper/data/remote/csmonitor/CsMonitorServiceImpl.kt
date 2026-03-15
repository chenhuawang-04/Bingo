package com.xty.englishhelper.data.remote.csmonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named

class CsMonitorServiceImpl @Inject constructor(
    @Named("csmonitor") private val client: OkHttpClient
) : CsMonitorService {

    override suspend fun fetchSectionHtml(section: String): String {
        val normalized = section.trim().trimStart('/')
        val url = if (normalized.isBlank()) {
            BASE_URL
        } else {
            "$BASE_URL/$normalized"
        }
        return fetchHtml(url)
    }

    override suspend fun fetchArticleHtml(articleUrl: String): String {
        return fetchHtml(articleUrl)
    }

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw CsMonitorFetchException("HTTP ${response.code}: ${response.message}")
        }
        response.body?.string() ?: throw CsMonitorFetchException("Empty response body")
    }

    companion object {
        private const val BASE_URL = "https://www.csmonitor.com"
    }
}

class CsMonitorFetchException(message: String) : Exception(message)
