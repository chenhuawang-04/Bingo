package com.xty.englishhelper.domain.usecase.study

import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.study.Rating
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StudyUseCasesTest {

    @Test
    fun `reviewWord stores brainstorm state independently from normal`() = runTest {
        val repository = FakeStudyRepository()
        val useCase = ReviewWordUseCase(repository)

        useCase(wordId = 42L, rating = Rating.Good, studyMode = StudyMode.NORMAL)
        useCase(wordId = 42L, rating = Rating.Good, studyMode = StudyMode.BRAINSTORM)

        val normal = repository.getStudyState(42L, StudyMode.NORMAL)
        val brainstorm = repository.getStudyState(42L, StudyMode.BRAINSTORM)

        assertNotNull(normal)
        assertNotNull(brainstorm)
        assertEquals(2, repository.states.size)
        assertEquals(StudyMode.NORMAL, normal?.studyMode)
        assertEquals(StudyMode.BRAINSTORM, brainstorm?.studyMode)
        assertEquals(42L, normal?.wordId)
        assertEquals(42L, brainstorm?.wordId)
    }

    private class FakeStudyRepository : StudyRepository {
        val states = linkedMapOf<Pair<Long, StudyMode>, WordStudyState>()

        override suspend fun getStudyState(wordId: Long, studyMode: StudyMode): WordStudyState? {
            return states[wordId to studyMode]
        }

        override suspend fun upsertStudyState(state: WordStudyState) {
            states[state.wordId to state.studyMode] = state
        }

        override suspend fun getDueWords(
            unitIds: List<Long>,
            now: Long,
            studyMode: StudyMode
        ): List<WordDetails> = emptyList()

        override suspend fun getNewWords(
            unitIds: List<Long>,
            studyMode: StudyMode
        ): List<WordDetails> = emptyList()

        override suspend fun countDueWords(unitId: Long, now: Long, studyMode: StudyMode): Int = 0

        override suspend fun countNewWords(unitId: Long, studyMode: StudyMode): Int = 0

        override suspend fun getStudyStatesForDictionary(
            dictionaryId: Long,
            studyMode: StudyMode?
        ): List<WordStudyState> = emptyList()

        override suspend fun countAllDueWords(now: Long): Int = 0

        override suspend fun countReviewedToday(todayStart: Long, now: Long): Int = 0

        override suspend fun getAllActiveStudyStates(): List<WordStudyState> = states.values.toList()
    }
}
