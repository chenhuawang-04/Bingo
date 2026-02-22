package com.xty.englishhelper.domain.usecase.word

import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.usecase.article.LinkWordToArticlesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveWordUseCaseTest {

    private lateinit var wordRepository: WordRepository
    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var linkWordToArticles: LinkWordToArticlesUseCase
    private lateinit var saveWordUseCase: SaveWordUseCase

    @Before
    fun setUp() {
        wordRepository = mockk(relaxed = true)
        dictionaryRepository = mockk(relaxed = true)
        linkWordToArticles = mockk(relaxed = true)
        saveWordUseCase = SaveWordUseCase(wordRepository, dictionaryRepository, linkWordToArticles)
    }

    @Test
    fun `insert mode - new word gets UUID and calls insertWord`() = runTest {
        coEvery { wordRepository.findByNormalizedSpelling(any(), any()) } returns null
        coEvery { wordRepository.insertWord(any()) } returns 42L

        val word = WordDetails(
            id = 0,
            dictionaryId = 1,
            spelling = "Hello"
        )

        val result = saveWordUseCase(word)

        assertEquals(42L, result)
        coVerify {
            wordRepository.insertWord(match {
                it.normalizedSpelling == "hello" && it.wordUid.isNotBlank()
            })
        }
        coVerify { dictionaryRepository.updateWordCount(1) }
    }

    @Test
    fun `insert mode - existing spelling triggers upsert with updateWord`() = runTest {
        val existing = WordDetails(
            id = 10,
            dictionaryId = 1,
            spelling = "hello",
            wordUid = "existing-uid-123"
        )
        coEvery { wordRepository.findByNormalizedSpelling(1, "hello") } returns existing

        val word = WordDetails(
            id = 0,
            dictionaryId = 1,
            spelling = "Hello",
            phonetic = "/həˈloʊ/"
        )

        val result = saveWordUseCase(word)

        assertEquals(10L, result)
        coVerify {
            wordRepository.updateWord(match {
                it.id == 10L && it.wordUid == "existing-uid-123" && it.normalizedSpelling == "hello"
            })
        }
        // Should NOT call insertWord
        coVerify(exactly = 0) { wordRepository.insertWord(any()) }
    }

    @Test
    fun `edit mode - keeps original wordUid and calls updateWord`() = runTest {
        // Simulate ViewModel passing wordUid (already loaded from DB)
        val word = WordDetails(
            id = 5,
            dictionaryId = 1,
            spelling = "World",
            wordUid = "original-uid-456"
        )

        coEvery { wordRepository.getWordById(5) } returns word

        val result = saveWordUseCase(word)

        assertEquals(5L, result)
        coVerify {
            wordRepository.updateWord(match {
                it.id == 5L && it.wordUid == "original-uid-456" && it.normalizedSpelling == "world"
            })
        }
        coVerify { dictionaryRepository.updateWordCount(1) }
    }

    @Test
    fun `edit mode - recovers wordUid from DB when ViewModel omits it`() = runTest {
        // Simulate ViewModel constructing WordDetails without wordUid/createdAt
        val word = WordDetails(
            id = 5,
            dictionaryId = 1,
            spelling = "World",
            wordUid = "",  // ViewModel doesn't carry this
            createdAt = System.currentTimeMillis()  // fresh timestamp
        )

        val dbWord = WordDetails(
            id = 5,
            dictionaryId = 1,
            spelling = "World",
            wordUid = "db-uid-789",
            createdAt = 1000L
        )
        coEvery { wordRepository.getWordById(5) } returns dbWord

        val result = saveWordUseCase(word)

        assertEquals(5L, result)
        coVerify {
            wordRepository.updateWord(match {
                it.id == 5L && it.wordUid == "db-uid-789" && it.createdAt == 1000L
            })
        }
    }

    @Test
    fun `insert mode - normalizedSpelling trims and lowercases`() = runTest {
        coEvery { wordRepository.findByNormalizedSpelling(any(), any()) } returns null
        coEvery { wordRepository.insertWord(any()) } returns 1L

        val word = WordDetails(
            id = 0,
            dictionaryId = 1,
            spelling = "  APPLE  "
        )

        saveWordUseCase(word)

        coVerify {
            wordRepository.findByNormalizedSpelling(1, "apple")
        }
        coVerify {
            wordRepository.insertWord(match {
                it.normalizedSpelling == "apple"
            })
        }
    }

    @Test
    fun `insert mode - new word has non-empty wordUid`() = runTest {
        coEvery { wordRepository.findByNormalizedSpelling(any(), any()) } returns null
        coEvery { wordRepository.insertWord(any()) } returns 1L

        val word = WordDetails(
            id = 0,
            dictionaryId = 1,
            spelling = "test"
        )

        saveWordUseCase(word)

        coVerify {
            wordRepository.insertWord(match {
                it.wordUid.isNotEmpty() && it.wordUid.length == 36 // UUID format
            })
        }
    }
}
