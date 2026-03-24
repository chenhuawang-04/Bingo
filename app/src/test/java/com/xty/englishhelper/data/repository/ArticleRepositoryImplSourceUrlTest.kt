package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.ArticleCategoryDao
import com.xty.englishhelper.data.local.dao.ArticleDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.entity.ArticleEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ArticleRepositoryImplSourceUrlTest {

    private lateinit var articleDao: ArticleDao
    private lateinit var articleCategoryDao: ArticleCategoryDao
    private lateinit var wordDao: WordDao
    private lateinit var repository: ArticleRepositoryImpl

    @Before
    fun setUp() {
        articleDao = mockk(relaxed = true)
        articleCategoryDao = mockk(relaxed = true)
        wordDao = mockk(relaxed = true)
        repository = ArticleRepositoryImpl(articleDao, articleCategoryDao, wordDao)
    }

    @Test
    fun `getArticleBySourceUrl prefers saved canonical article over unsaved variant`() = runTest {
        val requestedUrl = "https://www.csmonitor.com/World/2026/0324/Test-story?utm_source=test"
        val canonicalUrl = "https://www.csmonitor.com/World/2026/0324/Test-story"

        coEvery { articleDao.getArticlesBySourceUrls(any()) } returns listOf(
            articleEntity(id = 3, domain = requestedUrl, isSaved = 0, updatedAt = 100L),
            articleEntity(id = 7, domain = canonicalUrl, isSaved = 1, updatedAt = 50L)
        )

        val article = repository.getArticleBySourceUrl(requestedUrl)

        assertEquals(7L, article?.id)
        assertEquals(canonicalUrl, article?.domain)
        coVerify(exactly = 1) {
            articleDao.getArticlesBySourceUrls(
                match { candidates ->
                    candidates.contains(requestedUrl) &&
                        candidates.contains(canonicalUrl) &&
                        candidates.contains("$canonicalUrl/")
                }
            )
        }
    }

    @Test
    fun `updateSuitabilityBySourceUrl updates matched legacy variant and rewrites domain`() = runTest {
        val canonicalUrl = "https://www.csmonitor.com/World/2026/0324/Test-story"
        val legacyUrl = "$canonicalUrl/"

        coEvery { articleDao.getArticlesBySourceUrls(any()) } returns listOf(
            articleEntity(id = 11, domain = legacyUrl, isSaved = 1)
        )
        coEvery { articleDao.updateSuitabilityById(any(), any(), any(), any(), any(), any()) } returns Unit
        coEvery { articleDao.updateSourceUrlById(any(), any(), any()) } returns Unit

        val updated = repository.updateSuitabilityBySourceUrl(
            sourceUrl = canonicalUrl,
            score = 92,
            reason = "Good fit",
            evaluatedAt = 1234L,
            modelKey = "fast-model"
        )

        assertEquals(1, updated)
        coVerify(exactly = 1) {
            articleDao.updateSuitabilityById(11, 92, "Good fit", 1234L, "fast-model", any())
        }
        coVerify(exactly = 1) {
            articleDao.updateSourceUrlById(11, canonicalUrl, any())
        }
    }

    private fun articleEntity(
        id: Long,
        domain: String,
        isSaved: Int,
        updatedAt: Long = 0L
    ): ArticleEntity {
        return ArticleEntity(
            id = id,
            articleUid = "uid-$id",
            title = "Title $id",
            content = "Body $id",
            domain = domain,
            updatedAt = updatedAt,
            isSaved = isSaved,
            sourceTypeV2 = "ONLINE"
        )
    }
}
