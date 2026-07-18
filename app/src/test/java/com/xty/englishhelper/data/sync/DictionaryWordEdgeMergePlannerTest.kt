package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.json.WordEdgeJsonModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryWordEdgeMergePlannerTest {

    private val planner = DictionaryWordEdgeMergePlanner()

    @Test
    fun `cloud only edge is applied`() {
        val cloud = edge(updatedAt = 20L)

        val plan = planner.plan(emptyList(), listOf(cloud))

        assertEquals(listOf(cloud), plan.cloudEdgesToApply)
    }

    @Test
    fun `newer confidence zero edge propagates remove verdict`() {
        val local = edge(confidence = 0.8, updatedAt = 10L)
        val cloud = edge(confidence = 0.0, updatedAt = 20L)

        val plan = planner.plan(listOf(local), listOf(cloud))

        assertEquals(0.0, plan.cloudEdgesToApply.single().confidence, 0.0)
    }

    @Test
    fun `newer identical edge is applied to preserve its sync timestamp`() {
        val local = edge(updatedAt = 10L)
        val cloud = edge(updatedAt = 20L)

        val plan = planner.plan(listOf(local), listOf(cloud))

        assertEquals(listOf(cloud), plan.cloudEdgesToApply)
    }

    @Test
    fun `equal timestamp conflict is rejected`() {
        val error = runCatching {
            planner.plan(
                listOf(edge(confidence = 0.8, updatedAt = 10L)),
                listOf(edge(confidence = 0.4, updatedAt = 10L))
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `duplicate normalized edge keys are rejected`() {
        val error = runCatching {
            planner.plan(
                localEdges = emptyList(),
                cloudEdges = listOf(
                    edge(updatedAt = 10L),
                    edge(updatedAt = 20L).copy(wordUidA = "b", wordUidB = "a")
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `older user note remains authoritative over newer generated evidence`() {
        val local = edge(updatedAt = 10L).copy(evidenceSource = "user_note", confidence = 1.0)
        val cloud = edge(updatedAt = 20L).copy(evidenceSource = "balanced_local", confidence = 0.4)

        val plan = planner.plan(listOf(local), listOf(cloud))

        assertTrue(plan.cloudEdgesToApply.isEmpty())
    }

    @Test
    fun `cloud user note replaces newer generated evidence`() {
        val local = edge(updatedAt = 20L).copy(evidenceSource = "balanced_local", confidence = 0.4)
        val cloud = edge(updatedAt = 10L).copy(evidenceSource = "user_note", confidence = 1.0)

        val plan = planner.plan(listOf(local), listOf(cloud))

        assertEquals(listOf(cloud), plan.cloudEdgesToApply)
    }

    private fun edge(
        confidence: Double = 0.8,
        updatedAt: Long
    ) = WordEdgeJsonModel(
        wordUidA = "a",
        wordUidB = "b",
        edgeType = "SEMANTIC_SYNONYM",
        confidence = confidence,
        updatedAt = updatedAt
    )
}
