package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.AppDatabase
import com.xty.englishhelper.data.local.dao.EdgeProjection
import com.xty.englishhelper.data.local.dao.GraphEdgeProjection
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.dao.WordEdgeDao
import com.xty.englishhelper.data.local.dao.WordLabelProjection
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.local.entity.WordEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.repository.pool.EdgeReviewer
import com.xty.englishhelper.data.repository.pool.EntryTypeClassifier
import com.xty.englishhelper.domain.background.PoolBuildLiveMonitor
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.PoolStrategy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun `review pools purifies edges without rebuilding persisted pools`() = runTest {
        val wordDao = mockk<WordDao>()
        val wordEdgeDao = mockk<WordEdgeDao>()
        val wordPoolDao = mockk<WordPoolDao>(relaxed = true)
        val edgeReviewer = mockk<EdgeReviewer>()
        val repository = repository(
            wordDao = wordDao,
            wordEdgeDao = wordEdgeDao,
            wordPoolDao = wordPoolDao,
            edgeReviewer = edgeReviewer
        )

        coEvery { wordEdgeDao.countEdges(1L) } returns 2
        coEvery { wordDao.getWordLabels(1L) } returns listOf(
            WordLabelProjection(id = 10L, spelling = "adapt"),
            WordLabelProjection(id = 20L, spelling = "adopt")
        )
        coEvery {
            edgeReviewer.reviewDictionaryEdgesWithAi(
                1L,
                mapOf(10L to "adapt", 20L to "adopt"),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns true

        repository.reviewPools(dictionaryId = 1L, strategy = PoolStrategy.QUALITY_FIRST)

        coVerify(exactly = 1) {
            edgeReviewer.reviewDictionaryEdgesWithAi(
                1L,
                mapOf(10L to "adapt", 20L to "adopt"),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
        coVerify(exactly = 0) { wordPoolDao.deleteByDictionaryAndStrategy(any(), any()) }
        coVerify(exactly = 0) { wordPoolDao.insertPool(any()) }
        coVerify(exactly = 0) { wordPoolDao.insertMembers(any()) }
        coVerify(exactly = 0) { wordEdgeDao.getSignificantEdgesPageFull(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `word edge previews group multiple edge types for the same neighbor`() = runTest {
        val wordDao = mockk<WordDao>()
        val wordEdgeDao = mockk<WordEdgeDao>()
        val repository = repository(wordDao = wordDao, wordEdgeDao = wordEdgeDao)

        coEvery { wordEdgeDao.getEdgesForWord(1L, 10L, 1.0) } returns listOf(
            WordEdgeEntity(
                id = 1L,
                wordIdA = 10L,
                wordIdB = 20L,
                edgeType = EdgeType.FORM_SPELLING.dbValue,
                dictionaryId = 1L,
                confidence = 1.0
            ),
            WordEdgeEntity(
                id = 2L,
                wordIdA = 10L,
                wordIdB = 20L,
                edgeType = EdgeType.LEARNING_CONFUSABLE.dbValue,
                dictionaryId = 1L,
                confidence = 1.0
            ),
            WordEdgeEntity(
                id = 3L,
                wordIdA = 30L,
                wordIdB = 10L,
                edgeType = EdgeType.SEMANTIC_SYNONYM.dbValue,
                dictionaryId = 1L,
                confidence = 1.0
            )
        )
        coEvery { wordDao.getWordsByIds(listOf(20L, 30L)) } returns listOf(
            wordWithDetails(20L, 1L, "adopt"),
            wordWithDetails(30L, 1L, "adjust")
        )

        val previews = repository.getWordEdgePreviews(1L, 10L, 1.0)

        assertEquals(2, previews.size)
        assertEquals("adopt", previews[0].spelling)
        assertEquals(setOf(EdgeType.FORM_SPELLING, EdgeType.LEARNING_CONFUSABLE), previews[0].edgeTypes)
        assertEquals("adjust", previews[1].spelling)
        assertEquals(setOf(EdgeType.SEMANTIC_SYNONYM), previews[1].edgeTypes)
    }

    @Test
    fun `confirm word relation only promotes confidence without rebuilding pools`() = runTest {
        val wordEdgeDao = mockk<WordEdgeDao>()
        val wordPoolDao = mockk<WordPoolDao>(relaxed = true)
        val repository = repository(wordEdgeDao = wordEdgeDao, wordPoolDao = wordPoolDao)

        coEvery { wordEdgeDao.getEdgesBetweenWords(1L, 10L, 20L) } returns listOf(
            WordEdgeEntity(
                id = 7L,
                wordIdA = 10L,
                wordIdB = 20L,
                edgeType = EdgeType.FORM_SPELLING.dbValue,
                dictionaryId = 1L,
                status = "optional",
                confidence = 0.42
            )
        )
        coEvery { wordEdgeDao.updateEdgeStatus(any(), any(), any()) } returns Unit

        val confirmed = repository.confirmWordRelation(1L, 10L, 20L)

        assertTrue(confirmed)
        coVerify(exactly = 1) { wordEdgeDao.updateEdgeStatus(7L, "optional", 1.0) }
        coVerify(exactly = 0) { wordPoolDao.deleteByDictionaryAndStrategy(any(), any()) }
        coVerify(exactly = 0) { wordPoolDao.insertPool(any()) }
        coVerify(exactly = 0) { wordPoolDao.insertMembers(any()) }
    }

    @Test
    fun `organize word note relation inserts fallback edge and promotes persisted relation without rebuilding pools`() = runTest {
        val wordDao = mockk<WordDao>()
        val wordEdgeDao = mockk<WordEdgeDao>()
        val wordPoolDao = mockk<WordPoolDao>(relaxed = true)
        val insertedEdge = slot<WordEdgeEntity>()
        val repository = repository(
            wordDao = wordDao,
            wordEdgeDao = wordEdgeDao,
            wordPoolDao = wordPoolDao
        )

        coEvery { wordDao.getWordById(10L) } returns wordWithDetails(10L, 1L, "adapt")
        coEvery { wordDao.getWordById(20L) } returns wordWithDetails(20L, 1L, "adopt")
        coEvery { wordEdgeDao.getEdgesBetweenWords(1L, 10L, 20L) } returnsMany listOf(
            emptyList(),
            listOf(
                WordEdgeEntity(
                    id = 8L,
                    wordIdA = 10L,
                    wordIdB = 20L,
                    edgeType = EdgeType.LEARNING_CONFUSABLE.dbValue,
                    dictionaryId = 1L,
                    status = "warning",
                    confidence = 0.63,
                    reason = "existing ai-reviewed reason",
                    evidenceSource = "manual-review"
                )
            )
        )
        coEvery { wordEdgeDao.insertEdgeIfAbsent(capture(insertedEdge)) } returns -1L
        coEvery { wordEdgeDao.updateEdgeStatus(any(), any(), any()) } returns Unit

        repository.organizeWordNoteRelation(1L, 10L, 20L)

        coVerify(exactly = 1) { wordEdgeDao.insertEdgeIfAbsent(any()) }
        coVerify(exactly = 1) { wordEdgeDao.updateEdgeStatus(8L, "warning", 1.0) }
        coVerify(exactly = 0) { wordEdgeDao.upsertEdge(any()) }
        assertEquals(10L, insertedEdge.captured.wordIdA)
        assertEquals(20L, insertedEdge.captured.wordIdB)
        assertEquals(EdgeType.FORM_SPELLING.dbValue, insertedEdge.captured.edgeType)
        assertEquals("support", insertedEdge.captured.status)
        assertEquals(1.0, insertedEdge.captured.confidence, 0.0)
        coVerify(exactly = 0) { wordPoolDao.deleteByDictionaryAndStrategy(any(), any()) }
        coVerify(exactly = 0) { wordPoolDao.insertPool(any()) }
        coVerify(exactly = 0) { wordPoolDao.insertMembers(any()) }
    }

    @Test
    fun `organize word note relation rejects words outside target dictionary`() = runTest {
        val wordDao = mockk<WordDao>()
        val wordEdgeDao = mockk<WordEdgeDao>(relaxed = true)
        val repository = repository(wordDao = wordDao, wordEdgeDao = wordEdgeDao)

        coEvery { wordDao.getWordById(10L) } returns wordWithDetails(10L, 1L, "adapt")
        coEvery { wordDao.getWordById(20L) } returns wordWithDetails(20L, 2L, "adopt")

        val error = try {
            repository.organizeWordNoteRelation(1L, 10L, 20L)
            throw AssertionError("Expected IllegalStateException")
        } catch (actual: IllegalStateException) {
            actual
        }

        assertEquals("便签关联的两个单词必须属于同一个词典", error.message)
        coVerify(exactly = 0) { wordEdgeDao.getEdgesBetweenWords(any(), any(), any()) }
        coVerify(exactly = 0) { wordEdgeDao.insertEdgeIfAbsent(any()) }
    }

    private fun repository(
        wordDao: WordDao = mockk(relaxed = true),
        wordEdgeDao: WordEdgeDao = mockk(relaxed = true),
        wordPoolDao: WordPoolDao = mockk(relaxed = true),
        edgeReviewer: EdgeReviewer = mockk(relaxed = true)
    ): WordPoolRepositoryImpl =
        WordPoolRepositoryImpl(
            db = mockk<AppDatabase>(relaxed = true),
            wordPoolDao = wordPoolDao,
            wordEdgeDao = wordEdgeDao,
            wordDao = wordDao,
            aiApiClientProvider = mockk<AiApiClientProvider>(relaxed = true),
            settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
            edgeReviewer = edgeReviewer,
            entryTypeClassifier = mockk<EntryTypeClassifier>(relaxed = true),
            liveMonitor = mockk<PoolBuildLiveMonitor>(relaxed = true)
        )

    private fun wordWithDetails(id: Long, dictionaryId: Long, spelling: String): WordWithDetails =
        WordWithDetails(
            word = WordEntity(
                id = id,
                dictionaryId = dictionaryId,
                spelling = spelling,
                normalizedSpelling = spelling.lowercase()
            ),
            synonyms = emptyList(),
            similarWords = emptyList(),
            cognates = emptyList()
        )
}
