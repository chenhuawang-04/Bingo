package com.xty.englishhelper.domain.plan

object PlanProgressRules {

    fun normalizedTarget(targetCount: Int): Int = targetCount.coerceAtLeast(1)

    fun isCompleted(doneCount: Int, targetCount: Int): Boolean =
        doneCount.coerceAtLeast(0) >= normalizedTarget(targetCount)

    fun doneCountWhenMarkCompleted(existingDoneCount: Int, targetCount: Int): Int =
        existingDoneCount.coerceAtLeast(0).coerceAtLeast(normalizedTarget(targetCount))

    fun doneCountWhenMarkIncomplete(existingDoneCount: Int, targetCount: Int): Int {
        val safeTarget = normalizedTarget(targetCount)
        return existingDoneCount.coerceAtLeast(0).coerceAtMost(safeTarget - 1)
    }
}
