package com.xty.englishhelper.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleAdvancedScoringTargetsTest {
    @Test
    fun `advanced target keys are unique and cover every paper profile slot`() {
        val targets = ArticleAdvancedScoringTargets.all
        assertEquals(targets.size, targets.map { it.key }.distinct().size)

        ExamPaperProfile.entries.forEach { profile ->
            ArticleAdvancedScoringTargets.selectableSpecialTypes.forEach { specialType ->
                val blueprint = ExamPaperBlueprint.forYear(2026, profile, specialType)
                blueprint.slots.forEach { slot ->
                    assertNotNull(
                        "Missing advanced scoring target for ${profile.name}/${slot.key}",
                        ArticleAdvancedScoringTargets.targetFor(slot)
                    )
                }
            }
        }
    }

    @Test
    fun `selectable special types never include fixed paper sections`() {
        val specialTypes = ArticleAdvancedScoringTargets.selectableSpecialTypes
        assertTrue(QuestionType.CLOZE !in specialTypes)
        assertTrue(QuestionType.READING_COMPREHENSION !in specialTypes)
        assertTrue(QuestionType.TRANSLATION !in specialTypes)
        assertTrue(QuestionType.WRITING !in specialTypes)
    }
}
