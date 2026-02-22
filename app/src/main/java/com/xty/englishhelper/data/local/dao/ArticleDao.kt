package com.xty.englishhelper.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xty.englishhelper.data.local.entity.ArticleEntity
import com.xty.englishhelper.data.local.entity.ArticleImageEntity
import com.xty.englishhelper.data.local.entity.ArticleSentenceEntity
import com.xty.englishhelper.data.local.entity.ArticleWordLinkEntity
import com.xty.englishhelper.data.local.entity.ArticleWordStatEntity
import com.xty.englishhelper.data.local.entity.SentenceAnalysisCacheEntity
import com.xty.englishhelper.data.local.entity.WordExampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    // Articles
    @Query("SELECT * FROM articles ORDER BY updated_at DESC")
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

    @Query("SELECT COUNT(*) FROM articles")
    fun getArticleCount(): Flow<Int>

    // Sentences
    @Query("SELECT * FROM article_sentences WHERE article_id = :articleId ORDER BY sentence_index ASC")
    suspend fun getSentences(articleId: Long): List<ArticleSentenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<ArticleSentenceEntity>)

    @Query("DELETE FROM article_sentences WHERE article_id = :articleId")
    suspend fun deleteSentencesByArticle(articleId: Long)

    // Word stats
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWordStats(stats: List<ArticleWordStatEntity>)

    @Query("SELECT * FROM article_word_stats WHERE article_id = :articleId ORDER BY frequency DESC")
    suspend fun getWordStats(articleId: Long): List<ArticleWordStatEntity>

    @Query("DELETE FROM article_word_stats WHERE article_id = :articleId")
    suspend fun deleteWordStatsByArticle(articleId: Long)

    // Word links
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWordLinks(links: List<ArticleWordLinkEntity>)

    @Query("SELECT * FROM article_word_links WHERE article_id = :articleId")
    suspend fun getWordLinks(articleId: Long): List<ArticleWordLinkEntity>

    @Query("SELECT * FROM article_word_links WHERE word_id = :wordId")
    suspend fun getWordLinksByWord(wordId: Long): List<ArticleWordLinkEntity>

    // Sentence analysis cache
    @Query("SELECT * FROM sentence_analysis_cache WHERE article_id = :articleId AND sentence_id = :sentenceId AND sentence_hash = :hash LIMIT 1")
    suspend fun getAnalysisCache(articleId: Long, sentenceId: Long, hash: String): SentenceAnalysisCacheEntity?

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
}

data class WordMatchProjection(
    val id: Long,
    @ColumnInfo(name = "dictionary_id") val dictionaryId: Long,
    @ColumnInfo(name = "normalized_spelling") val normalizedSpelling: String,
    @ColumnInfo(name = "inflections_json") val inflectionsJson: String
)
