package com.xty.englishhelper.domain.usecase.study

import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.StudyMode
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
    suspend operator fun invoke(
        wordId: Long,
        studyMode: StudyMode = StudyMode.NORMAL
    ): WordStudyState? = repository.getStudyState(wordId, studyMode)
}

class ReviewWordUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    private val engine = FsrsEngine()

    suspend operator fun invoke(
        wordId: Long,
        rating: Rating,
        studyMode: StudyMode = StudyMode.NORMAL
    ): SchedulingResult {
        val existing = repository.getStudyState(wordId, studyMode)
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
                studyMode = studyMode,
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
    private val engine = FsrsEngine(enableFuzz = false)

    suspend operator fun invoke(
        wordId: Long,
        studyMode: StudyMode = StudyMode.NORMAL
    ): Map<Rating, Long> {
        val existing = repository.getStudyState(wordId, studyMode)
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
    suspend operator fun invoke(
        unitIds: List<Long>,
        studyMode: StudyMode = StudyMode.NORMAL
    ): List<WordDetails> = repository.getDueWords(unitIds, System.currentTimeMillis(), studyMode)
}

class GetNewWordsUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(
        unitIds: List<Long>,
        studyMode: StudyMode = StudyMode.NORMAL
    ): List<WordDetails> = repository.getNewWords(unitIds, studyMode)
}

class CountDueWordsUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(
        unitId: Long,
        studyMode: StudyMode = StudyMode.NORMAL
    ): Int = repository.countDueWords(unitId, System.currentTimeMillis(), studyMode)
}

class CountNewWordsUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    suspend operator fun invoke(
        unitId: Long,
        studyMode: StudyMode = StudyMode.NORMAL
    ): Int = repository.countNewWords(unitId, studyMode)
}
