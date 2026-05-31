package com.xty.englishhelper.ui.screen.study

import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.CloudWordExample
import com.xty.englishhelper.domain.study.Rating

data class StudyUiState(
    val phase: StudyPhase = StudyPhase.Loading,
    val currentWord: WordDetails? = null,
    val showAnswer: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0,
    val previewIntervals: Map<Rating, Long> = emptyMap(),
    val stats: StudyStats = StudyStats(),
    val error: String? = null,
    val studyMode: StudyMode = StudyMode.NORMAL,
    val currentWordRelatedSpellings: List<String> = emptyList(),
    val currentWordEdges: List<WordEdgePreview> = emptyList(),
    val cloudExampleSource: CloudExampleSource = CloudExampleSource.CAMBRIDGE,
    val cloudExamples: List<CloudWordExample> = emptyList(),
    val cloudExamplesLoading: Boolean = false,
    val cloudExamplesError: String? = null,
    // Brainstorm daily goal
    val showBrainstormGoalDialog: Boolean = false,
    val brainstormGoalTarget: Int = 200,
    val showBrainstormGoalReachedDialog: Boolean = false,
    val brainstormLearnedCount: Int = 0,
    val brainstormTargetCount: Int = 0,
    val brainstormDueLearned: Int = 0,
    val brainstormNewLearned: Int = 0
)

data class WordEdgePreview(
    val spelling: String,
    val edgeType: EdgeType
)

enum class StudyPhase {
    Loading,
    Studying,
    WaitingForNext,
    Finished
}

data class StudyStats(
    val totalWords: Int = 0,
    val againCount: Int = 0,
    val hardCount: Int = 0,
    val goodCount: Int = 0,
    val easyCount: Int = 0
)
