package com.xty.englishhelper.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xty.englishhelper.data.local.entity.DictionaryEntity
import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import com.xty.englishhelper.data.local.entity.WordEntity
import com.xty.englishhelper.domain.model.EdgeType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordEdgeDaoEffectiveGraphTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun effectiveGraphExcludesConfidenceZeroAndOptionalEdges() = runBlocking {
        val dictionaryId = db.dictionaryDao().insert(DictionaryEntity(name = "test"))
        val firstWordId = db.wordDao().insertWord(
            WordEntity(dictionaryId = dictionaryId, spelling = "adapt", normalizedSpelling = "adapt")
        )
        val secondWordId = db.wordDao().insertWord(
            WordEntity(dictionaryId = dictionaryId, spelling = "adopt", normalizedSpelling = "adopt")
        )
        val thirdWordId = db.wordDao().insertWord(
            WordEntity(dictionaryId = dictionaryId, spelling = "adjust", normalizedSpelling = "adjust")
        )
        db.wordEdgeDao().insertEdges(
            listOf(
                WordEdgeEntity(
                    wordIdA = firstWordId,
                    wordIdB = secondWordId,
                    edgeType = EdgeType.FORM_SPELLING.dbValue,
                    dictionaryId = dictionaryId,
                    confidence = 0.0,
                    status = "support"
                ),
                WordEdgeEntity(
                    wordIdA = firstWordId,
                    wordIdB = thirdWordId,
                    edgeType = EdgeType.LEARNING_CONFUSABLE.dbValue,
                    dictionaryId = dictionaryId,
                    confidence = 0.9,
                    status = "optional"
                ),
                WordEdgeEntity(
                    wordIdA = secondWordId,
                    wordIdB = thirdWordId,
                    edgeType = EdgeType.SEMANTIC_OVERLAP.dbValue,
                    dictionaryId = dictionaryId,
                    confidence = 0.9,
                    status = "support"
                ),
                WordEdgeEntity(
                    wordIdA = firstWordId,
                    wordIdB = firstWordId,
                    edgeType = EdgeType.FAMILY_INFLECTION.dbValue,
                    dictionaryId = dictionaryId,
                    confidence = 0.9,
                    status = "support"
                )
            )
        )

        val edges = db.wordEdgeDao().getEffectiveGraphEdgesPage(
            dictionaryId = dictionaryId,
            lastId = 0L,
            minConfidence = 0.3,
            excludedStatus = "optional",
            limit = 100
        )

        assertEquals(1, edges.size)
        assertEquals(EdgeType.SEMANTIC_OVERLAP.dbValue, edges.single().edgeType)

        val removable = db.wordEdgeDao()
            .getEdgesBetweenWords(dictionaryId, secondWordId, thirdWordId)
            .single { it.edgeType == EdgeType.SEMANTIC_OVERLAP.dbValue }
        db.wordEdgeDao().updateEdgeStatus(removable.id, removable.status, 0.0)
        val removedRow = db.wordEdgeDao()
            .getEdgesBetweenWords(dictionaryId, secondWordId, thirdWordId)
            .single { it.edgeType == EdgeType.SEMANTIC_OVERLAP.dbValue }
        assertEquals(0.0, removedRow.confidence, 0.0)

        db.wordEdgeDao().insertEdgeIfAbsent(
            WordEdgeEntity(
                wordIdA = firstWordId,
                wordIdB = secondWordId,
                edgeType = EdgeType.FORM_PRONUNCIATION.dbValue,
                dictionaryId = dictionaryId,
                confidence = 1.0,
                status = "support",
                evidenceSource = "user_note"
            )
        )
        val userEdge = db.wordEdgeDao()
            .getEdgesBetweenWords(dictionaryId, firstWordId, secondWordId)
            .single { it.edgeType == EdgeType.FORM_PRONUNCIATION.dbValue }
        db.wordEdgeDao().updateEdgeStatus(userEdge.id, "optional", 0.0)
        val preservedUserEdge = db.wordEdgeDao()
            .getEdgesBetweenWords(dictionaryId, firstWordId, secondWordId)
            .single { it.edgeType == EdgeType.FORM_PRONUNCIATION.dbValue }
        assertEquals("support", preservedUserEdge.status)
        assertEquals(1.0, preservedUserEdge.confidence, 0.0)
    }
}
