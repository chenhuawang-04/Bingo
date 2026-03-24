package com.xty.englishhelper.data.remote.csmonitor

interface CsMonitorService {
    suspend fun fetchSectionHtml(section: String): String
    suspend fun fetchArticleHtml(articleUrl: String): String
    suspend fun fetchRawUrl(url: String): String
}
