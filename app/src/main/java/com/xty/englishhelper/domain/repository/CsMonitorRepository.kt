package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.data.remote.csmonitor.CsMonitorArticleDetail
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorArticlePreview

interface CsMonitorRepository {
    suspend fun getSectionArticles(section: String): List<CsMonitorArticlePreview>
    suspend fun getArticleDetail(articleUrl: String): CsMonitorArticleDetail
    suspend fun createTemporaryArticle(detail: CsMonitorArticleDetail): Long
    suspend fun saveToLocal(articleId: Long): Long
}
