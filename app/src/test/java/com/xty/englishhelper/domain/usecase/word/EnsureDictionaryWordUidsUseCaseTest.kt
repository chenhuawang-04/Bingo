package com.xty.englishhelper.domain.usecase.word

import com.xty.englishhelper.data.sync.DictionaryWordUidNormalizer
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.domain.repository.WordRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EnsureDictionaryWordUidsUseCaseTest {

    private lateinit var wordRepository: WordRepository
    private lateinit var transactionRunner: TransactionRunner
    private lateinit var useCase: EnsureDictionaryWordUidsUseCase
    private val normalizer = DictionaryWordUidNormalizer()

    @Before
    fun setUp() {
        wordRepository = mockk(relaxed = true)
        transactionRunner = object : TransactionRunner {
            override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
        }
        useCase = EnsureDictionaryWordUidsUseCase(wordRepository, transactionRunner, normalizer)
    }

    @Test
    fun `repairs missing wordUid deterministically`() = runTest {
        val original = WordDetails(
            id = 1L,
            dictionaryId = 7L,
            spelling = "Apple",
            normalizedSpelling = "apple",
            wordUid = "",
            createdAt = 10L,
            updatedAt = 20L
        )
        val repairedUid = normalizer.generateUid("CET4", "apple")
        val repaired = original.copy(wordUid = repairedUid)

        every { wordRepository.getWordsByDictionary(7L) } returnsMany listOf(
            flowOf(listOf(original)),
            flowOf(listOf(repaired))
        )
        coEvery { wordRepository.updateWord(any()) } returns Unit

        val result = useCase(7L, "CET4")

        coVerify {
            wordRepository.updateWord(match {
                it.id == 1L &&
                    it.wordUid == repairedUid &&
                    it.createdAt == 10L &&
                    it.updatedAt == 20L
            })
        }
        assertEquals(repairedUid, result.single().wordUid)
    }
}
