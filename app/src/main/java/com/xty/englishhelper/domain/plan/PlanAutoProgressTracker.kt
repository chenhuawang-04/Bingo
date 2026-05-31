package com.xty.englishhelper.domain.plan

import com.xty.englishhelper.domain.model.PlanAutoSource
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.repository.PlanRepository
import com.xty.englishhelper.domain.utils.DateUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanAutoProgressTracker @Inject constructor(
    private val planRepository: PlanRepository
) {

    suspend fun onStudySessionCompleted(
        unitIds: List<Long>,
        studyMode: StudyMode,
        hasDueWords: Boolean,
        hasNewWords: Boolean
    ) {
        val today = DateUtils.todayKey()
        val unitKey = unitIds.sorted().joinToString(",")
        if (hasDueWords) {
            planRepository.consumeAutoProgress(
                source = PlanAutoSource.STUDY_DUE_SESSION,
                eventKey = "study_due:$today:${studyMode.name}:$unitKey",
                delta = 1
            )
        }
        if (hasNewWords) {
            planRepository.consumeAutoProgress(
                source = PlanAutoSource.STUDY_NEW_SESSION,
                eventKey = "study_new:$today:${studyMode.name}:$unitKey",
                delta = 1
            )
        }
    }

    suspend fun onArticleOpened(articleId: Long) {
        val today = DateUtils.todayKey()
        planRepository.consumeAutoProgress(
            source = PlanAutoSource.ARTICLE_OPEN,
            eventKey = "article_open:$today:$articleId",
            delta = 1
        )
    }

    suspend fun onArticleTtsFinished(articleId: Long) {
        val today = DateUtils.todayKey()
        planRepository.consumeAutoProgress(
            source = PlanAutoSource.ARTICLE_TTS_FINISHED,
            eventKey = "article_tts_finish:$today:$articleId",
            delta = 1
        )
    }

    suspend fun onQuestionSubmitted(groupId: Long) {
        val today = DateUtils.todayKey()
        planRepository.consumeAutoProgress(
            source = PlanAutoSource.QUESTION_SUBMIT,
            eventKey = "question_submit:$today:$groupId",
            delta = 1
        )
    }

    suspend fun onBrainstormSessionCompleted(
        unitIds: List<Long>,
        totalWordsLearned: Int
    ) {
        val today = DateUtils.todayKey()
        val unitKey = unitIds.sorted().joinToString(",")
        val eventKey = "brainstorm:$today:$unitKey"

        planRepository.consumeAutoProgress(
            source = PlanAutoSource.BRAINSTORM_SESSION,
            eventKey = eventKey,
            delta = totalWordsLearned
        )
    }
}
