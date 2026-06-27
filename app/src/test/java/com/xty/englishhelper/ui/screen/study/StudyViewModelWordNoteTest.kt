package com.xty.englishhelper.ui.screen.study

import androidx.lifecycle.SavedStateHandle
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskPayload
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordNoteOrganizePayload
import com.xty.englishhelper.domain.plan.PlanAutoProgressTracker
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.usecase.brainstorm.BuildBrainstormSessionUseCase
import com.xty.englishhelper.domain.usecase.brainstorm.CollectRelatedGroupUseCase
import com.xty.englishhelper.domain.usecase.brainstorm.GetBrainstormDailyGoalUseCase
import com.xty.englishhelper.domain.usecase.brainstorm.SaveBrainstormDailyGoalUseCase
import com.xty.englishhelper.domain.usecase.brainstorm.UpdateBrainstormProgressUseCase
import com.xty.englishhelper.domain.usecase.dictionary.GetCloudWordExamplesUseCase
import com.xty.englishhelper.domain.usecase.study.GetDueWordsUseCase
import com.xty.englishhelper.domain.usecase.study.GetNewWordsUseCase
import com.xty.englishhelper.domain.usecase.study.GetStudyWordEdgePreviewsUseCase
import com.xty.englishhelper.domain.usecase.study.PreviewIntervalsUseCase
import com.xty.englishhelper.domain.usecase.study.ReviewWordUseCase
import com.xty.englishhelper.domain.usecase.study.SearchStudyWordNoteSuggestionsUseCase
import com.xty.englishhelper.domain.usecase.study.SubmitStudyWordNoteResult
import com.xty.englishhelper.domain.usecase.study.SubmitStudyWordNoteUseCase
import com.xty.englishhelper.domain.usecase.study.StudyWordNoteOutcome
import com.xty.englishhelper.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelWordNoteTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `queued word notes for multiple targets each refresh current word edges when completed`() = runTest {
        val currentWord = WordDetails(
            id = 1L,
            dictionaryId = 1L,
            spelling = "adapt",
            normalizedSpelling = "adapt"
        )
        val backgroundTaskRepository = FakeBackgroundTaskRepository()
        val getDueWords = mockk<GetDueWordsUseCase>()
        val getNewWords = mockk<GetNewWordsUseCase>()
        val reviewWord = mockk<ReviewWordUseCase>()
        val previewIntervals = mockk<PreviewIntervalsUseCase>()
        val getCloudWordExamples = mockk<GetCloudWordExamplesUseCase>()
        val settingsDataStore = mockk<SettingsDataStore>()
        val ttsManager = mockk<TtsManager>(relaxed = true)
        val planAutoProgressTracker = mockk<PlanAutoProgressTracker>(relaxed = true)
        val getBrainstormDailyGoal = mockk<GetBrainstormDailyGoalUseCase>()
        val saveBrainstormDailyGoal = mockk<SaveBrainstormDailyGoalUseCase>()
        val updateBrainstormProgress = mockk<UpdateBrainstormProgressUseCase>()
        val collectRelatedGroup = mockk<CollectRelatedGroupUseCase>()
        val buildBrainstormSession = mockk<BuildBrainstormSessionUseCase>()
        val getStudyWordEdgePreviews = mockk<GetStudyWordEdgePreviewsUseCase>()
        val searchStudyWordNoteSuggestions = mockk<SearchStudyWordNoteSuggestionsUseCase>()
        val submitStudyWordNote = mockk<SubmitStudyWordNoteUseCase>()

        every { settingsDataStore.studyWordNoteEnabled } returns MutableStateFlow(true)
        every { settingsDataStore.ttsAutoStudy } returns flowOf(false)
        coEvery { settingsDataStore.getStudyWordNoteEnabled() } returns true
        coEvery { getDueWords.invoke(emptyList(), StudyMode.NORMAL) } returns listOf(currentWord)
        coEvery { getNewWords.invoke(emptyList(), StudyMode.NORMAL) } returns emptyList()
        coEvery { getStudyWordEdgePreviews.invoke(1L, 1L, 1.0) } returns emptyList()
        coEvery { searchStudyWordNoteSuggestions.invoke(any(), any(), any()) } returns emptyList()
        coEvery {
            submitStudyWordNote.invoke(currentWord, "alpha", emptyList())
        } returns SubmitStudyWordNoteResult(
            relatedWordId = 2L,
            relatedSpelling = "alpha",
            createdWord = true,
            outcome = StudyWordNoteOutcome.QUEUED,
            message = "已创建 alpha，并加入后台整理"
        )
        coEvery {
            submitStudyWordNote.invoke(currentWord, "beta", emptyList())
        } returns SubmitStudyWordNoteResult(
            relatedWordId = 3L,
            relatedSpelling = "beta",
            createdWord = true,
            outcome = StudyWordNoteOutcome.QUEUED,
            message = "已创建 beta，并加入后台整理"
        )

        val viewModel = StudyViewModel(
            savedStateHandle = SavedStateHandle(mapOf("unitIds" to "", "mode" to "NORMAL")),
            getDueWords = getDueWords,
            getNewWords = getNewWords,
            reviewWord = reviewWord,
            previewIntervals = previewIntervals,
            getCloudWordExamples = getCloudWordExamples,
            settingsDataStore = settingsDataStore,
            ttsManager = ttsManager,
            backgroundTaskRepository = backgroundTaskRepository,
            planAutoProgressTracker = planAutoProgressTracker,
            getBrainstormDailyGoal = getBrainstormDailyGoal,
            saveBrainstormDailyGoal = saveBrainstormDailyGoal,
            updateBrainstormProgress = updateBrainstormProgress,
            collectRelatedGroup = collectRelatedGroup,
            buildBrainstormSession = buildBrainstormSession,
            getStudyWordEdgePreviews = getStudyWordEdgePreviews,
            searchStudyWordNoteSuggestions = searchStudyWordNoteSuggestions,
            submitStudyWordNote = submitStudyWordNote
        )

        advanceUntilIdle()
        coVerify(exactly = 1) { getStudyWordEdgePreviews.invoke(1L, 1L, 1.0) }

        viewModel.onWordNoteInputChange("alpha")
        viewModel.submitWordNote()
        advanceUntilIdle()

        viewModel.onWordNoteInputChange("beta")
        viewModel.submitWordNote()
        advanceUntilIdle()

        backgroundTaskRepository.emit(
            listOf(
                wordNoteTask(
                    taskId = 100L,
                    sourceWordId = 1L,
                    targetWordId = 2L,
                    targetSpelling = "alpha",
                    status = BackgroundTaskStatus.SUCCESS
                ),
                wordNoteTask(
                    taskId = 101L,
                    sourceWordId = 1L,
                    targetWordId = 3L,
                    targetSpelling = "beta",
                    status = BackgroundTaskStatus.PENDING
                )
            )
        )
        advanceUntilIdle()

        assertEquals("alpha 已加入强关联", viewModel.uiState.value.wordNoteMessage)
        coVerify(exactly = 2) { getStudyWordEdgePreviews.invoke(1L, 1L, 1.0) }

        backgroundTaskRepository.emit(
            listOf(
                wordNoteTask(
                    taskId = 100L,
                    sourceWordId = 1L,
                    targetWordId = 2L,
                    targetSpelling = "alpha",
                    status = BackgroundTaskStatus.SUCCESS
                ),
                wordNoteTask(
                    taskId = 101L,
                    sourceWordId = 1L,
                    targetWordId = 3L,
                    targetSpelling = "beta",
                    status = BackgroundTaskStatus.SUCCESS
                )
            )
        )
        advanceUntilIdle()

        assertEquals("beta 已加入强关联", viewModel.uiState.value.wordNoteMessage)
        coVerify(exactly = 3) { getStudyWordEdgePreviews.invoke(1L, 1L, 1.0) }
    }

    private fun wordNoteTask(
        taskId: Long,
        sourceWordId: Long,
        targetWordId: Long,
        targetSpelling: String,
        status: BackgroundTaskStatus
    ): BackgroundTask = BackgroundTask(
        id = taskId,
        type = BackgroundTaskType.WORD_NOTE_ORGANIZE,
        status = status,
        payload = WordNoteOrganizePayload(
            dictionaryId = 1L,
            sourceWordId = sourceWordId,
            sourceSpelling = "adapt",
            targetWordId = targetWordId,
            targetSpelling = targetSpelling
        ),
        progressCurrent = 0,
        progressTotal = 1,
        progressMessage = null,
        attempt = 0,
        errorMessage = null,
        createdAt = taskId,
        updatedAt = taskId,
        dedupeKey = "word_note:1:$sourceWordId:$targetWordId"
    )

    private class FakeBackgroundTaskRepository : BackgroundTaskRepository {
        private val tasks = MutableStateFlow<List<BackgroundTask>>(emptyList())

        fun emit(value: List<BackgroundTask>) {
            tasks.value = value
        }

        override fun observeAllTasks(): Flow<List<BackgroundTask>> = tasks

        override fun observeTasksByTypes(types: List<BackgroundTaskType>): Flow<List<BackgroundTask>> = tasks

        override suspend fun getTaskById(id: Long): BackgroundTask? = null

        override suspend fun getTaskByDedupeKey(dedupeKey: String): BackgroundTask? = null

        override suspend fun insertTask(
            type: BackgroundTaskType,
            payload: BackgroundTaskPayload,
            dedupeKey: String
        ): Long = 0L

        override suspend fun getTasksByStatuses(
            statuses: List<BackgroundTaskStatus>,
            limit: Int
        ): List<BackgroundTask> = emptyList()

        override suspend fun updateStatus(id: Long, status: BackgroundTaskStatus, errorMessage: String?) = Unit

        override suspend fun updateProgress(id: Long, current: Int, total: Int, message: String?) = Unit

        override suspend fun incrementAttempt(id: Long) = Unit

        override suspend fun updatePayload(id: Long, type: BackgroundTaskType, payload: BackgroundTaskPayload) = Unit

        override suspend fun updateStatusByStatus(fromStatus: BackgroundTaskStatus, toStatus: BackgroundTaskStatus) = Unit

        override suspend fun deleteTask(id: Long) = Unit

        override suspend fun deleteTasksByStatuses(statuses: List<BackgroundTaskStatus>) = Unit
    }
}
