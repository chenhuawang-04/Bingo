package com.xty.englishhelper.ui.screen.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleFilterControlsTest {

    private data class FakeArticle(
        val title: String,
        val wordCount: Int?,
        val score: Int?
    )

    @Test
    fun `disabled filters keep original list regardless of configured rules`() {
        val items = listOf(
            FakeArticle(title = "B", wordCount = 1800, score = 92),
            FakeArticle(title = "A", wordCount = 500, score = 58)
        )

        val presented = applyArticlePresentation(
            items = items,
            filtersEnabled = false,
            lengthFilter = ArticleLengthFilter.LONG,
            scoreFilter = ArticleScoreFilter.TOP,
            sortOption = ArticleSortOption.SCORE_ASC,
            wordCountOf = { it.wordCount },
            scoreOf = { it.score },
            titleOf = { it.title }
        )

        assertEquals(items, presented)
    }

    @Test
    fun `enabled filters apply both filtering and sorting`() {
        val items = listOf(
            FakeArticle(title = "Gamma", wordCount = 2100, score = 88),
            FakeArticle(title = "Alpha", wordCount = 1600, score = 85),
            FakeArticle(title = "Beta", wordCount = 700, score = 95)
        )

        val presented = applyArticlePresentation(
            items = items,
            filtersEnabled = true,
            lengthFilter = ArticleLengthFilter.LONG,
            scoreFilter = ArticleScoreFilter.HIGH,
            sortOption = ArticleSortOption.SCORE_DESC,
            wordCountOf = { it.wordCount },
            scoreOf = { it.score },
            titleOf = { it.title }
        )

        assertEquals(listOf("Gamma", "Alpha"), presented.map { it.title })
    }

    @Test
    fun `filter config and active state are computed separately`() {
        assertFalse(
            hasArticleFilterConfig(
                lengthFilter = ArticleLengthFilter.ALL,
                scoreFilter = ArticleScoreFilter.ALL,
                sortOption = ArticleSortOption.DEFAULT
            )
        )
        assertTrue(
            hasArticleFilterConfig(
                lengthFilter = ArticleLengthFilter.MEDIUM,
                scoreFilter = ArticleScoreFilter.ALL,
                sortOption = ArticleSortOption.DEFAULT
            )
        )
        assertFalse(
            isArticleFilterActive(
                filtersEnabled = false,
                lengthFilter = ArticleLengthFilter.MEDIUM,
                scoreFilter = ArticleScoreFilter.ALL,
                sortOption = ArticleSortOption.DEFAULT
            )
        )
        assertTrue(
            isArticleFilterActive(
                filtersEnabled = true,
                lengthFilter = ArticleLengthFilter.MEDIUM,
                scoreFilter = ArticleScoreFilter.ALL,
                sortOption = ArticleSortOption.DEFAULT
            )
        )
    }

    @Test
    fun `config updates preserve paused state and only keep active state when already enabled`() {
        assertFalse(
            resolveFilterEnabledAfterConfigChange(
                previousEnabled = false,
                lengthFilter = ArticleLengthFilter.LONG,
                scoreFilter = ArticleScoreFilter.ALL,
                sortOption = ArticleSortOption.DEFAULT
            )
        )

        assertTrue(
            resolveFilterEnabledAfterConfigChange(
                previousEnabled = true,
                lengthFilter = ArticleLengthFilter.LONG,
                scoreFilter = ArticleScoreFilter.ALL,
                sortOption = ArticleSortOption.DEFAULT
            )
        )

        assertFalse(
            resolveFilterEnabledAfterConfigChange(
                previousEnabled = true,
                lengthFilter = ArticleLengthFilter.ALL,
                scoreFilter = ArticleScoreFilter.ALL,
                sortOption = ArticleSortOption.DEFAULT
            )
        )
    }
}
