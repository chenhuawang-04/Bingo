package com.xty.englishhelper.ui.screen.study

import androidx.annotation.StringRes
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordSuggestion
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.CloudWordExample
import com.xty.englishhelper.domain.model.WordCluster
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
    val wordNoteEnabled: Boolean = false,
    val wordNoteExpanded: Boolean = false,
    val wordNoteInput: String = "",
    val wordNoteSuggestions: List<WordSuggestion> = emptyList(),
    val wordNoteSuggestionsLoading: Boolean = false,
    val wordNoteSuggestionsExpanded: Boolean = false,
    val wordNoteEdgeType: EdgeType = EdgeType.SEMANTIC_OVERLAP,
    val wordNoteSubmitting: Boolean = false,
    val wordNoteMessage: String? = null,
    val wordNoteError: String? = null,
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
    val brainstormNewLearned: Int = 0,
    // 阶段C：当前词所在学习簇的掌握进度（会话内）
    val brainstormClusterLearned: Int = 0,
    val brainstormClusterTotal: Int = 0,
    // 阶段C：揭示答案时展示的记忆钩子（最强关联的关系依据/例句）
    val currentWordHook: BrainstormHook? = null,
    // 阶段C：关联主动回忆选择题（开启且当前词符合条件时，替代翻卡流程）
    val brainstormQuiz: BrainstormQuiz? = null,
    val wordClusters: List<WordCluster> = emptyList(),
    val allWordClusters: List<WordCluster> = emptyList(),
    val wordClustersExpanded: Boolean = false,
    val wordClusterEditorVisible: Boolean = false,
    val newWordClusterName: String = "",
    val wordClusterSaving: Boolean = false,
    val wordClusterError: String? = null,
    val relatedClusterName: String? = null,
    val relatedWords: List<WordDetails> = emptyList(),
    val relatedWordIndex: Int = 0,
    val relatedWordShowAnswer: Boolean = false,
    val relatedWordRatings: Map<Long, Rating> = emptyMap()
)

val StudyUiState.isRelatedClusterReview: Boolean get() = relatedClusterName != null
val StudyUiState.relatedCurrentWord: WordDetails? get() = relatedWords.getOrNull(relatedWordIndex)

/** 记忆钩子：当前词与某关联词的关系依据 / 例句，揭示答案时展示。 */
data class BrainstormHook(
    val relatedSpelling: String,
    @StringRes val relationLabel: Int,
    val reason: String?,
    val example: String?
)

/** 关联主动回忆选择题：从同组词中选出当前词的正确关联词。 */
data class BrainstormQuiz(
    val targetSpelling: String,
    @StringRes val relationLabel: Int,
    val options: List<BrainstormQuizOption>,
    val correctWordId: Long,
    val selectedWordId: Long? = null
) {
    val answered: Boolean get() = selectedWordId != null
    val isCorrect: Boolean get() = selectedWordId == correctWordId
}

data class BrainstormQuizOption(
    val wordId: Long,
    val spelling: String
)

data class WordEdgePreview(
    val wordId: Long,
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
