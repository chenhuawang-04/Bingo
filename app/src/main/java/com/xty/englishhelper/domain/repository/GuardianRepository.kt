package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.data.remote.guardian.GuardianArticleDetail
import com.xty.englishhelper.data.remote.guardian.GuardianArticlePreview

interface GuardianRepository {
    suspend fun getSectionArticles(section: String): List<GuardianArticlePreview>
    suspend fun getArticleDetail(articleUrl: String): GuardianArticleDetail
    suspend fun createTemporaryArticle(detail: GuardianArticleDetail): Long
    suspend fun saveToLocal(articleId: Long): Long
    suspend fun cleanupUnsavedArticles()
}
