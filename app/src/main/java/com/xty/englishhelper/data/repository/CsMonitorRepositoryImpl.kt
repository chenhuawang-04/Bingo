package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.csmonitor.CsMonitorArticleDetail
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorArticlePreview
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorHtmlParser
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorService
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleSourceTypeV2
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.CsMonitorRepository
import com.xty.englishhelper.domain.usecase.article.ParseArticleUseCase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsMonitorRepositoryImpl @Inject constructor(
    private val service: CsMonitorService,
    private val htmlParser: CsMonitorHtmlParser,
    private val articleRepository: ArticleRepository,
    private val parseArticleUseCase: ParseArticleUseCase
) : CsMonitorRepository {

    override suspend fun getSectionArticles(section: String): List<CsMonitorArticlePreview> {
        val html = service.fetchSectionHtml(section)
        return htmlParser.parseSectionPage(html)
    }

    override suspend fun getArticleDetail(articleUrl: String): CsMonitorArticleDetail {
        val html = service.fetchArticleHtml(articleUrl)
        return htmlParser.parseArticlePage(html, articleUrl)
    }

    override suspend fun createTemporaryArticle(detail: CsMonitorArticleDetail): Long {
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
