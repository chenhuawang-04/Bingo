package com.xty.englishhelper.data.remote.atlantic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named

class AtlanticServiceImpl @Inject constructor(
    @Named("atlantic") private val client: OkHttpClient
) : AtlanticService {

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
            throw AtlanticFetchException("HTTP ${response.code}: ${response.message}")
        }
        response.body?.string() ?: throw AtlanticFetchException("Empty response body")
    }

    companion object {
        private const val BASE_URL = "https://www.theatlantic.com"
    }
}

class AtlanticFetchException(message: String) : Exception(message)
