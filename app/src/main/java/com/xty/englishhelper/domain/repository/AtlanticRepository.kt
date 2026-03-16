package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.data.remote.atlantic.AtlanticArticleDetail
import com.xty.englishhelper.data.remote.atlantic.AtlanticArticlePreview

interface AtlanticRepository {
    suspend fun getSectionArticles(section: String): List<AtlanticArticlePreview>
    suspend fun getArticleDetail(articleUrl: String): AtlanticArticleDetail
    suspend fun createTemporaryArticle(detail: AtlanticArticleDetail): Long
    suspend fun saveToLocal(articleId: Long): Long
}
