package com.xty.englishhelper.domain.usecase.study

import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.util.EbbinghausIntervals
import javax.inject.Inject

class GetStudyStateUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(wordId: Long): WordStudyState? =
        repository.getStudyState(wordId)
}

class MarkKnownUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(state: WordStudyState): WordStudyState {
        val newEaseLevel = state.easeLevel + 1
        val newRemaining = state.remainingReviews - 1
        val now = System.currentTimeMillis()
        val interval = EbbinghausIntervals.getInterval(newEaseLevel)
        val updated = state.copy(
            easeLevel = newEaseLevel,
            remainingReviews = maxOf(0, newRemaining),
            nextReviewAt = now + interval,
            lastReviewedAt = now
        )
        repository.upsertStudyState(updated)
        return updated
    }
}

class MarkUnknownUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(state: WordStudyState): WordStudyState {
        val now = System.currentTimeMillis()
        val updated = state.copy(
            easeLevel = maxOf(0, state.easeLevel - 1),
            nextReviewAt = 0,
            lastReviewedAt = now
        )
        repository.upsertStudyState(updated)
        return updated
    }
}

class InitStudyStateUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(wordId: Long, repeatCount: Int): WordStudyState {
        val state = WordStudyState(
            wordId = wordId,
            remainingReviews = repeatCount,
            easeLevel = 0,
            nextReviewAt = 0,
            lastReviewedAt = 0
        )
        repository.upsertStudyState(state)
        return state
    }
}

class GetDueWordsUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(unitIds: List<Long>): List<WordDetails> =
        repository.getDueWords(unitIds, System.currentTimeMillis())
}

class GetNewWordsUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(unitIds: List<Long>): List<WordDetails> =
        repository.getNewWords(unitIds)
}

class CountDueWordsUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(unitId: Long): Int =
        repository.countDueWords(unitId, System.currentTimeMillis())
}

class CountNewWordsUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(unitId: Long): Int =
        repository.countNewWords(unitId)
}
