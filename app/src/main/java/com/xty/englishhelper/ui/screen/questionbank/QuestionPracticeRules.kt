package com.xty.englishhelper.ui.screen.questionbank

import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionType

internal object QuestionPracticeRules {
    fun canSelectAnswer(state: ReaderUiState): Boolean {
        return !state.isSubmitted && !state.showingAnswers && !state.isSubmitting
    }

    fun missingRequiredAnswerCount(state: ReaderUiState): Int {
        return requiredAnswerItems(state).count { item ->
            state.selectedAnswers[item.id].isNullOrBlank()
        }
    }

    fun canSubmitObjectiveAnswers(state: ReaderUiState): Boolean {
        val hasAnswers = state.items.any { it.correctAnswer != null }
        return hasAnswers &&
            state.selectedAnswers.isNotEmpty() &&
            missingRequiredAnswerCount(state) == 0 &&
            !state.isSubmitting
    }

    fun canSubmitTranslationAnswers(state: ReaderUiState): Boolean {
        return state.items.isNotEmpty() &&
            missingRequiredAnswerCount(state) == 0 &&
            !state.isSubmitting
    }

    fun submissionValidationError(state: ReaderUiState): String? {
        if (state.group?.questionType == QuestionType.WRITING) {
            val item = state.items.firstOrNull()
            val essayText = item?.let { state.selectedAnswers[it.id]?.trim() }.orEmpty()
            return when {
                item == null || essayText.isBlank() -> "请先输入作文内容"
                state.writingPracticeEnabled && state.isPreparingWritingPractice ->
                    "写作练习短语仍在准备中，请稍后提交"
                state.writingPracticeEnabled && state.writingPracticePhrases.isEmpty() ->
                    "写作练习模式尚未选出短语，请先关闭或重新开启"
                else -> null
            }
        }

        val missing = missingRequiredAnswerCount(state)
        return if (missing > 0) "还有 ${missing} 题未作答" else null
    }

    private fun requiredAnswerItems(state: ReaderUiState): List<QuestionItem> {
        return when (state.group?.questionType) {
            QuestionType.WRITING -> emptyList()
            QuestionType.TRANSLATION -> state.items
            else -> state.items.filter { it.correctAnswer != null }
        }
    }
}
