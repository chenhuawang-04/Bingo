package com.xty.englishhelper.domain.pool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityFirstPoolPlannerTest {

    @Test
    fun `sixteen node chain is fully covered by connected pools`() {
        val edges = (1L until 16L).map { QualityPoolEdge(it, it + 1) }

        val plan = QualityFirstPoolPlanner.plan(edges)

        assertEquals((1L..16L).toSet(), plan.coveredWordIds)
        assertTrue(plan.pools.all { it.size in 2..15 })
        assertTrue(plan.pools.all { QualityFirstPoolPlanner.isConnectedPool(it, edges) })
    }

    @Test
    fun `large star reuses its center instead of dropping the final leaf`() {
        val edges = (2L..16L).map { QualityPoolEdge(1L, it) }

        val plan = QualityFirstPoolPlanner.plan(edges)

        assertEquals((1L..16L).toSet(), plan.coveredWordIds)
        assertEquals(2, plan.pools.size)
        assertTrue(plan.pools.all { 1L in it })
        assertTrue(plan.pools.all { QualityFirstPoolPlanner.isConnectedPool(it, edges) })
    }

    @Test
    fun `thirty one node chain never produces a singleton tail`() {
        val edges = (1L until 31L).map { QualityPoolEdge(it, it + 1) }

        val plan = QualityFirstPoolPlanner.plan(edges)

        assertEquals((1L..31L).toSet(), plan.coveredWordIds)
        assertTrue(plan.pools.none { it.size == 1 })
        assertTrue(plan.pools.all { QualityFirstPoolPlanner.isConnectedPool(it, edges) })
    }
}
