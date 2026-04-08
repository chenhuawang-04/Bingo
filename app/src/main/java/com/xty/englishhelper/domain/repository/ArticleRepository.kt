package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.ArticleWordStat
import com.xty.englishhelper.domain.model.WordExampleSourceType
import com.xty.englishhelper.domain.model.QuickWordAnalysis
import kotlinx.coroutines.flow.Flow

data class WordExample(
    val id: Long = 0,
    val wordId: Long,
    val sentence: String,
    val sourceType: WordExampleSourceType = WordExampleSourceType.MANUAL,
    val sourceArticleId: Long? = null,
    val sourceSentenceId: Long? = null,
    val sourceLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class WordMatchInfo(
    val wordId: Long,
    val dictionaryId: Long,
    val normalizedSpelling: String,
    val inflections: List<String>
)

data class SentenceAnalysisCache(
    val meaningZh: String,
    val grammarJson: String,
    val keywordsJson: String
)

data class ParagraphAnalysisCacheData(
    val meaningZh: String,
    val grammarJson: String,
    val keywordsJson: String,
    val breakdownsJson: String
)

interface ArticleRepository {
    fun getAllArticles(): Flow<List<Article>>
    fun getArticlesByCategory(categoryId: Long): Flow<List<Article>>
    fun getArticleCategories(): Flow<List<ArticleCategory>>
    fun getArticleById(id: Long): Flow<Article?>
    suspend fun getArticleByIdOnce(id: Long): Article?
    suspend fun upsertArticle(article: Article): Long
    suspend fun updateParseStatus(articleId: Long, status: Int)
    suspend fun deleteArticle(articleId: Long)
    fun getArticleCount(): Flow<Int>

    suspend fun getSentences(articleId: Long): List<ArticleSentence>
    suspend fun insertSentences(articleId: Long, sentences: List<ArticleSentence>)
    suspend fun deleteSentencesByArticle(articleId: Long)
    suspend fun getSentencesByParagraph(paragraphId: Long): List<ArticleSentence>

    // Paragraphs
    suspend fun getParagraphs(articleId: Long): List<ArticleParagraph>
    suspend fun insertParagraphs(paragraphs: List<ArticleParagraph>)
    suspend fun deleteParagraphsByArticle(articleId: Long)

    suspend fun upsertWordStats(articleId: Long, stats: List<ArticleWordStat>)
    suspend fun getWordStats(articleId: Long): List<ArticleWordStat>
    suspend fun deleteWordStatsByArticle(articleId: Long)
    suspend fun getArticleIdsByTokens(tokens: List<String>): List<Long>

    suspend fun upsertWordLinks(links: List<ArticleWordLink>)
    suspend fun getWordLinks(articleId: Long): List<ArticleWordLink>
    suspend fun getWordLinksByWord(wordId: Long): List<ArticleWordLink>

    suspend fun getAnalysisCache(articleId: Long, sentenceId: Long, hash: String, modelKey: String): SentenceAnalysisCache?
    suspend fun insertAnalysisCache(articleId: Long, sentenceId: Long, hash: String, modelKey: String, cache: SentenceAnalysisCache)
    suspend fun getParagraphAnalysisCache(articleId: Long, paragraphId: Long, hash: String, modelKey: String): ParagraphAnalysisCacheData?
    suspend fun insertParagraphAnalysisCache(articleId: Long, paragraphId: Long, hash: String, modelKey: String, cache: ParagraphAnalysisCacheData)

    // In-memory cache for unsaved articles
    suspend fun getMemoryParagraphAnalysisCache(cacheKey: String): ParagraphAnalysisCacheData?
    suspend fun putMemoryParagraphAnalysisCache(cacheKey: String, cache: ParagraphAnalysisCacheData)

    suspend fun getMemoryQuickWordAnalysisCache(cacheKey: String): QuickWordAnalysis?
    suspend fun putMemoryQuickWordAnalysisCache(cacheKey: String, analysis: QuickWordAnalysis)
    suspend fun getExamplesForWord(wordId: Long): List<WordExample>
    suspend fun insertExamples(examples: List<WordExample>)
    suspend fun deleteExamplesByArticle(articleId: Long)
    suspend fun deleteWordLinksByWord(wordId: Long)
    suspend fun deleteExamplesByWord(wordId: Long)

    suspend fun getAllWordsForMatching(): List<WordMatchInfo>

    suspend fun insertImages(articleId: Long, uris: List<String>)
    suspend fun getImages(articleId: Long): List<String>
    suspend fun deleteImagesByArticle(articleId: Long)

    suspend fun updateWordCount(articleId: Long, wordCount: Int)
    suspend fun updateArticleCategory(articleId: Long, categoryId: Long)

    suspend fun createCategory(name: String, isSystem: Boolean = false): Long
    suspend fun renameCategory(id: Long, name: String)
    suspend fun deleteCategory(id: Long)
    suspend fun replaceCategories(categories: List<ArticleCategory>)
    suspend fun ensureDefaultCategories()

    // Guardian support
    suspend fun getArticleBySourceUrl(sourceUrl: String): Article?
    suspend fun markArticleSaved(articleId: Long)
    suspend fun deleteUnsavedArticlesBefore(cutoff: Long)
    fun getSavedArticles(): Flow<List<Article>>
    fun getTopScoredOnlineArticles(limit: Int): Flow<List<Article>>

    suspend fun updateSuitabilityById(
        articleId: Long,
        score: Int?,
        reason: String?,
        evaluatedAt: Long?,
        modelKey: String?
    )

    suspend fun updateSuitabilityBySourceUrl(
        sourceUrl: String,
        score: Int?,
        reason: String?,
        evaluatedAt: Long?,
        modelKey: String?
    ): Int
}


