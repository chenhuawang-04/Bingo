package com.xty.englishhelper.domain.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanProgressRulesTest {

    @Test
    fun `isCompleted returns false when done less than target`() {
        assertFalse(PlanProgressRules.isCompleted(doneCount = 1, targetCount = 3))
    }

    @Test
    fun `isCompleted returns true when done reaches target`() {
        assertTrue(PlanProgressRules.isCompleted(doneCount = 3, targetCount = 3))
    }

    @Test
    fun `mark completed raises done count to target floor`() {
        assertEquals(3, PlanProgressRules.doneCountWhenMarkCompleted(existingDoneCount = 1, targetCount = 3))
    }

    @Test
    fun `mark incomplete keeps done below target`() {
        assertEquals(2, PlanProgressRules.doneCountWhenMarkIncomplete(existingDoneCount = 4, targetCount = 3))
        assertEquals(0, PlanProgressRules.doneCountWhenMarkIncomplete(existingDoneCount = 0, targetCount = 3))
    }
}
