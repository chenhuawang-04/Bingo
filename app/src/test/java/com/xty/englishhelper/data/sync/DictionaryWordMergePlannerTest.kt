package com.xty.englishhelper.data.sync

import com.xty.englishhelper.data.json.MeaningJsonModel
import com.xty.englishhelper.data.json.WordJsonModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryWordMergePlannerTest {

    private val planner = DictionaryWordMergePlanner()

    @Test
    fun `plan imports cloud-only words`() {
        val plan = planner.plan(
            localWords = listOf(word(uid = "local-1", spelling = "apple")),
            cloudWords = listOf(
                word(uid = "local-1", spelling = "apple"),
                word(uid = "cloud-2", spelling = "banana")
            )
        )

        assertEquals(listOf("cloud-2"), plan.cloudOnlyWords.map { it.wordUid })
        assertTrue(plan.cloudUpdates.isEmpty())
    }

    @Test
    fun `plan updates newer cloud words by timestamp`() {
        val plan = planner.plan(
            localWords = listOf(word(uid = "same", spelling = "apple", updatedAt = 100)),
            cloudWords = listOf(word(uid = "same", spelling = "apple", updatedAt = 200, phonetic = "/a/"))
        )

        assertTrue(plan.cloudOnlyWords.isEmpty())
        assertEquals(1, plan.cloudUpdates.size)
        assertEquals("same", plan.cloudUpdates.single().localWordUid)
    }

    @Test
    fun `plan matches legacy blank uid words by normalized spelling`() {
        val plan = planner.plan(
            localWords = listOf(word(uid = "", spelling = "Apple", updatedAt = 0)),
            cloudWords = listOf(
                word(
                    uid = "",
                    spelling = "apple",
                    updatedAt = 0,
                    meanings = listOf(MeaningJsonModel(pos = "n.", definition = "fruit"))
                )
            )
        )

        assertTrue(plan.cloudOnlyWords.isEmpty())
        assertEquals(1, plan.cloudUpdates.size)
        assertEquals("apple", plan.cloudUpdates.single().localNormalizedSpelling)
    }

    @Test
    fun `plan prefers richer cloud payload when timestamps are absent`() {
        val plan = planner.plan(
            localWords = listOf(word(uid = "same", spelling = "apple")),
            cloudWords = listOf(
                word(
                    uid = "same",
                    spelling = "apple",
                    meanings = listOf(MeaningJsonModel(pos = "n.", definition = "fruit")),
                    rootExplanation = "from Latin"
                )
            )
        )

        assertEquals(1, plan.cloudUpdates.size)
        assertEquals("same", plan.cloudUpdates.single().cloudWord.wordUid)
    }

    private fun word(
        uid: String,
        spelling: String,
        updatedAt: Long = 0,
        phonetic: String = "",
        meanings: List<MeaningJsonModel> = emptyList(),
        rootExplanation: String = ""
    ): WordJsonModel {
        return WordJsonModel(
            spelling = spelling,
            phonetic = phonetic,
            wordUid = uid,
            updatedAt = updatedAt,
            meanings = meanings,
            rootExplanation = rootExplanation
        )
    }
}
