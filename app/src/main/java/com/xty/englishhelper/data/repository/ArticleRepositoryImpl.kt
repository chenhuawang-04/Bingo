package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.ArticleDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.entity.ArticleEntity
import com.xty.englishhelper.data.local.entity.ArticleImageEntity
import com.xty.englishhelper.data.local.entity.ArticleSentenceEntity
import com.xty.englishhelper.data.local.entity.ArticleWordLinkEntity
import com.xty.englishhelper.data.local.entity.ArticleWordStatEntity
import com.xty.englishhelper.data.local.entity.SentenceAnalysisCacheEntity
import com.xty.englishhelper.data.local.entity.WordExampleEntity
import com.xty.englishhelper.data.mapper.parseInflections
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.ArticleWordStat
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.SentenceAnalysisCache
import com.xty.englishhelper.domain.repository.WordExample
import com.xty.englishhelper.domain.repository.WordMatchInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleRepositoryImpl @Inject constructor(
    private val articleDao: ArticleDao,
    private val wordDao: WordDao
) : ArticleRepository {

    override fun getAllArticles(): Flow<List<Article>> {
        return articleDao.getAllArticles().map { list -> list.map { it.toDomain() } }
    }

    override fun getArticleById(id: Long): Flow<Article?> {
        return articleDao.getArticleById(id).map { it?.toDomain() }
    }

    override suspend fun getArticleByIdOnce(id: Long): Article? {
        return articleDao.getArticleByIdOnce(id)?.toDomain()
    }

    override suspend fun upsertArticle(article: Article): Long {
        return articleDao.upsertArticle(article.toEntity())
    }

    override suspend fun updateParseStatus(articleId: Long, status: Int) {
        articleDao.updateParseStatus(articleId, status)
    }

    override suspend fun deleteArticle(articleId: Long) {
        articleDao.deleteArticle(articleId)
    }

    override fun getArticleCount(): Flow<Int> {
        return articleDao.getArticleCount()
    }

    override suspend fun getSentences(articleId: Long): List<ArticleSentence> {
        return articleDao.getSentences(articleId).map { it.toDomain() }
    }

    override suspend fun insertSentences(articleId: Long, sentences: List<ArticleSentence>) {
        articleDao.insertSentences(sentences.map { it.toEntity() })
    }

    override suspend fun deleteSentencesByArticle(articleId: Long) {
        articleDao.deleteSentencesByArticle(articleId)
    }

    override suspend fun upsertWordStats(articleId: Long, stats: List<ArticleWordStat>) {
        articleDao.upsertWordStats(stats.map { it.toEntity() })
    }

    override suspend fun getWordStats(articleId: Long): List<ArticleWordStat> {
        return articleDao.getWordStats(articleId).map { it.toDomain() }
    }

    override suspend fun deleteWordStatsByArticle(articleId: Long) {
        articleDao.deleteWordStatsByArticle(articleId)
    }

    override suspend fun upsertWordLinks(links: List<ArticleWordLink>) {
        articleDao.upsertWordLinks(links.map { it.toEntity() })
    }

    override suspend fun getWordLinks(articleId: Long): List<ArticleWordLink> {
        return articleDao.getWordLinks(articleId).map { it.toDomain() }
    }

    override suspend fun getWordLinksByWord(wordId: Long): List<ArticleWordLink> {
        return articleDao.getWordLinksByWord(wordId).map { it.toDomain() }
    }

    override suspend fun getAnalysisCache(articleId: Long, sentenceId: Long, hash: String): SentenceAnalysisCache? {
        return articleDao.getAnalysisCache(articleId, sentenceId, hash)?.let {
            SentenceAnalysisCache(
                meaningZh = it.meaningZh,
                grammarJson = it.grammarJson,
                keywordsJson = it.keywordsJson
            )
        }
    }

    override suspend fun insertAnalysisCache(articleId: Long, sentenceId: Long, hash: String, cache: SentenceAnalysisCache) {
        articleDao.insertAnalysisCache(
            SentenceAnalysisCacheEntity(
                articleId = articleId,
                sentenceId = sentenceId,
                sentenceHash = hash,
                meaningZh = cache.meaningZh,
                grammarJson = cache.grammarJson,
                keywordsJson = cache.keywordsJson
            )
        )
    }

    override suspend fun getExamplesForWord(wordId: Long): List<WordExample> {
        return articleDao.getExamplesForWord(wordId).map { it.toDomain() }
    }

    override suspend fun insertExamples(examples: List<WordExample>) {
        articleDao.insertExamples(examples.map { it.toEntity() })
    }

    override suspend fun deleteExamplesByArticle(articleId: Long) {
        articleDao.deleteExamplesByArticle(articleId)
    }

    override suspend fun getAllWordsForMatching(): List<WordMatchInfo> {
        return wordDao.getAllWordsForMatching().map { proj ->
            val inflections = parseInflections(proj.inflectionsJson).map { it.form }
            WordMatchInfo(
                wordId = proj.id,
                dictionaryId = proj.dictionaryId,
                normalizedSpelling = proj.normalizedSpelling,
                inflections = inflections
            )
        }
    }

    override suspend fun insertImages(articleId: Long, uris: List<String>) {
        val entities = uris.mapIndexed { index, uri ->
            ArticleImageEntity(articleId = articleId, localUri = uri, orderIndex = index)
        }
        articleDao.insertImages(entities)
    }

    override suspend fun getImages(articleId: Long): List<String> {
        return articleDao.getImages(articleId).map { it.localUri }
    }

    override suspend fun deleteImagesByArticle(articleId: Long) {
        articleDao.deleteImagesByArticle(articleId)
    }

    // Entity <-> Domain mapping
    private fun ArticleEntity.toDomain() = Article(
        id = id,
        title = title,
        content = content,
        domain = domain,
        difficultyAi = difficultyAi,
        difficultyLocal = difficultyLocal,
        difficultyFinal = difficultyFinal,
        sourceType = ArticleSourceType.entries.getOrElse(sourceType - 1) { ArticleSourceType.MANUAL },
        parseStatus = ArticleParseStatus.entries.getOrElse(parseStatus) { ArticleParseStatus.PENDING },
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Article.toEntity() = ArticleEntity(
        id = id,
        title = title,
        content = content,
        domain = domain,
        difficultyAi = difficultyAi,
        difficultyLocal = difficultyLocal,
        difficultyFinal = difficultyFinal,
        sourceType = sourceType.ordinal + 1,
        parseStatus = parseStatus.ordinal,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ArticleSentenceEntity.toDomain() = ArticleSentence(
        id = id,
        articleId = articleId,
        sentenceIndex = sentenceIndex,
        text = text,
        charStart = charStart,
        charEnd = charEnd
    )

    private fun ArticleSentence.toEntity() = ArticleSentenceEntity(
        id = id,
        articleId = articleId,
        sentenceIndex = sentenceIndex,
        text = text,
        charStart = charStart,
        charEnd = charEnd
    )

    private fun ArticleWordStatEntity.toDomain() = ArticleWordStat(
        id = id,
        articleId = articleId,
        normalizedToken = normalizedToken,
        displayToken = displayToken,
        frequency = frequency
    )

    private fun ArticleWordStat.toEntity() = ArticleWordStatEntity(
        id = id,
        articleId = articleId,
        normalizedToken = normalizedToken,
        displayToken = displayToken,
        frequency = frequency
    )

    private fun ArticleWordLinkEntity.toDomain() = ArticleWordLink(
        id = id,
        articleId = articleId,
        sentenceId = sentenceId,
        wordId = wordId,
        dictionaryId = dictionaryId,
        matchedToken = matchedToken
    )

    private fun ArticleWordLink.toEntity() = ArticleWordLinkEntity(
        id = id,
        articleId = articleId,
        sentenceId = sentenceId,
        wordId = wordId,
        dictionaryId = dictionaryId,
        matchedToken = matchedToken
    )

    private fun WordExampleEntity.toDomain() = WordExample(
        id = id,
        wordId = wordId,
        sentence = sentence,
        sourceType = sourceType,
        sourceArticleId = sourceArticleId,
        sourceSentenceId = sourceSentenceId,
        sourceLabel = sourceLabel,
        createdAt = createdAt
    )

    private fun WordExample.toEntity() = WordExampleEntity(
        id = id,
        wordId = wordId,
        sentence = sentence,
        sourceType = sourceType,
        sourceArticleId = sourceArticleId,
        sourceSentenceId = sourceSentenceId,
        sourceLabel = sourceLabel,
        createdAt = createdAt
    )
}
