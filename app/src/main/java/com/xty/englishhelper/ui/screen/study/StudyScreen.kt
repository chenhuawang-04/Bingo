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
import androidx.hilt.navigation.compose.hiltViewModel
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onBack: () -> Unit,
    viewModel: StudyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(onBack = onBack)

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.phase == StudyPhase.Studying || state.phase == StudyPhase.WaitingForNext) {
                        if (state.studyMode == StudyMode.BRAINSTORM && state.brainstormTargetCount > 0) {
                            // Show daily goal progress
                            Text("风暴 ${state.brainstormLearnedCount}/${state.brainstormTargetCount}")
                        } else {
                            val prefix = if (state.studyMode == StudyMode.BRAINSTORM) "风暴 " else ""
                            Text("$prefix${state.progress}/${state.total}")
                        }
                    } else {
                        Text("学习完成")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            )
        }
    ) { padding ->
        when (state.phase) {
            StudyPhase.Loading -> {
                LoadingIndicator(Modifier.padding(padding))
            }

            StudyPhase.Studying -> {
                StudyingContent(
                    state = state,
                    onRevealAnswer = viewModel::onRevealAnswer,
                    onRate = viewModel::onRate,
                    onCloudExampleSourceSelected = viewModel::selectCloudExampleSource,
                    onQuizAnswer = viewModel::onQuizAnswer,
                    onQuizContinue = viewModel::onQuizContinue,
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
