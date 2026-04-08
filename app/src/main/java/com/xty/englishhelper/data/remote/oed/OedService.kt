package com.xty.englishhelper.data.remote.oed

interface OedService {
    suspend fun fetchSearchHtml(query: String): String
    suspend fun fetchEntryHtml(pathOrUrl: String): String
}
