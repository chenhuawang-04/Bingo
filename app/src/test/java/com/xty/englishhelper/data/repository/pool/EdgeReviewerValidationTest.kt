package com.xty.englishhelper.data.repository.pool

import com.xty.englishhelper.data.local.entity.WordEdgeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeReviewerValidationTest {
    private val batch = listOf(
        edge(1L, 1L, 2L),
        edge(2L, 2L, 3L)
    )

    @Test(expected = RetryableEdgeException::class)
    fun `review response must cover every edge in the batch`() {
        parseReviewUpdates("""[{"i":0,"verdict":"keep"}]""", batch)
    }

    @Test(expected = RetryableEdgeException::class)
    fun `review response rejects unknown verdict`() {
        parseReviewUpdates(
            """[{"i":0,"verdict":"keep"},{"i":1,"verdict":"ignore"}]""",
            batch
        )
    }

    @Test
    fun `remove verdict preserves row and sets confidence to zero`() {
        val updates = parseReviewUpdates(
            """[{"i":0,"verdict":"keep"},{"i":1,"verdict":"remove"}]""",
            batch
        )

        assertEquals(1, updates.size)
        assertEquals(2L, updates.single().edgeId)
        assertEquals(0.0, updates.single().newConfidence, 0.0)
    }

    private fun edge(id: Long, a: Long, b: Long) = WordEdgeEntity(
        id = id,
        wordIdA = a,
        wordIdB = b,
        edgeType = "SEMANTIC_OVERLAP",
        dictionaryId = 1L,
        status = "support",
        confidence = 0.8
    )
}
