package com.xty.englishhelper.data.remote.guardian

interface GuardianService {
    suspend fun fetchSectionHtml(section: String): String
    suspend fun fetchArticleHtml(articleUrl: String): String
}
