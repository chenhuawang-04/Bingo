package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.DictionaryDao
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.local.entity.WordEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.domain.background.PoolEdgeWriteCoordinator
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WordRepositoryImplDeletionTest {

    @Test
    fun `deleting a dictionary cleans edge and exclusion rows transactionally`() = runTest {
        val dictionaryDao = mockk<DictionaryDao>(relaxed = true)
        val wordEdgeDao = mockk<WordEdgeDao>(relaxed = true)
        val wordPoolDao = mockk<WordPoolDao>(relaxed = true)
        val transactionRunner = RecordingTransactionRunner()
        val repository = DictionaryRepositoryImpl(
            dictionaryDao,
            wordEdgeDao,
            wordPoolDao,
            transactionRunner,
            PoolEdgeWriteCoordinator()
        )

        repository.deleteDictionary(3L)

        coVerify(exactly = 1) { wordEdgeDao.deleteByDictionary(3L) }
        coVerify(exactly = 1) { wordEdgeDao.deleteExcludedByDictionary(3L) }
        coVerify(exactly = 1) { dictionaryDao.deleteById(3L) }
        assertEquals(1, transactionRunner.executedTransactions)
    }

    @Test
    fun `deleting a word cleans edge state and invalid pools in one transaction`() = runTest {
        val wordDao = mockk<WordDao>(relaxed = true)
        val wordEdgeDao = mockk<WordEdgeDao>(relaxed = true)
        val wordPoolDao = mockk<WordPoolDao>(relaxed = true)
        val transactionRunner = RecordingTransactionRunner()
        val repository = WordRepositoryImpl(
            wordDao,
            wordEdgeDao,
            wordPoolDao,
            transactionRunner,
            PoolEdgeWriteCoordinator()
        )
        coEvery { wordDao.getWordById(10L) } returns WordWithDetails(
            word = WordEntity(id = 10L, dictionaryId = 3L, spelling = "adapt"),
            synonyms = emptyList(),
            similarWords = emptyList(),
            cognates = emptyList()
        )

        repository.deleteWord(10L)

        coVerify(exactly = 1) { wordPoolDao.deletePoolsContainingWord(10L) }
        coVerify(exactly = 1) { wordEdgeDao.deleteEdgesForWord(3L, 10L) }
        coVerify(exactly = 1) { wordEdgeDao.deleteExcludedForWord(3L, 10L) }
        coVerify(exactly = 1) { wordEdgeDao.deleteStagedEdgesForWord(3L, 10L) }
        coVerify(exactly = 1) { wordDao.deleteWord(10L) }
        coVerify(exactly = 1) { wordPoolDao.deletePoolsWithFewerThanTwoMembers(3L) }
        coVerifyOrder {
            wordPoolDao.deletePoolsContainingWord(10L)
            wordEdgeDao.deleteEdgesForWord(3L, 10L)
            wordEdgeDao.deleteExcludedForWord(3L, 10L)
            wordEdgeDao.deleteStagedEdgesForWord(3L, 10L)
            wordDao.deleteWord(10L)
            wordPoolDao.deletePoolsWithFewerThanTwoMembers(3L)
        }
        assertEquals(1, transactionRunner.executedTransactions)
    }

    private class RecordingTransactionRunner : TransactionRunner {
        var executedTransactions: Int = 0

        override suspend fun <R> runInTransaction(block: suspend () -> R): R {
            executedTransactions++
            return block()
        }
    }
}
