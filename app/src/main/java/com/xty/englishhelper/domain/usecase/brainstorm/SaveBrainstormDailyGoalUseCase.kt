package com.xty.englishhelper.domain.usecase.brainstorm

import com.xty.englishhelper.domain.model.BrainstormDailyGoal
import com.xty.englishhelper.domain.repository.BrainstormRepository
import com.xty.englishhelper.domain.utils.DateUtils
import javax.inject.Inject

class SaveBrainstormDailyGoalUseCase @Inject constructor(
    private val brainstormRepository: BrainstormRepository
) {
    suspend operator fun invoke(targetCount: Int) {
        val today = DateUtils.todayKey()
        val existing = brainstormRepository.getDailyGoal(today)
        val goal = (existing ?: BrainstormDailyGoal(date = today)).copy(
            targetCount = targetCount
        )
        brainstormRepository.saveDailyGoal(goal)
        brainstormRepository.updateLastDailyTarget(targetCount)
    }
}
