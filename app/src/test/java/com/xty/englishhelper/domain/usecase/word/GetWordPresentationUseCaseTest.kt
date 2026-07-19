package com.xty.englishhelper.domain.usecase.word

import com.xty.englishhelper.domain.model.AssociatedWordInfo
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.WordClusterRepository
import com.xty.englishhelper.domain.repository.WordPhraseRepository
import com.xty.englishhelper.domain.usecase.article.GetWordExamplesUseCase
import com.xty.englishhelper.domain.usecase.pool.GetWordPoolsUseCase
import com.xty.englishhelper.domain.usecase.study.GetStudyWordEdgePreviewsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetWordPresentationUseCaseTest {

    private val resolveLinkedWords = mockk<ResolveLinkedWordsUseCase>()
    private val getAssociatedWords = mockk<GetAssociatedWordsUseCase>()
    private val getWordExamples = mockk<GetWordExamplesUseCase>()
    private val getWordPools = mockk<GetWordPoolsUseCase>()
    private val wordClusterRepository = mockk<WordClusterRepository>()
    private val getWordEdgePreviews = mockk<GetStudyWordEdgePreviewsUseCase>()
    private val wordPhraseRepository = mockk<WordPhraseRepository>()

    private val useCase = GetWordPresentationUseCase(
        resolveLinkedWords = resolveLinkedWords,
        getAssociatedWords = getAssociatedWords,
        getWordExamples = getWordExamples,
        getWordPools = getWordPools,
        wordClusterRepository = wordClusterRepository,
        getWordEdgePreviews = getWordEdgePreviews,
        wordPhraseRepository = wordPhraseRepository
    )

    @Test
    fun `one failed source does not discard successful word details`() = runTest {
        val word = WordDetails(id = 7L, dictionaryId = 3L, spelling = "stable")
        val associated = listOf(
            AssociatedWordInfo(
                wordId = 8L,
                spelling = "stability",
                similarity = 0.8f,
                commonSegments = listOf("stabil")
            )
        )

        coEvery { getAssociatedWords(7L) } returns associated
        coEvery { getWordExamples(7L) } throws IllegalStateException("article database unavailable")
        coEvery { getWordPools(7L) } returns emptyList()
        coEvery { wordClusterRepository.getClusterReviewsForWord(7L) } returns emptyList()
        coEvery { getWordEdgePreviews(3L, 7L, 0.0) } returns emptyList()
        coEvery { wordPhraseRepository.getPhrasesForWord(7L) } returns emptyList()

        val result = useCase(word)

        assertEquals(associated, result.associatedWords)
        assertTrue(result.examples.isEmpty())
        assertEquals(setOf(WordPresentationSection.ARTICLE_EXAMPLES), result.failedSections)
    }
}
