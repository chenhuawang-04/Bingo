package com.xty.englishhelper.data.sync

import com.xty.englishhelper.domain.model.WordDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryWordUpdatePlannerTest {

    private val planner = DictionaryWordUpdatePlanner()

    @Test
    fun `plan allows chained renames by staging temporary normalized spellings`() {
        val localWords = listOf(
            word(id = 1, spelling = "alpha"),
            word(id = 2, spelling = "beta")
        )
        val candidates = listOf(
            DictionaryWordUpdatePlanner.Candidate(
                existingWord = localWords[0],
                finalWord = localWords[0].copy(
                    spelling = "beta",
                    normalizedSpelling = "beta"
                )
            ),
            DictionaryWordUpdatePlanner.Candidate(
                existingWord = localWords[1],
                finalWord = localWords[1].copy(
                    spelling = "gamma",
                    normalizedSpelling = "gamma"
                )
            )
        )

        val plan = planner.plan(localWords, candidates)

        assertEquals(setOf(1L, 2L), plan.temporaryRenameIds)
        assertEquals(listOf("beta", "gamma"), plan.finalUpdates.map { it.normalizedSpelling })
    }

    @Test
    fun `plan rejects rename colliding with unaffected local word`() {
        val localWords = listOf(
            word(id = 1, spelling = "alpha"),
            word(id = 2, spelling = "beta"),
            word(id = 3, spelling = "gamma")
        )
        val candidates = listOf(
            DictionaryWordUpdatePlanner.Candidate(
                existingWord = localWords[0],
                finalWord = localWords[0].copy(
                    spelling = "gamma",
                    normalizedSpelling = "gamma"
                )
            )
        )

        val error = runCatching { planner.plan(localWords, candidates) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("同名词条") == true)
    }

    @Test
    fun `plan rejects duplicate final normalized spellings`() {
        val localWords = listOf(
            word(id = 1, spelling = "alpha"),
            word(id = 2, spelling = "beta")
        )
        val candidates = listOf(
            DictionaryWordUpdatePlanner.Candidate(
                existingWord = localWords[0],
                finalWord = localWords[0].copy(
                    spelling = "gamma",
                    normalizedSpelling = "gamma"
                )
            ),
            DictionaryWordUpdatePlanner.Candidate(
                existingWord = localWords[1],
                finalWord = localWords[1].copy(
                    spelling = "gamma",
                    normalizedSpelling = "gamma"
                )
            )
        )

        val error = runCatching { planner.plan(localWords, candidates) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("同一个拼写") == true)
    }

    private fun word(id: Long, spelling: String): WordDetails {
        return WordDetails(
            id = id,
            dictionaryId = 1L,
            spelling = spelling,
            normalizedSpelling = spelling
        )
    }
}
