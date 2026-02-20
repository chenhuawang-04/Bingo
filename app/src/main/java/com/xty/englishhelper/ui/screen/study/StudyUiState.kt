package com.xty.englishhelper.ui.screen.study

import com.xty.englishhelper.domain.model.WordDetails

data class StudyUiState(
    val phase: StudyPhase = StudyPhase.Loading,
    val currentWord: WordDetails? = null,
    val showAnswer: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0,
    val stats: StudyStats = StudyStats(),
    val error: String? = null
)

enum class StudyPhase {
    Loading,
    Studying,
    Finished
}

data class StudyStats(
    val totalWords: Int = 0,
    val knownCount: Int = 0,
    val unknownCount: Int = 0,
    val masteredCount: Int = 0
)
