package com.xty.englishhelper.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamPaperBlueprintTest {
    @Test
    fun englishOneBlueprint_matchesPostgraduateExamShape() {
        val blueprint = ExamPaperBlueprint.forYear(2026, ExamPaperProfile.ENGLISH_ONE)

        assertEquals(9, blueprint.slots.size)
        assertEquals(52, blueprint.totalQuestionCount)
        assertEquals(4, blueprint.slots.count { it.questionType == QuestionType.READING_COMPREHENSION })
        assertEquals(1, blueprint.slots.count { it.key == "special" })
        assertEquals(QuestionType.SENTENCE_INSERTION, blueprint.specialQuestionType)
        assertEquals(listOf(1, 21, 26, 31, 36, 41, 46, 51, 52), blueprint.slots.map { it.startQuestionNumber })
    }

    @Test
    fun readiness_requiresSelectedRotatingSlot_notEverySpecialType() {
        val blueprint = ExamPaperBlueprint.forYear(2027, ExamPaperProfile.ENGLISH_ONE)
        val occupied = blueprint.slots.mapTo(mutableSetOf()) { it.key }

        assertEquals(QuestionType.COMMENT_OPINION_MATCH, blueprint.specialQuestionType)
        assertTrue(blueprint.isReady(occupied))
        assertFalse(blueprint.slots.any { it.questionType == QuestionType.PARAGRAPH_ORDER })
        assertFalse(blueprint.slots.any { it.questionType == QuestionType.INFORMATION_MATCH })
    }

    @Test
    fun nextAvailableReadingSlot_isOrderedAndStopsAtQuota() {
        val blueprint = ExamPaperBlueprint.forYear(2026)
        val first = blueprint.nextAvailableSlot(QuestionType.READING_COMPREHENSION, null, emptySet())
        val second = blueprint.nextAvailableSlot(
            QuestionType.READING_COMPREHENSION,
            null,
            setOf("reading_1")
        )
        val full = blueprint.nextAvailableSlot(
            QuestionType.READING_COMPREHENSION,
            null,
            setOf("reading_1", "reading_2", "reading_3", "reading_4")
        )

        assertEquals("reading_1", first?.key)
        assertEquals("reading_2", second?.key)
        assertNull(full)
    }

    @Test
    fun englishTwoBlueprint_usesSingleTranslationQuestion() {
        val blueprint = ExamPaperBlueprint.forYear(2026, ExamPaperProfile.ENGLISH_TWO)
        val translation = blueprint.slots.single { it.questionType == QuestionType.TRANSLATION }

        assertEquals("ENG2", translation.variant)
        assertEquals(1, translation.questionCount)
        assertEquals(48, blueprint.totalQuestionCount)
        assertEquals(47, blueprint.slots.single { it.key == "writing_small" }.startQuestionNumber)
    }

    @Test
    fun dailyPaperTitle_usesStableDateAndSequencePattern() {
        assertEquals("2026-07-19-第1套", ExamPaperBlueprint.dailyPaperTitle("2026-07-19", 1))
        assertEquals("2026-07-19-第3套", ExamPaperBlueprint.dailyPaperTitle("2026-07-19", 3))
    }

    @Test
    fun explicitSpecialType_overridesAnnualDefaultForOnePaper() {
        val blueprint = ExamPaperBlueprint.forYear(
            year = 2026,
            specialQuestionType = QuestionType.INFORMATION_MATCH
        )

        assertEquals(QuestionType.INFORMATION_MATCH, blueprint.specialQuestionType)
        assertEquals(
            listOf(QuestionType.INFORMATION_MATCH),
            blueprint.slots.filter { it.key == "special" }.map { it.questionType }
        )
    }
}
