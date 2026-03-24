package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.guardian.GuardianArticleDetail
import com.xty.englishhelper.data.remote.guardian.GuardianArticlePreview
import com.xty.englishhelper.data.remote.guardian.GuardianHtmlParser
import com.xty.englishhelper.data.remote.guardian.GuardianService
import com.xty.englishhelper.domain.article.OnlineArticleSourceUrl
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleSourceTypeV2
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.GuardianRepository
import com.xty.englishhelper.domain.usecase.article.ParseArticleUseCase
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuardianRepositoryImpl @Inject constructor(
    private val guardianService: GuardianService,
    private val htmlParser: GuardianHtmlParser,
    private val articleRepository: ArticleRepository,
    private val parseArticleUseCase: ParseArticleUseCase
) : GuardianRepository {

    override suspend fun getSectionArticles(section: String): List<GuardianArticlePreview> {
        val html = guardianService.fetchSectionHtml(section)
        return htmlParser.parseSectionPage(html)
    }

    override suspend fun getArticleDetail(articleUrl: String): GuardianArticleDetail {
        val html = guardianService.fetchArticleHtml(articleUrl)
        return htmlParser.parseArticlePage(html, articleUrl)
    }

    override suspend fun createTemporaryArticle(detail: GuardianArticleDetail): Long {
        val normalizedSourceUrl = OnlineArticleSourceUrl.normalize(detail.sourceUrl).ifBlank { detail.sourceUrl }
        // Check if article already exists by source URL
        val existing = articleRepository.getArticleBySourceUrl(normalizedSourceUrl)
        if (existing != null) {
            val needsUpdate = existing.content.isBlank() || existing.title != detail.title
            if (needsUpdate) {
                val updated = existing.copy(
                    title = detail.title,
                    content = detail.paragraphs.joinToString("\n\n") { it.text },
                    sourceType = ArticleSourceType.MANUAL,
                    sourceTypeV2 = ArticleSourceTypeV2.ONLINE,
                    parseStatus = ArticleParseStatus.DONE,
                    summary = detail.summary,
                    author = detail.author,
                    source = detail.source,
                    coverImageUrl = detail.coverImageUrl,
                    domain = normalizedSourceUrl,
                    wordCount = existing.wordCount
                )
                val articleId = articleRepository.upsertArticle(updated)
                articleRepository.deleteParagraphsByArticle(articleId)
                val paragraphsWithId = detail.paragraphs.mapIndexed { index, p ->
                    p.copy(articleId = articleId, paragraphIndex = index)
                }
                articleRepository.insertParagraphs(paragraphsWithId)
                return articleId
            }
            return existing.id
        }

        // Create new temporary article (isSaved=false)
        val article = Article(
            title = detail.title,
            content = detail.paragraphs.joinToString("\n\n") { it.text },
            articleUid = UUID.randomUUID().toString(),
            sourceType = ArticleSourceType.MANUAL,
            sourceTypeV2 = ArticleSourceTypeV2.ONLINE,
            parseStatus = ArticleParseStatus.DONE,
            summary = detail.summary,
            author = detail.author,
            source = detail.source,
            coverImageUrl = detail.coverImageUrl,
            domain = normalizedSourceUrl,
            isSaved = false
        )

        val articleId = articleRepository.upsertArticle(article)

        // Store paragraphs
        val paragraphsWithId = detail.paragraphs.mapIndexed { index, p ->
            p.copy(articleId = articleId, paragraphIndex = index)
        }
        articleRepository.insertParagraphs(paragraphsWithId)

        return articleId
    }

    override suspend fun saveToLocal(articleId: Long): Long {
        // Mark as saved
        articleRepository.markArticleSaved(articleId)

        // Trigger full parse (sentence splitting, word links, examples)
        parseArticleUseCase(articleId)

        return articleId
    }

    override suspend fun cleanupUnsavedArticles() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        articleRepository.deleteUnsavedArticlesBefore(cutoff)
    }
}
