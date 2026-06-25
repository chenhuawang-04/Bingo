package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.AppDatabase
import com.xty.englishhelper.data.local.dao.EdgeProjection
import com.xty.englishhelper.data.local.dao.GraphEdgeProjection
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.dao.WordLabelProjection
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.repository.pool.EdgeReviewer
import com.xty.englishhelper.data.repository.pool.EntryTypeClassifier
import com.xty.englishhelper.domain.background.PoolBuildLiveMonitor
import com.xty.englishhelper.domain.model.EdgeType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WordPoolRepositoryImplGraphTest {

    @Test
    fun `relation graph uses lightweight edge projection and caches immediate reloads`() = runTest {
        val wordDao = mockk<WordDao>()
        val wordEdgeDao = mockk<WordEdgeDao>()
        val repository = repository(wordDao = wordDao, wordEdgeDao = wordEdgeDao)

        coEvery { wordDao.getWordLabels(1L) } returns listOf(
            WordLabelProjection(id = 10L, spelling = "adapt"),
            WordLabelProjection(id = 20L, spelling = "adopt")
        )
        coEvery { wordEdgeDao.countEdges(1L) } returns 1
        coEvery { wordEdgeDao.getGraphEdgesPage(1L, 0L, any()) } returns listOf(
            GraphEdgeProjection(
                id = 7L,
                wordIdA = 10L,
                wordIdB = 20L,
                edgeType = EdgeType.FORM_SPELLING.dbValue,
                relationStrength = 4,
                confidence = 0.8
            )
        )
        coEvery { wordEdgeDao.getGraphEdgesPage(1L, 7L, any()) } returns emptyList()

        val first = repository.getWordRelationGraph(1L)
        val second = repository.getWordRelationGraph(1L)

        assertSame(first, second)
        assertEquals(1, first.edges.size)
        assertEquals(7L, first.edges.single().edgeId)
        assertEquals(EdgeType.FORM_SPELLING, first.edges.single().type)
        coVerify(exactly = 1) { wordDao.getWordLabels(1L) }
        coVerify(exactly = 1) { wordEdgeDao.countEdges(1L) }
        coVerify(exactly = 1) { wordEdgeDao.getGraphEdgesPage(1L, 0L, any()) }
        coVerify(exactly = 1) { wordEdgeDao.getGraphEdgesPage(1L, 7L, any()) }
        coVerify(exactly = 0) { wordEdgeDao.getGraphEdges(any()) }
        coVerify(exactly = 0) { wordEdgeDao.getAllEdges(any()) }
    }

    @Test
    fun `edge detail is loaded by id only when requested`() = runTest {
        val wordEdgeDao = mockk<WordEdgeDao>()
        val repository = repository(wordEdgeDao = wordEdgeDao)

        coEvery { wordEdgeDao.getEdgeDetail(7L) } returns EdgeProjection(
            id = 7L,
            wordIdA = 10L,
            wordIdB = 20L,
            edgeType = EdgeType.SEMANTIC_SYNONYM.dbValue,
            status = "warning",
            learningValue = 5,
            relationStrength = 4,
            confidence = 0.87,
            reason = "shared meaning",
            warningNote = "check register",
            evidenceSource = null,
            register = "formal",
            exampleSentence = "They share a close meaning.",
            difficultyCefr = "B2"
        )

        val detail = repository.getWordGraphEdgeDetail(7L)

        requireNotNull(detail)
        assertEquals(7L, detail.edgeId)
        assertEquals(EdgeType.SEMANTIC_SYNONYM, detail.type)
        assertEquals("shared meaning", detail.reason)
        assertEquals("They share a close meaning.", detail.exampleSentence)
        coVerify(exactly = 1) { wordEdgeDao.getEdgeDetail(7L) }
    }

    private fun repository(
        wordDao: WordDao = mockk(relaxed = true),
        wordEdgeDao: WordEdgeDao = mockk(relaxed = true)
    ): WordPoolRepositoryImpl =
        WordPoolRepositoryImpl(
            db = mockk<AppDatabase>(relaxed = true),
            wordPoolDao = mockk<WordPoolDao>(relaxed = true),
            wordEdgeDao = wordEdgeDao,
            wordDao = wordDao,
            aiApiClientProvider = mockk<AiApiClientProvider>(relaxed = true),
            settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
            edgeReviewer = mockk<EdgeReviewer>(relaxed = true),
            entryTypeClassifier = mockk<EntryTypeClassifier>(relaxed = true),
            liveMonitor = mockk<PoolBuildLiveMonitor>(relaxed = true)
        )
}
