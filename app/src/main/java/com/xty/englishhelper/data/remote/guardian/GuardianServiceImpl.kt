package com.xty.englishhelper.data.remote.guardian

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named

class GuardianServiceImpl @Inject constructor(
    @Named("guardian") private val client: OkHttpClient
) : GuardianService {

    override suspend fun fetchSectionHtml(section: String): String {
        return fetchHtml("https://www.theguardian.com/$section")
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
            throw GuardianFetchException("HTTP ${response.code}: ${response.message}")
        }
        response.body?.string() ?: throw GuardianFetchException("Empty response body")
    }
}

class GuardianFetchException(message: String) : Exception(message)
