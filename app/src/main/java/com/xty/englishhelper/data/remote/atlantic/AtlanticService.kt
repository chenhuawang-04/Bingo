package com.xty.englishhelper.data.remote.atlantic

interface AtlanticService {
    suspend fun fetchSectionHtml(section: String): String
    suspend fun fetchArticleHtml(articleUrl: String): String
}
