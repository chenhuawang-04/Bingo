package com.xty.englishhelper.domain.usecase.study

import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.study.CardState
import com.xty.englishhelper.domain.study.FsrsEngine
import com.xty.englishhelper.domain.study.Rating
import com.xty.englishhelper.domain.study.SchedulingResult
import javax.inject.Inject

class GetStudyStateUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(wordId: Long): WordStudyState? =
        repository.getStudyState(wordId)
}

class ReviewWordUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    private val engine = FsrsEngine()

    suspend operator fun invoke(wordId: Long, rating: Rating): SchedulingResult {
        val existing = repository.getStudyState(wordId)
        val now = System.currentTimeMillis()

        val result = if (existing == null) {
            engine.reviewNew(rating, now)
        } else {
            engine.review(
                state = CardState.fromValue(existing.state),
                step = existing.step,
                stability = existing.stability,
                difficulty = existing.difficulty,
                lastReviewAt = existing.lastReviewAt,
                reps = existing.reps,
                lapses = existing.lapses,
                rating = rating,
                now = now
            )
        }

        repository.upsertStudyState(
            WordStudyState(
                wordId = wordId,
                state = result.state.value,
                step = result.step,
                stability = result.stability,
                difficulty = result.difficulty,
                due = result.due,
                lastReviewAt = result.lastReviewAt,
                reps = result.reps,
                lapses = result.lapses
            )
        )

        return result
    }
}

class PreviewIntervalsUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    private val engine = FsrsEngine()

    suspend operator fun invoke(wordId: Long): Map<Rating, Long> {
        val existing = repository.getStudyState(wordId)
        val now = System.currentTimeMillis()

        return if (existing == null) {
            Rating.entries.associateWith { rating ->
                engine.reviewNew(rating, now).scheduledInterval
            }
        } else {
            engine.previewIntervals(
                state = CardState.fromValue(existing.state),
                step = existing.step,
                stability = existing.stability,
                difficulty = existing.difficulty,
                lastReviewAt = existing.lastReviewAt,
                reps = existing.reps,
                lapses = existing.lapses,
                now = now
            )
        }
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
