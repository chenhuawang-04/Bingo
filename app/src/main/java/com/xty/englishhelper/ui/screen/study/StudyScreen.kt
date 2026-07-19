package com.xty.englishhelper.ui.screen.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.R
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.ui.components.LoadingIndicator
import com.xty.englishhelper.ui.components.topbar.AppTopBarCloseButton
import com.xty.englishhelper.ui.components.topbar.AppTopBarEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onBack: () -> Unit,
    onWordClick: (Long, Long) -> Unit,
    onArticleClick: (Long, Long) -> Unit,
    viewModel: StudyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(onBack = if (state.isRelatedClusterReview) viewModel::exitRelatedClusterReview else onBack)

    // Brainstorm daily goal dialog
    if (state.showBrainstormGoalDialog) {
        BrainstormGoalDialog(
            defaultTarget = state.brainstormGoalTarget,
            onConfirm = viewModel::confirmBrainstormGoal
        )
    }

    // Brainstorm goal reached dialog
    if (state.showBrainstormGoalReachedDialog) {
        BrainstormGoalReachedDialog(
            learned = state.brainstormLearnedCount,
            target = state.brainstormTargetCount,
            dueLearned = state.brainstormDueLearned,
            newLearned = state.brainstormNewLearned,
            onContinue = viewModel::onContinueAfterGoal,
            onExit = viewModel::onExitAfterGoal
        )
    }

    AppTopBarEffect(
        title = {
            if (state.isRelatedClusterReview) {
                Text(state.relatedClusterName.orEmpty())
            } else if (state.phase == StudyPhase.Studying || state.phase == StudyPhase.WaitingForNext) {
                if (state.studyMode == StudyMode.BRAINSTORM && state.brainstormTargetCount > 0) {
                    Text(stringResource(R.string.study_storm_progress, state.brainstormLearnedCount, state.brainstormTargetCount))
                } else {
                    val prefix = if (state.studyMode == StudyMode.BRAINSTORM) "风暴 " else ""
                    Text("$prefix${state.progress}/${state.total}")
                }
            } else {
                Text(stringResource(R.string.study_complete))
            }
        },
        navigationIcon = {
            AppTopBarCloseButton(
                if (state.isRelatedClusterReview) viewModel::exitRelatedClusterReview else onBack
            )
        }
    )

    Scaffold { padding ->
        when (state.phase) {
            StudyPhase.Loading -> {
                LoadingIndicator(Modifier.padding(padding))
            }

            StudyPhase.Studying -> {
                StudyingContent(
                    state = state,
                    onRevealAnswer = viewModel::onRevealAnswer,
                    onRate = viewModel::onRate,
                    onOpenRelatedWord = onWordClick,
                    onOpenArticle = onArticleClick,
                    onWordNoteInputChange = viewModel::onWordNoteInputChange,
                    onWordNoteSuggestionSelected = viewModel::onWordNoteSuggestionSelected,
                    onWordNoteSuggestionsExpandedChange = viewModel::setWordNoteSuggestionsExpanded,
                    onWordNoteExpandedChange = viewModel::setWordNoteExpanded,
                    onWordNoteEdgeTypeSelected = viewModel::selectWordNoteEdgeType,
                    onSubmitWordNote = viewModel::submitWordNote,
                    onCloudExampleSourceSelected = viewModel::selectCloudExampleSource,
                    onQuizAnswer = viewModel::onQuizAnswer,
                    onQuizContinue = viewModel::onQuizContinue,
                    onWordClustersExpandedChange = viewModel::setWordClustersExpanded,
                    onWordClusterEditorVisibleChange = viewModel::showWordClusterEditor,
                    onNewWordClusterNameChange = viewModel::onNewWordClusterNameChange,
                    onCreateWordCluster = viewModel::createWordCluster,
                    onSetCurrentWordInCluster = viewModel::setCurrentWordInCluster,
                    onStartRelatedClusterReview = viewModel::startRelatedClusterReview,
                    onRevealRelatedWord = viewModel::revealRelatedWord,
                    onRateRelatedWord = viewModel::rateRelatedWord,
                    onExitRelatedClusterReview = viewModel::exitRelatedClusterReview,
                    onRetryCurrentWordDetails = viewModel::retryCurrentWordDetails,
                    onRetryRelatedWordDetails = viewModel::retryRelatedWordDetails,
                    modifier = Modifier.padding(padding)
                )
            }

            StudyPhase.WaitingForNext -> {
                LoadingIndicator(Modifier.padding(padding))
            }

            StudyPhase.Finished -> {
                FinishedContent(
                    stats = state.stats,
                    onDone = onBack,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}
