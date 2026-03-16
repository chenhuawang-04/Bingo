package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.atlantic.AtlanticArticleDetail
import com.xty.englishhelper.data.remote.atlantic.AtlanticArticlePreview
import com.xty.englishhelper.data.remote.atlantic.AtlanticHtmlParser
import com.xty.englishhelper.data.remote.atlantic.AtlanticService
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleSourceTypeV2
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.AtlanticRepository
import com.xty.englishhelper.domain.usecase.article.ParseArticleUseCase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AtlanticRepositoryImpl @Inject constructor(
    private val service: AtlanticService,
    private val htmlParser: AtlanticHtmlParser,
    private val articleRepository: ArticleRepository,
    private val parseArticleUseCase: ParseArticleUseCase
) : AtlanticRepository {

    override suspend fun getSectionArticles(section: String): List<AtlanticArticlePreview> {
        val html = service.fetchSectionHtml(section)
        return htmlParser.parseSectionPage(html)
    }

    override suspend fun getArticleDetail(articleUrl: String): AtlanticArticleDetail {
        val html = service.fetchArticleHtml(articleUrl)
        return htmlParser.parseArticlePage(html, articleUrl)
    }

    override suspend fun createTemporaryArticle(detail: AtlanticArticleDetail): Long {
        val existing = articleRepository.getArticleBySourceUrl(detail.sourceUrl)
        if (existing != null) {
            return existing.id
        }

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
            domain = detail.sourceUrl,
            isSaved = false
        )

        val articleId = articleRepository.upsertArticle(article)

        val paragraphsWithId = detail.paragraphs.mapIndexed { index, p ->
            p.copy(articleId = articleId, paragraphIndex = index)
        }
        articleRepository.insertParagraphs(paragraphsWithId)

        return articleId
    }

    override suspend fun saveToLocal(articleId: Long): Long {
        articleRepository.markArticleSaved(articleId)
        parseArticleUseCase(articleId)
        return articleId
    }
}
