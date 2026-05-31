package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.BrainstormDao
import com.xty.englishhelper.data.local.entity.BrainstormDailyGoalEntity
import com.xty.englishhelper.data.local.entity.BrainstormSettingsEntity
import com.xty.englishhelper.domain.model.BrainstormDailyGoal
import com.xty.englishhelper.domain.repository.BrainstormRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrainstormRepositoryImpl @Inject constructor(
    private val brainstormDao: BrainstormDao
) : BrainstormRepository {

    override suspend fun getDailyGoal(date: String): BrainstormDailyGoal? {
        return brainstormDao.getDailyGoal(date)?.toDomain()
    }

    override suspend fun saveDailyGoal(goal: BrainstormDailyGoal) {
        brainstormDao.upsertDailyGoal(goal.toEntity())
    }

    override suspend fun updateProgress(date: String, total: Int, due: Int, newW: Int) {
        brainstormDao.updateProgress(date, total, due, newW, System.currentTimeMillis())
    }

    override suspend fun markCompleted(date: String) {
        brainstormDao.markCompleted(date, System.currentTimeMillis())
    }

    override suspend fun markCompletedAndContinued(date: String) {
        brainstormDao.markCompletedAndContinued(date, System.currentTimeMillis())
    }

    override suspend fun getLastDailyTarget(): Int {
        return brainstormDao.getSettings()?.lastDailyTarget ?: 200
    }

    override suspend fun updateLastDailyTarget(target: Int) {
        brainstormDao.upsertSettings(
            BrainstormSettingsEntity(
                id = 1,
                lastDailyTarget = target,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun BrainstormDailyGoalEntity.toDomain(): BrainstormDailyGoal {
        return BrainstormDailyGoal(
            date = date,
            targetCount = targetCount,
            totalLearned = totalLearned,
            dueWordsLearned = dueWordsLearned,
            newWordsLearned = newWordsLearned,
            isCompleted = isCompleted,
            continuedAfterGoal = continuedAfterGoal
        )
    }

    private fun BrainstormDailyGoal.toEntity(): BrainstormDailyGoalEntity {
        return BrainstormDailyGoalEntity(
            date = date,
            targetCount = targetCount,
            totalLearned = totalLearned,
            dueWordsLearned = dueWordsLearned,
            newWordsLearned = newWordsLearned,
            isCompleted = isCompleted,
            continuedAfterGoal = continuedAfterGoal
        )
    }
}
