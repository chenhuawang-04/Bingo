package com.xty.englishhelper.ui.screen.article

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleFilterControlsTest {

    private data class TestArticle(
        val title: String,
        val wordCount: Int?,
        val score: Int?
    )

    @Test
    fun `length filter excludes unknown word count when non all`() {
        val items = listOf(
            TestArticle(title = "Unknown", wordCount = null, score = 88),
            TestArticle(title = "Short", wordCount = 720, score = 75),
            TestArticle(title = "Medium", wordCount = 1200, score = 82)
        )

        val result = applyArticlePresentation(
            items = items,
            lengthFilter = ArticleLengthFilter.SHORT,
            scoreFilter = ArticleScoreFilter.ALL,
            sortOption = ArticleSortOption.DEFAULT,
            wordCountOf = { it.wordCount },
            scoreOf = { it.score },
            titleOf = { it.title }
        )

        assertEquals(listOf("Short"), result.map { it.title })
    }

    @Test
    fun `score filter excludes unscored items`() {
        val items = listOf(
            TestArticle(title = "Unscored", wordCount = 900, score = null),
            TestArticle(title = "Low", wordCount = 900, score = 58),
            TestArticle(title = "High", wordCount = 900, score = 91)
        )

        val result = applyArticlePresentation(
            items = items,
            lengthFilter = ArticleLengthFilter.ALL,
            scoreFilter = ArticleScoreFilter.TOP,
            sortOption = ArticleSortOption.DEFAULT,
            wordCountOf = { it.wordCount },
            scoreOf = { it.score },
            titleOf = { it.title }
        )

        assertEquals(listOf("High"), result.map { it.title })
    }

    @Test
    fun `score descending keeps unscored items at the end`() {
        val items = listOf(
            TestArticle(title = "Gamma", wordCount = 800, score = null),
            TestArticle(title = "Alpha", wordCount = 800, score = 86),
            TestArticle(title = "Beta", wordCount = 800, score = 92)
        )

        val result = applyArticlePresentation(
            items = items,
            lengthFilter = ArticleLengthFilter.ALL,
            scoreFilter = ArticleScoreFilter.ALL,
            sortOption = ArticleSortOption.SCORE_DESC,
            wordCountOf = { it.wordCount },
            scoreOf = { it.score },
            titleOf = { it.title }
        )

        assertEquals(listOf("Beta", "Alpha", "Gamma"), result.map { it.title })
    }
}
