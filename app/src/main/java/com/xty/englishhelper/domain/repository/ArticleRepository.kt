package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.ArticleWordStat
import kotlinx.coroutines.flow.Flow

data class WordExample(
    val id: Long = 0,
    val wordId: Long,
    val sentence: String,
    val sourceType: Int = 0,
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

interface ArticleRepository {
    fun getAllArticles(): Flow<List<Article>>
    fun getArticleById(id: Long): Flow<Article?>
    suspend fun getArticleByIdOnce(id: Long): Article?
    suspend fun upsertArticle(article: Article): Long
    suspend fun updateParseStatus(articleId: Long, status: Int)
    suspend fun deleteArticle(articleId: Long)
    fun getArticleCount(): Flow<Int>

    suspend fun getSentences(articleId: Long): List<ArticleSentence>
    suspend fun insertSentences(articleId: Long, sentences: List<ArticleSentence>)
    suspend fun deleteSentencesByArticle(articleId: Long)

    suspend fun upsertWordStats(articleId: Long, stats: List<ArticleWordStat>)
    suspend fun getWordStats(articleId: Long): List<ArticleWordStat>
    suspend fun deleteWordStatsByArticle(articleId: Long)
    suspend fun getArticleIdsByTokens(tokens: List<String>): List<Long>

    suspend fun upsertWordLinks(links: List<ArticleWordLink>)
    suspend fun getWordLinks(articleId: Long): List<ArticleWordLink>
    suspend fun getWordLinksByWord(wordId: Long): List<ArticleWordLink>

    suspend fun getAnalysisCache(articleId: Long, sentenceId: Long, hash: String): SentenceAnalysisCache?
    suspend fun insertAnalysisCache(articleId: Long, sentenceId: Long, hash: String, cache: SentenceAnalysisCache)

    suspend fun getExamplesForWord(wordId: Long): List<WordExample>
    suspend fun insertExamples(examples: List<WordExample>)
    suspend fun deleteExamplesByArticle(articleId: Long)
    suspend fun deleteWordLinksByWord(wordId: Long)
    suspend fun deleteExamplesByWord(wordId: Long)

    suspend fun getAllWordsForMatching(): List<WordMatchInfo>

    suspend fun insertImages(articleId: Long, uris: List<String>)
    suspend fun getImages(articleId: Long): List<String>
    suspend fun deleteImagesByArticle(articleId: Long)
}
