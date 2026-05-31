package com.xty.englishhelper.domain.repository

import com.xty.englishhelper.domain.model.BrainstormDailyGoal

interface BrainstormRepository {
    suspend fun getDailyGoal(date: String): BrainstormDailyGoal?
    suspend fun saveDailyGoal(goal: BrainstormDailyGoal)
    suspend fun updateProgress(date: String, total: Int, due: Int, newW: Int)
    suspend fun markCompleted(date: String)
    suspend fun markCompletedAndContinued(date: String)
    suspend fun getLastDailyTarget(): Int
    suspend fun updateLastDailyTarget(target: Int)
}
