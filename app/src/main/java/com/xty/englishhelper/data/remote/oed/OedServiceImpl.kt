package com.xty.englishhelper.data.remote.oed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named

class OedServiceImpl @Inject constructor(
    @Named("oed") private val client: OkHttpClient
) : OedService {

    override suspend fun fetchSearchHtml(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.oed.com/search/dictionary/?scope=Entries&q=$encoded"
        return fetch(url)
    }

    override suspend fun fetchEntryHtml(pathOrUrl: String): String {
        val url = if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            pathOrUrl
        } else {
            "https://www.oed.com${pathOrUrl.trim()}"
        }
        return fetch(url)
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw OedFetchException("HTTP ${response.code}: ${response.message}")
        }
        response.body?.string() ?: throw OedFetchException("Empty response body")
    }
}

class OedFetchException(message: String) : Exception(message)
