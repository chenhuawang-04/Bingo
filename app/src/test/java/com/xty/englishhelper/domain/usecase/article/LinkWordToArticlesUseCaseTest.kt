package com.xty.englishhelper.domain.usecase.article

import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.WordExampleSourceType
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.WordExample
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LinkWordToArticlesUseCaseTest {

    private lateinit var repository: ArticleRepository
    private lateinit var useCase: LinkWordToArticlesUseCase

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = LinkWordToArticlesUseCase(repository)
    }

    @Test
    fun `cleans old links and examples before inserting new ones`() = runTest {
        coEvery { repository.getArticleIdsByTokens(any()) } returns emptyList()

        useCase(wordId = 1, dictionaryId = 1, spelling = "hello", inflections = emptyList())

        coVerifyOrder {
            repository.deleteWordLinksByWord(1)
            repository.deleteExamplesByWord(1)
        }
    }

    @Test
    fun `does nothing when no articles match`() = runTest {
        coEvery { repository.getArticleIdsByTokens(any()) } returns emptyList()

        useCase(wordId = 1, dictionaryId = 1, spelling = "hello", inflections = emptyList())

        coVerify(exactly = 0) { repository.upsertWordLinks(any()) }
        coVerify(exactly = 0) { repository.insertExamples(any()) }
    }

    @Test
    fun `sourceLabel uses article title in correct format`() = runTest {
        val article = Article(id = 100, title = "My Article", content = "The cat sat on the mat.")
        val sentences = listOf(
            ArticleSentence(id = 10, articleId = 100, sentenceIndex = 0, text = "The cat sat on the mat.", charStart = 0, charEnd = 23)
        )

        coEvery { repository.getArticleIdsByTokens(any()) } returns listOf(100L)
        coEvery { repository.getArticleByIdOnce(100) } returns article
        coEvery { repository.getSentences(100) } returns sentences

        val examplesSlot = slot<List<WordExample>>()
        coEvery { repository.insertExamples(capture(examplesSlot)) } returns Unit

        useCase(wordId = 1, dictionaryId = 1, spelling = "cat", inflections = emptyList())

        assertTrue(examplesSlot.isCaptured)
        val examples = examplesSlot.captured
        assertEquals(1, examples.size)
        assertEquals("「My Article」例句", examples[0].sourceLabel)
    }

    @Test
    fun `examples use ARTICLE source type`() = runTest {
        val article = Article(id = 100, title = "Test", content = "Hello world.")
        val sentences = listOf(
            ArticleSentence(id = 10, articleId = 100, sentenceIndex = 0, text = "Hello world.", charStart = 0, charEnd = 12)
        )

        coEvery { repository.getArticleIdsByTokens(any()) } returns listOf(100L)
        coEvery { repository.getArticleByIdOnce(100) } returns article
        coEvery { repository.getSentences(100) } returns sentences

        val examplesSlot = slot<List<WordExample>>()
        coEvery { repository.insertExamples(capture(examplesSlot)) } returns Unit

        useCase(wordId = 1, dictionaryId = 1, spelling = "hello", inflections = emptyList())

        assertTrue(examplesSlot.isCaptured)
        assertEquals(WordExampleSourceType.ARTICLE, examplesSlot.captured[0].sourceType)
    }

    @Test
    fun `matches word by inflection form`() = runTest {
        val article = Article(id = 100, title = "Test", content = "The cats are here.")
        val sentences = listOf(
            ArticleSentence(id = 10, articleId = 100, sentenceIndex = 0, text = "The cats are here.", charStart = 0, charEnd = 18)
        )

        coEvery { repository.getArticleIdsByTokens(any()) } returns listOf(100L)
        coEvery { repository.getArticleByIdOnce(100) } returns article
        coEvery { repository.getSentences(100) } returns sentences

        val linksSlot = slot<List<ArticleWordLink>>()
        coEvery { repository.upsertWordLinks(capture(linksSlot)) } returns Unit

        useCase(
            wordId = 1,
            dictionaryId = 1,
            spelling = "cat",
            inflections = listOf(Inflection(form = "cats", formType = "plural"))
        )

        assertTrue(linksSlot.isCaptured)
        assertEquals(1, linksSlot.captured.size)
        assertEquals("cats", linksSlot.captured[0].matchedToken)
    }

    @Test
    fun `creates only one link per sentence even with multiple token matches`() = runTest {
        val article = Article(id = 100, title = "Test", content = "The cat chased another cat.")
        val sentences = listOf(
            ArticleSentence(id = 10, articleId = 100, sentenceIndex = 0, text = "The cat chased another cat.", charStart = 0, charEnd = 27)
        )

        coEvery { repository.getArticleIdsByTokens(any()) } returns listOf(100L)
        coEvery { repository.getArticleByIdOnce(100) } returns article
        coEvery { repository.getSentences(100) } returns sentences

        val linksSlot = slot<List<ArticleWordLink>>()
        coEvery { repository.upsertWordLinks(capture(linksSlot)) } returns Unit

        useCase(
            wordId = 1,
            dictionaryId = 1,
            spelling = "cat",
            inflections = listOf(Inflection(form = "cats", formType = "plural"))
        )

        // Should break after first token match per sentence
        assertTrue(linksSlot.isCaptured)
        assertEquals(1, linksSlot.captured.size)
    }

    @Test
    fun `sets correct fields on word link`() = runTest {
        val article = Article(id = 100, title = "Test", content = "Hello world.")
        val sentences = listOf(
            ArticleSentence(id = 10, articleId = 100, sentenceIndex = 0, text = "Hello world.", charStart = 0, charEnd = 12)
        )

        coEvery { repository.getArticleIdsByTokens(any()) } returns listOf(100L)
        coEvery { repository.getArticleByIdOnce(100) } returns article
        coEvery { repository.getSentences(100) } returns sentences

        val linksSlot = slot<List<ArticleWordLink>>()
        coEvery { repository.upsertWordLinks(capture(linksSlot)) } returns Unit

        useCase(wordId = 5, dictionaryId = 3, spelling = "hello", inflections = emptyList())

        assertTrue(linksSlot.isCaptured)
        val link = linksSlot.captured[0]
        assertEquals(100L, link.articleId)
        assertEquals(10L, link.sentenceId)
        assertEquals(5L, link.wordId)
        assertEquals(3L, link.dictionaryId)
        assertEquals("Hello", link.matchedToken)
    }

    @Test
    fun `sets correct fields on word example`() = runTest {
        val article = Article(id = 100, title = "Reading Practice", content = "Hello world.")
        val sentences = listOf(
            ArticleSentence(id = 10, articleId = 100, sentenceIndex = 0, text = "Hello world.", charStart = 0, charEnd = 12)
        )

        coEvery { repository.getArticleIdsByTokens(any()) } returns listOf(100L)
        coEvery { repository.getArticleByIdOnce(100) } returns article
        coEvery { repository.getSentences(100) } returns sentences

        val examplesSlot = slot<List<WordExample>>()
        coEvery { repository.insertExamples(capture(examplesSlot)) } returns Unit

        useCase(wordId = 5, dictionaryId = 3, spelling = "hello", inflections = emptyList())

        assertTrue(examplesSlot.isCaptured)
        val example = examplesSlot.captured[0]
        assertEquals(5L, example.wordId)
        assertEquals("Hello world.", example.sentence)
        assertEquals(WordExampleSourceType.ARTICLE, example.sourceType)
        assertEquals(100L, example.sourceArticleId)
        assertEquals(10L, example.sourceSentenceId)
        assertEquals("「Reading Practice」例句", example.sourceLabel)
    }

    @Test
    fun `skips article when getArticleByIdOnce returns null`() = runTest {
        coEvery { repository.getArticleIdsByTokens(any()) } returns listOf(100L)
        coEvery { repository.getArticleByIdOnce(100) } returns null

        useCase(wordId = 1, dictionaryId = 1, spelling = "hello", inflections = emptyList())

        coVerify(exactly = 0) { repository.getSentences(any()) }
        coVerify(exactly = 0) { repository.upsertWordLinks(any()) }
        coVerify(exactly = 0) { repository.insertExamples(any()) }
    }

    @Test
    fun `processes multiple articles`() = runTest {
        val article1 = Article(id = 100, title = "Article 1", content = "Hello.")
        val article2 = Article(id = 200, title = "Article 2", content = "Say hello.")
        val sentences1 = listOf(
            ArticleSentence(id = 10, articleId = 100, sentenceIndex = 0, text = "Hello.", charStart = 0, charEnd = 6)
        )
        val sentences2 = listOf(
            ArticleSentence(id = 20, articleId = 200, sentenceIndex = 0, text = "Say hello.", charStart = 0, charEnd = 10)
        )

        coEvery { repository.getArticleIdsByTokens(any()) } returns listOf(100L, 200L)
        coEvery { repository.getArticleByIdOnce(100) } returns article1
        coEvery { repository.getArticleByIdOnce(200) } returns article2
        coEvery { repository.getSentences(100) } returns sentences1
        coEvery { repository.getSentences(200) } returns sentences2

        val linksSlot = slot<List<ArticleWordLink>>()
        coEvery { repository.upsertWordLinks(capture(linksSlot)) } returns Unit

        val examplesSlot = slot<List<WordExample>>()
        coEvery { repository.insertExamples(capture(examplesSlot)) } returns Unit

        useCase(wordId = 1, dictionaryId = 1, spelling = "hello", inflections = emptyList())

        assertTrue(linksSlot.isCaptured)
        assertEquals(2, linksSlot.captured.size)

        assertTrue(examplesSlot.isCaptured)
        assertEquals(2, examplesSlot.captured.size)
        assertEquals("「Article 1」例句", examplesSlot.captured[0].sourceLabel)
        assertEquals("「Article 2」例句", examplesSlot.captured[1].sourceLabel)
    }
}
