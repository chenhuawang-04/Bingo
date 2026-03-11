package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.ArticleDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.entity.ArticleEntity
import com.xty.englishhelper.data.local.entity.ArticleImageEntity
import com.xty.englishhelper.data.local.entity.ArticleParagraphEntity
import com.xty.englishhelper.data.local.entity.ArticleSentenceEntity
import com.xty.englishhelper.data.local.entity.ArticleWordLinkEntity
import com.xty.englishhelper.data.local.entity.ArticleWordStatEntity
import com.xty.englishhelper.data.local.entity.ParagraphAnalysisCacheEntity
import com.xty.englishhelper.data.local.entity.SentenceAnalysisCacheEntity
import com.xty.englishhelper.data.local.entity.WordExampleEntity
import com.xty.englishhelper.data.mapper.parseInflections
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleSourceTypeV2
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.ArticleWordStat
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.domain.model.WordExampleSourceType
import com.xty.englishhelper.domain.model.QuickWordAnalysis
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.ParagraphAnalysisCacheData
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

    private val memoryParagraphCache = java.util.concurrent.ConcurrentHashMap<String, ParagraphAnalysisCacheData>()
    private val memoryQuickWordCache = java.util.concurrent.ConcurrentHashMap<String, QuickWordAnalysis>()

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
        articleDao.deleteArticleCascade(articleId)
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

    override suspend fun getSentencesByParagraph(paragraphId: Long): List<ArticleSentence> {
        return articleDao.getSentencesByParagraph(paragraphId).map { it.toDomain() }
    }

    // Paragraphs
    override suspend fun getParagraphs(articleId: Long): List<ArticleParagraph> {
        return articleDao.getParagraphs(articleId).map { it.toDomain() }
    }

    override suspend fun insertParagraphs(paragraphs: List<ArticleParagraph>) {
        articleDao.insertParagraphs(paragraphs.map { it.toEntity() })
    }

    override suspend fun deleteParagraphsByArticle(articleId: Long) {
        articleDao.deleteParagraphsByArticle(articleId)
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

    override suspend fun getArticleIdsByTokens(tokens: List<String>): List<Long> {
        return articleDao.getArticleIdsByTokens(tokens)
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

    override suspend fun getAnalysisCache(articleId: Long, sentenceId: Long, hash: String, modelKey: String): SentenceAnalysisCache? {
        return articleDao.getAnalysisCache(articleId, sentenceId, hash, modelKey)?.let {
            SentenceAnalysisCache(
                meaningZh = it.meaningZh,
                grammarJson = it.grammarJson,
                keywordsJson = it.keywordsJson
            )
        }
    }

    override suspend fun insertAnalysisCache(articleId: Long, sentenceId: Long, hash: String, modelKey: String, cache: SentenceAnalysisCache) {
        articleDao.insertAnalysisCache(
            SentenceAnalysisCacheEntity(
                articleId = articleId,
                sentenceId = sentenceId,
                sentenceHash = hash,
                modelKey = modelKey,
                meaningZh = cache.meaningZh,
                grammarJson = cache.grammarJson,
                keywordsJson = cache.keywordsJson
            )
        )
    }

    override suspend fun getParagraphAnalysisCache(articleId: Long, paragraphId: Long, hash: String, modelKey: String): ParagraphAnalysisCacheData? {
        return articleDao.getParagraphAnalysisCache(articleId, paragraphId, hash, modelKey)?.let {
            ParagraphAnalysisCacheData(
                meaningZh = it.meaningZh,
                grammarJson = it.grammarJson,
                keywordsJson = it.keywordsJson,
                breakdownsJson = it.breakdownsJson
            )
        }
    }

    override suspend fun insertParagraphAnalysisCache(articleId: Long, paragraphId: Long, hash: String, modelKey: String, cache: ParagraphAnalysisCacheData) {
        articleDao.insertParagraphAnalysisCache(
            ParagraphAnalysisCacheEntity(
                articleId = articleId,
                paragraphId = paragraphId,
                paragraphHash = hash,
                modelKey = modelKey,
                meaningZh = cache.meaningZh,
                grammarJson = cache.grammarJson,
                keywordsJson = cache.keywordsJson,
                breakdownsJson = cache.breakdownsJson
            )
        )
    }

    override suspend fun getMemoryParagraphAnalysisCache(cacheKey: String): ParagraphAnalysisCacheData? {
        return memoryParagraphCache[cacheKey]
    }

    override suspend fun putMemoryParagraphAnalysisCache(cacheKey: String, cache: ParagraphAnalysisCacheData) {
        memoryParagraphCache[cacheKey] = cache
    }

    override suspend fun getMemoryQuickWordAnalysisCache(cacheKey: String): QuickWordAnalysis? {
        return memoryQuickWordCache[cacheKey]
    }

    override suspend fun putMemoryQuickWordAnalysisCache(cacheKey: String, analysis: QuickWordAnalysis) {
        memoryQuickWordCache[cacheKey] = analysis
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

    override suspend fun deleteWordLinksByWord(wordId: Long) =
        articleDao.deleteWordLinksByWord(wordId)

    override suspend fun deleteExamplesByWord(wordId: Long) =
        articleDao.deleteExamplesByWord(wordId)

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

    override suspend fun updateWordCount(articleId: Long, wordCount: Int) {
        articleDao.updateWordCount(articleId, wordCount)
    }

    override suspend fun getArticleBySourceUrl(sourceUrl: String): Article? {
        return articleDao.getArticleBySourceUrl(sourceUrl)?.toDomain()
    }

    override suspend fun markArticleSaved(articleId: Long) {
        articleDao.markArticleSaved(articleId)
    }

    override suspend fun deleteUnsavedArticlesBefore(cutoff: Long) {
        articleDao.deleteUnsavedArticlesBefore(cutoff)
    }

    override fun getSavedArticles(): Flow<List<Article>> {
        return articleDao.getSavedArticles().map { list -> list.map { it.toDomain() } }
    }

    // Entity <-> Domain mapping
    private fun ArticleEntity.toDomain() = Article(
        id = id,
        articleUid = articleUid,
        title = title,
        content = content,
        domain = domain,
        difficultyAi = difficultyAi,
        difficultyLocal = difficultyLocal,
        difficultyFinal = difficultyFinal,
        sourceType = ArticleSourceType.entries.getOrElse(sourceType - 1) { ArticleSourceType.MANUAL },
        parseStatus = ArticleParseStatus.entries.getOrElse(parseStatus) { ArticleParseStatus.PENDING },
        createdAt = createdAt,
        updatedAt = updatedAt,
        summary = summary,
        author = author,
        source = source,
        coverImageUri = coverImageUri,
        coverImageUrl = coverImageUrl,
        wordCount = wordCount,
        isSaved = isSaved == 1,
        sourceTypeV2 = runCatching { ArticleSourceTypeV2.valueOf(sourceTypeV2) }.getOrDefault(ArticleSourceTypeV2.LOCAL)
    )

    private fun Article.toEntity() = ArticleEntity(
        id = id,
        articleUid = articleUid,
        title = title,
        content = content,
        domain = domain,
        difficultyAi = difficultyAi,
        difficultyLocal = difficultyLocal,
        difficultyFinal = difficultyFinal,
        sourceType = sourceType.ordinal + 1,
        parseStatus = parseStatus.ordinal,
        createdAt = createdAt,
        updatedAt = updatedAt,
        summary = summary,
        author = author,
        source = source,
        coverImageUri = coverImageUri,
        coverImageUrl = coverImageUrl,
        wordCount = wordCount,
        isSaved = if (isSaved) 1 else 0,
        sourceTypeV2 = sourceTypeV2.name
    )

    private fun ArticleSentenceEntity.toDomain() = ArticleSentence(
        id = id,
        articleId = articleId,
        sentenceIndex = sentenceIndex,
        text = text,
        charStart = charStart,
        charEnd = charEnd,
        paragraphId = paragraphId
    )

    private fun ArticleSentence.toEntity() = ArticleSentenceEntity(
        id = id,
        articleId = articleId,
        sentenceIndex = sentenceIndex,
        text = text,
        charStart = charStart,
        charEnd = charEnd,
        paragraphId = paragraphId
    )

    private fun ArticleParagraphEntity.toDomain() = ArticleParagraph(
        id = id,
        articleId = articleId,
        paragraphIndex = paragraphIndex,
        text = text,
        imageUri = imageUri,
        imageUrl = imageUrl,
        paragraphType = runCatching { ParagraphType.valueOf(paragraphType) }.getOrDefault(ParagraphType.TEXT)
    )

    private fun ArticleParagraph.toEntity() = ArticleParagraphEntity(
        id = id,
        articleId = articleId,
        paragraphIndex = paragraphIndex,
        text = text,
        imageUri = imageUri,
        imageUrl = imageUrl,
        paragraphType = paragraphType.name
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
        sourceType = WordExampleSourceType.fromValue(sourceType),
        sourceArticleId = sourceArticleId,
        sourceSentenceId = sourceSentenceId,
        sourceLabel = sourceLabel,
        createdAt = createdAt
    )

    private fun WordExample.toEntity() = WordExampleEntity(
        id = id,
        wordId = wordId,
        sentence = sentence,
        sourceType = sourceType.value,
        sourceArticleId = sourceArticleId,
        sourceSentenceId = sourceSentenceId,
        sourceLabel = sourceLabel,
        createdAt = createdAt
    )
}
