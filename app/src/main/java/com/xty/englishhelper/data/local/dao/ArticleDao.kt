package com.xty.englishhelper.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.xty.englishhelper.data.local.entity.ArticleEntity
import com.xty.englishhelper.data.local.entity.ArticleImageEntity
import com.xty.englishhelper.data.local.entity.ArticleParagraphEntity
import com.xty.englishhelper.data.local.entity.ArticleSentenceEntity
import com.xty.englishhelper.data.local.entity.ArticleWordLinkEntity
import com.xty.englishhelper.data.local.entity.ArticleWordStatEntity
import com.xty.englishhelper.data.local.entity.ParagraphAnalysisCacheEntity
import com.xty.englishhelper.data.local.entity.SentenceAnalysisCacheEntity
import com.xty.englishhelper.data.local.entity.WordExampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    // Articles
    @Query("SELECT * FROM articles WHERE is_saved = 1 ORDER BY updated_at DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    fun getArticleById(id: Long): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleByIdOnce(id: Long): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticle(entity: ArticleEntity): Long

    @Query("UPDATE articles SET parse_status = :status, updated_at = :now WHERE id = :articleId")
    suspend fun updateParseStatus(articleId: Long, status: Int, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM articles WHERE id = :articleId")
    suspend fun deleteArticle(articleId: Long)

    @Query("SELECT COUNT(*) FROM articles WHERE is_saved = 1")
    fun getArticleCount(): Flow<Int>

    // Sentences
    @Query("SELECT * FROM article_sentences WHERE article_id = :articleId ORDER BY sentence_index ASC")
    suspend fun getSentences(articleId: Long): List<ArticleSentenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<ArticleSentenceEntity>)

    @Query("DELETE FROM article_sentences WHERE article_id = :articleId")
    suspend fun deleteSentencesByArticle(articleId: Long)

    // Sentences by paragraph
    @Query("SELECT * FROM article_sentences WHERE paragraph_id = :paragraphId ORDER BY sentence_index ASC")
    suspend fun getSentencesByParagraph(paragraphId: Long): List<ArticleSentenceEntity>

    // Paragraphs
    @Query("SELECT * FROM article_paragraphs WHERE article_id = :articleId ORDER BY paragraph_index ASC")
    suspend fun getParagraphs(articleId: Long): List<ArticleParagraphEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParagraphs(paragraphs: List<ArticleParagraphEntity>)

    @Query("DELETE FROM article_paragraphs WHERE article_id = :articleId")
    suspend fun deleteParagraphsByArticle(articleId: Long)

    // Paragraph analysis cache
    @Query("SELECT * FROM paragraph_analysis_cache WHERE article_id = :articleId AND paragraph_id = :paragraphId AND paragraph_hash = :hash AND model_key = :modelKey LIMIT 1")
    suspend fun getParagraphAnalysisCache(articleId: Long, paragraphId: Long, hash: String, modelKey: String): ParagraphAnalysisCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParagraphAnalysisCache(entity: ParagraphAnalysisCacheEntity)

    // Word count update
    @Query("UPDATE articles SET word_count = :wordCount, updated_at = :now WHERE id = :articleId")
    suspend fun updateWordCount(articleId: Long, wordCount: Int, now: Long = System.currentTimeMillis())

    // Word stats
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWordStats(stats: List<ArticleWordStatEntity>)

    @Query("SELECT * FROM article_word_stats WHERE article_id = :articleId ORDER BY frequency DESC")
    suspend fun getWordStats(articleId: Long): List<ArticleWordStatEntity>

    @Query("DELETE FROM article_word_stats WHERE article_id = :articleId")
    suspend fun deleteWordStatsByArticle(articleId: Long)

    @Query("SELECT DISTINCT article_id FROM article_word_stats WHERE normalized_token IN (:tokens)")
    suspend fun getArticleIdsByTokens(tokens: List<String>): List<Long>

    // Word links
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWordLinks(links: List<ArticleWordLinkEntity>)

    @Query("SELECT * FROM article_word_links WHERE article_id = :articleId")
    suspend fun getWordLinks(articleId: Long): List<ArticleWordLinkEntity>

    @Query("SELECT * FROM article_word_links WHERE word_id = :wordId")
    suspend fun getWordLinksByWord(wordId: Long): List<ArticleWordLinkEntity>

    // Sentence analysis cache
    @Query("SELECT * FROM sentence_analysis_cache WHERE article_id = :articleId AND sentence_id = :sentenceId AND sentence_hash = :hash AND model_key = :modelKey LIMIT 1")
    suspend fun getAnalysisCache(articleId: Long, sentenceId: Long, hash: String, modelKey: String): SentenceAnalysisCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysisCache(entity: SentenceAnalysisCacheEntity)

    // Images
    @Query("SELECT * FROM article_images WHERE article_id = :articleId ORDER BY order_index ASC")
    suspend fun getImages(articleId: Long): List<ArticleImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ArticleImageEntity>)

    @Query("DELETE FROM article_images WHERE article_id = :articleId")
    suspend fun deleteImagesByArticle(articleId: Long)

    // Word examples
    @Query("SELECT * FROM word_examples WHERE word_id = :wordId ORDER BY created_at DESC")
    suspend fun getExamplesForWord(wordId: Long): List<WordExampleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExamples(examples: List<WordExampleEntity>)

    @Query("DELETE FROM word_examples WHERE source_article_id = :articleId")
    suspend fun deleteExamplesByArticle(articleId: Long)

    @Transaction
    suspend fun deleteArticleWithExamples(articleId: Long) {
        deleteExamplesByArticle(articleId)
        deleteArticle(articleId)
    }

    @Query("DELETE FROM article_word_links WHERE word_id = :wordId")
    suspend fun deleteWordLinksByWord(wordId: Long)

    @Query("DELETE FROM word_examples WHERE word_id = :wordId")
    suspend fun deleteExamplesByWord(wordId: Long)

    // Guardian: find article by source URL (stored in domain field)
    @Query("SELECT * FROM articles WHERE domain = :sourceUrl AND source_type_v2 = 'ONLINE' LIMIT 1")
    suspend fun getArticleBySourceUrl(sourceUrl: String): ArticleEntity?

    // Guardian: delete old unsaved articles
    @Query("DELETE FROM articles WHERE is_saved = 0 AND created_at < :cutoff")
    suspend fun deleteUnsavedArticlesBefore(cutoff: Long)

    // Guardian: update is_saved
    @Query("UPDATE articles SET is_saved = 1, updated_at = :now WHERE id = :articleId")
    suspend fun markArticleSaved(articleId: Long, now: Long = System.currentTimeMillis())

    // Guardian: get all saved articles only
    @Query("SELECT * FROM articles WHERE is_saved = 1 ORDER BY updated_at DESC")
    fun getSavedArticles(): Flow<List<ArticleEntity>>
}

data class WordMatchProjection(
    val id: Long,
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    @ColumnInfo(name = "normalized_spelling") val normalizedSpelling: String,
    @ColumnInfo(name = "inflections_json") val inflectionsJson: String
)
