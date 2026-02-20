package com.xty.englishhelper.util

object EbbinghausIntervals {

    private val INTERVALS = longArrayOf(
        0L,                         // Level 0 → immediate (same session)
        5L * 60 * 1000,             // Level 1 → 5 minutes
        30L * 60 * 1000,            // Level 2 → 30 minutes
        12L * 60 * 60 * 1000,       // Level 3 → 12 hours
        1L * 24 * 60 * 60 * 1000,   // Level 4 → 1 day
        2L * 24 * 60 * 60 * 1000,   // Level 5 → 2 days
        4L * 24 * 60 * 60 * 1000,   // Level 6 → 4 days
        7L * 24 * 60 * 60 * 1000,   // Level 7 → 7 days
        15L * 24 * 60 * 60 * 1000,  // Level 8 → 15 days
        30L * 24 * 60 * 60 * 1000   // Level 9 → 30 days
    )

    fun getInterval(easeLevel: Int): Long {
        val index = easeLevel.coerceIn(0, INTERVALS.lastIndex)
        return INTERVALS[index]
    }

    val MAX_LEVEL = INTERVALS.lastIndex
}
