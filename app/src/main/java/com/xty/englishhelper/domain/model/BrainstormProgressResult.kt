package com.xty.englishhelper.domain.model

sealed class BrainstormProgressResult {
    data object NotStarted : BrainstormProgressResult()
    data class InProgress(val learned: Int, val target: Int) : BrainstormProgressResult()
    data class GoalReached(val learned: Int, val target: Int) : BrainstormProgressResult()
}
