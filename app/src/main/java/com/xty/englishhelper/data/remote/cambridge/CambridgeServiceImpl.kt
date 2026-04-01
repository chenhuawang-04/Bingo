package com.xty.englishhelper.data.remote.cambridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named

class CambridgeServiceImpl @Inject constructor(
    @Named("cambridge") private val client: OkHttpClient
) : CambridgeService {

    override suspend fun fetchAutocompleteJson(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://dictionary.cambridge.org/autocomplete/amp?dataset=english-chinese-simplified&q=$encoded&__amp_source_origin=https%3A%2F%2Fdictionary.cambridge.org"
        return fetch(url)
    }

    override suspend fun fetchEntryHtml(word: String): String {
        val url = "https://dictionary.cambridge.org/dictionary/english-chinese-simplified/$word"
        return fetch(url)
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw CambridgeFetchException("HTTP ${response.code}: ${response.message}")
        }
        response.body?.string() ?: throw CambridgeFetchException("Empty response body")
    }
}

class CambridgeFetchException(message: String) : Exception(message)
