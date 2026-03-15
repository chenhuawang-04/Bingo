package com.xty.englishhelper.domain.model

data class QuestionItem(
    val id: Long = 0,
    val questionGroupId: Long,
    val questionNumber: Int,
    val questionText: String,
    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,
    val correctAnswer: String? = null,
    val answerSource: AnswerSource = AnswerSource.NONE,
    val explanation: String? = null,
    val orderInGroup: Int = 0,
    val wordCount: Int = 0,
    val difficultyLevel: DifficultyLevel? = null,
    val difficultyScore: Float? = null,
    val wrongCount: Int = 0,
    val extraData: String? = null,
    val sampleSourceTitle: String? = null,
    val sampleSourceUrl: String? = null,
    val sampleSourceInfo: String? = null
)
