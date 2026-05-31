package com.xty.englishhelper.domain.usecase.brainstorm

import com.xty.englishhelper.domain.model.BrainstormDailyGoal
import com.xty.englishhelper.domain.repository.BrainstormRepository
import com.xty.englishhelper.domain.utils.DateUtils
import javax.inject.Inject

class GetBrainstormDailyGoalUseCase @Inject constructor(
    private val brainstormRepository: BrainstormRepository
) {
    suspend operator fun invoke(): BrainstormDailyGoal {
        val today = DateUtils.todayKey()
        val existing = brainstormRepository.getDailyGoal(today)
        if (existing != null) return existing

        val lastTarget = brainstormRepository.getLastDailyTarget()
        val goal = BrainstormDailyGoal(
            date = today,
            targetCount = lastTarget,
            totalLearned = 0,
            dueWordsLearned = 0,
            newWordsLearned = 0,
            isCompleted = false,
            continuedAfterGoal = false
        )
        brainstormRepository.saveDailyGoal(goal)
        return goal
    }
}
