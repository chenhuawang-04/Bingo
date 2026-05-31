package com.xty.englishhelper.domain.usecase.brainstorm

import com.xty.englishhelper.domain.model.BrainstormProgressResult
import com.xty.englishhelper.domain.repository.BrainstormRepository
import com.xty.englishhelper.domain.utils.DateUtils
import javax.inject.Inject

class UpdateBrainstormProgressUseCase @Inject constructor(
    private val brainstormRepository: BrainstormRepository
) {
    /**
     * 调用时机：每背完一个词（非 Again 评分）
     * @param isDueWord true = 复习词, false = 新词
     */
    suspend fun onWordLearned(isDueWord: Boolean): BrainstormProgressResult {
        val today = DateUtils.todayKey()
        val goal = brainstormRepository.getDailyGoal(today)
            ?: return BrainstormProgressResult.NotStarted

        val newDue = if (isDueWord) goal.dueWordsLearned + 1 else goal.dueWordsLearned
        val newNewW = if (!isDueWord) goal.newWordsLearned + 1 else goal.newWordsLearned
        val newTotal = newDue + newNewW

        brainstormRepository.updateProgress(today, newTotal, newDue, newNewW)

        return if (!goal.isCompleted && newTotal >= goal.targetCount) {
            BrainstormProgressResult.GoalReached(newTotal, goal.targetCount)
        } else {
            BrainstormProgressResult.InProgress(newTotal, goal.targetCount)
        }
    }

    suspend fun onContinueAfterGoal() {
        val today = DateUtils.todayKey()
        brainstormRepository.markCompletedAndContinued(today)
    }

    suspend fun onExitAfterGoal() {
        val today = DateUtils.todayKey()
        brainstormRepository.markCompleted(today)
    }
}
