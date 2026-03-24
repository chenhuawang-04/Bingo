package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.remote.csmonitor.CsMonitorArticleDetail
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorArticlePreview
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorHtmlParser
import com.xty.englishhelper.data.remote.csmonitor.CsMonitorService
import com.xty.englishhelper.domain.article.OnlineArticleSourceUrl
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParagraph
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
        return htmlParser.parseArticlePage(html, articleUrl) { rssUrl ->
            runCatching { service.fetchRawUrl(rssUrl) }.getOrNull()
        }
    }

    override suspend fun createTemporaryArticle(detail: CsMonitorArticleDetail): Long {
        val canonicalSourceUrl = OnlineArticleSourceUrl.normalize(detail.sourceUrl).ifBlank { detail.sourceUrl.trim() }
        val requestedSourceUrl = detail.requestedUrl.trim().takeIf { it.isNotBlank() }
        val articleContent = buildArticleContent(detail.paragraphs)
        val wordCount = countWords(detail.paragraphs)

        val existing = findExistingArticle(canonicalSourceUrl, requestedSourceUrl)
        if (existing != null) {
            val existingParagraphs = articleRepository.getParagraphs(existing.id)
            val paragraphsChanged = !sameParagraphs(existingParagraphs, detail.paragraphs)
            val contentChanged = existing.content != articleContent
            val metadataChanged = existing.title != detail.title ||
                existing.summary != detail.summary ||
                existing.author != detail.author ||
                existing.source != detail.source ||
                existing.coverImageUrl != detail.coverImageUrl ||
                existing.domain != canonicalSourceUrl ||
                existing.wordCount != wordCount

            if (!paragraphsChanged && !contentChanged && !metadataChanged) {
                return existing.id
            }

            val articleId = articleRepository.upsertArticle(
                existing.copy(
                    title = detail.title,
                    content = articleContent,
                    sourceType = ArticleSourceType.MANUAL,
                    sourceTypeV2 = ArticleSourceTypeV2.ONLINE,
                    parseStatus = ArticleParseStatus.DONE,
                    updatedAt = System.currentTimeMillis(),
                    summary = detail.summary,
                    author = detail.author,
                    source = detail.source,
                    coverImageUrl = detail.coverImageUrl,
                    domain = canonicalSourceUrl,
                    wordCount = wordCount
                )
            )

            if (paragraphsChanged || contentChanged) {
                replaceParagraphs(articleId, detail.paragraphs)
                if (existing.isSaved) {
                    parseArticleUseCase(articleId)
                }
            }
            return articleId
        }

        val article = Article(
            title = detail.title,
            content = articleContent,
            articleUid = UUID.randomUUID().toString(),
            sourceType = ArticleSourceType.MANUAL,
            sourceTypeV2 = ArticleSourceTypeV2.ONLINE,
            parseStatus = ArticleParseStatus.DONE,
            summary = detail.summary,
            author = detail.author,
            source = detail.source,
            coverImageUrl = detail.coverImageUrl,
            domain = canonicalSourceUrl,
            wordCount = wordCount,
            isSaved = false
        )

        val articleId = articleRepository.upsertArticle(article)
        replaceParagraphs(articleId, detail.paragraphs)
        return articleId
    }

    override suspend fun saveToLocal(articleId: Long): Long {
        articleRepository.markArticleSaved(articleId)
        parseArticleUseCase(articleId)
        return articleId
    }

    private suspend fun findExistingArticle(
        canonicalSourceUrl: String,
        requestedSourceUrl: String?
    ): Article? {
        articleRepository.getArticleBySourceUrl(canonicalSourceUrl)?.let { return it }
        if (!requestedSourceUrl.isNullOrBlank() && requestedSourceUrl != canonicalSourceUrl) {
            articleRepository.getArticleBySourceUrl(requestedSourceUrl)?.let { return it }
        }
        return null
    }

    private suspend fun replaceParagraphs(articleId: Long, paragraphs: List<ArticleParagraph>) {
        articleRepository.deleteParagraphsByArticle(articleId)
        val paragraphsWithId = paragraphs.mapIndexed { index, paragraph ->
            paragraph.copy(articleId = articleId, paragraphIndex = index)
        }
        articleRepository.insertParagraphs(paragraphsWithId)
    }

    private fun sameParagraphs(
        existing: List<ArticleParagraph>,
        incoming: List<ArticleParagraph>
    ): Boolean {
        if (existing.size != incoming.size) return false
        return existing.zip(incoming).all { (left, right) ->
            left.text == right.text &&
                left.imageUrl == right.imageUrl &&
                left.paragraphType == right.paragraphType
        }
    }

    private fun buildArticleContent(paragraphs: List<ArticleParagraph>): String {
        return paragraphs.joinToString("\n\n") { it.text }
    }

    private fun countWords(paragraphs: List<ArticleParagraph>): Int {
        return paragraphs
            .asSequence()
            .flatMap { paragraph -> paragraph.text.split(Regex("\\s+")).asSequence() }
            .count { it.isNotBlank() }
    }
}
