package com.xty.englishhelper.domain.plan

import com.xty.englishhelper.domain.model.PlanAutoSource
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.repository.PlanRepository
import java.util.Calendar
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
        val today = todayKey()
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
        val today = todayKey()
        planRepository.consumeAutoProgress(
            source = PlanAutoSource.ARTICLE_OPEN,
            eventKey = "article_open:$today:$articleId",
            delta = 1
        )
    }

    suspend fun onArticleTtsFinished(articleId: Long) {
        val today = todayKey()
        planRepository.consumeAutoProgress(
            source = PlanAutoSource.ARTICLE_TTS_FINISHED,
            eventKey = "article_tts_finish:$today:$articleId",
            delta = 1
        )
    }

    suspend fun onQuestionSubmitted(groupId: Long) {
        val today = todayKey()
        planRepository.consumeAutoProgress(
            source = PlanAutoSource.QUESTION_SUBMIT,
            eventKey = "question_submit:$today:$groupId",
            delta = 1
        )
    }

    private fun todayKey(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d-%02d-%02d", year, month, day)
    }
}
