package com.xty.englishhelper.domain.model

data class ExamPaperSlot(
    val key: String,
    val questionType: QuestionType,
    val variant: String? = null,
    val sectionLabel: String,
    val orderInPaper: Int,
    val startQuestionNumber: Int,
    val questionCount: Int
)

data class ExamPaperBlueprint(
    val version: Int,
    val profile: ExamPaperProfile,
    val specialQuestionType: QuestionType,
    val slots: List<ExamPaperSlot>
) {
    val totalQuestionCount: Int = slots.sumOf { it.questionCount }

    fun nextAvailableSlot(
        questionType: QuestionType,
        variant: String?,
        occupiedSlotKeys: Set<String>
    ): ExamPaperSlot? = slots.firstOrNull { slot ->
        slot.key !in occupiedSlotKeys &&
            slot.questionType == questionType &&
            variantsMatch(slot.variant, variant)
    }

    fun isReady(occupiedSlotKeys: Set<String>): Boolean =
        slots.all { it.key in occupiedSlotKeys }

    companion object {
        const val CURRENT_VERSION = 1

        private val rotatingSpecialTypes = listOf(
            QuestionType.PARAGRAPH_ORDER,
            QuestionType.SENTENCE_INSERTION,
            QuestionType.COMMENT_OPINION_MATCH,
            QuestionType.SUBHEADING_MATCH,
            QuestionType.INFORMATION_MATCH
        )

        fun rotatingSpecialType(year: Int): QuestionType {
            val index = Math.floorMod(year - 2025, rotatingSpecialTypes.size)
            return rotatingSpecialTypes[index]
        }

        fun dailyPaperTitle(dayKey: String, sequence: Int): String {
            require(dayKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Invalid day key: $dayKey" }
            require(sequence > 0) { "Sequence must be positive" }
            return "$dayKey-第${sequence}套"
        }

        fun forYear(
            year: Int,
            profile: ExamPaperProfile = ExamPaperProfile.ENGLISH_ONE,
            specialQuestionType: QuestionType = rotatingSpecialType(year),
            version: Int = CURRENT_VERSION
        ): ExamPaperBlueprint {
            require(specialQuestionType in rotatingSpecialTypes || specialQuestionType == QuestionType.NEW_TYPE) {
                "Unsupported rotating question type: $specialQuestionType"
            }

            val translationCount = if (profile == ExamPaperProfile.ENGLISH_ONE) 5 else 1
            val translationVariant = if (profile == ExamPaperProfile.ENGLISH_ONE) "ENG1" else "ENG2"
            val writingStart = 46 + translationCount
            val slots = buildList {
                add(ExamPaperSlot("cloze", QuestionType.CLOZE, sectionLabel = "完形填空", orderInPaper = 0, startQuestionNumber = 1, questionCount = 20))
                repeat(4) { index ->
                    add(
                        ExamPaperSlot(
                            key = "reading_${index + 1}",
                            questionType = QuestionType.READING_COMPREHENSION,
                            sectionLabel = "阅读理解 ${index + 1}",
                            orderInPaper = index + 1,
                            startQuestionNumber = 21 + index * 5,
                            questionCount = 5
                        )
                    )
                }
                add(
                    ExamPaperSlot(
                        key = "special",
                        questionType = specialQuestionType,
                        sectionLabel = "新题型：${specialQuestionType.displayName}",
                        orderInPaper = 5,
                        startQuestionNumber = 41,
                        questionCount = 5
                    )
                )
                add(
                    ExamPaperSlot(
                        key = "translation",
                        questionType = QuestionType.TRANSLATION,
                        variant = translationVariant,
                        sectionLabel = if (profile == ExamPaperProfile.ENGLISH_ONE) "翻译（英语一）" else "翻译（英语二）",
                        orderInPaper = 6,
                        startQuestionNumber = 46,
                        questionCount = translationCount
                    )
                )
                add(
                    ExamPaperSlot(
                        key = "writing_small",
                        questionType = QuestionType.WRITING,
                        variant = "SMALL",
                        sectionLabel = "写作（小作文）",
                        orderInPaper = 7,
                        startQuestionNumber = writingStart,
                        questionCount = 1
                    )
                )
                add(
                    ExamPaperSlot(
                        key = "writing_large",
                        questionType = QuestionType.WRITING,
                        variant = "LARGE",
                        sectionLabel = "写作（大作文）",
                        orderInPaper = 8,
                        startQuestionNumber = writingStart + 1,
                        questionCount = 1
                    )
                )
            }
            return ExamPaperBlueprint(version, profile, specialQuestionType, slots)
        }

        fun forPaper(paper: ExamPaper): ExamPaperBlueprint {
            val year = paper.dayKey?.take(4)?.toIntOrNull()
                ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            return forYear(
                year = year,
                profile = paper.profile,
                specialQuestionType = paper.specialQuestionType ?: rotatingSpecialType(year),
                version = paper.blueprintVersion
            )
        }

        private fun variantsMatch(expected: String?, requested: String?): Boolean =
            expected == requested || (expected == null && requested.isNullOrBlank())
    }
}
