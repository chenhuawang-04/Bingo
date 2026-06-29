package com.xty.englishhelper.ui.screen.questionbank

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.xty.englishhelper.data.image.ImageCompressionManager
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.PracticeRecord
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.TtsState
import com.xty.englishhelper.domain.organize.BackgroundOrganizeManager
import com.xty.englishhelper.domain.plan.PlanAutoProgressTracker
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.repository.WordPhraseRepository
import com.xty.englishhelper.domain.usecase.article.AnalyzeParagraphUseCase
import com.xty.englishhelper.domain.usecase.article.QuickAnalyzeWordUseCase
import com.xty.englishhelper.domain.usecase.article.ScanWordLinksUseCase
import com.xty.englishhelper.domain.usecase.article.TranslateParagraphUseCase
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase
import com.xty.englishhelper.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionBankReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `rapid objective submit writes practice records only once`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val items = listOf(
            questionItem(id = 1L, correctAnswer = "A"),
            questionItem(id = 2L, correctAnswer = "C")
        )
        val repository = questionBankRepository(
            group = questionGroup(type = QuestionType.READING_COMPREHENSION, items = items)
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.selectAnswer(1L, "A")
        viewModel.selectAnswer(2L, "B")
        viewModel.submitAnswers()
        viewModel.submitAnswers()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.insertPracticeRecords(match<List<PracticeRecord>> { it.size == 2 })
        }
        coVerify(exactly = 1) { repository.incrementWrongCount(2L) }
        assertTrue(viewModel.uiState.value.isSubmitted)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `showing answers freezes answer selection`() {
        val state = ReaderUiState(showingAnswers = true)

        assertFalse(QuestionPracticeRules.canSelectAnswer(state))
    }

    @Test
    fun `objective submit requires every answered item with a reference answer`() {
        val state = ReaderUiState(
            group = questionGroup(
                type = QuestionType.READING_COMPREHENSION,
                items = listOf(
                    questionItem(id = 1L, correctAnswer = "A"),
                    questionItem(id = 2L, correctAnswer = "B")
                )
            ),
            items = listOf(
                questionItem(id = 1L, correctAnswer = "A"),
                questionItem(id = 2L, correctAnswer = "B")
            ),
            selectedAnswers = mapOf(1L to "A")
        )

        assertEquals(1, QuestionPracticeRules.missingRequiredAnswerCount(state))
        assertFalse(QuestionPracticeRules.canSubmitObjectiveAnswers(state))
    }

    @Test
    fun `translation submit requires every item to have text`() {
        val items = listOf(
            questionItem(id = 1L, correctAnswer = "参考译文 1"),
            questionItem(id = 2L, correctAnswer = "参考译文 2")
        )
        val incomplete = ReaderUiState(
            group = questionGroup(type = QuestionType.TRANSLATION, items = items),
            items = items,
            selectedAnswers = mapOf(1L to "translation")
        )
        val complete = incomplete.copy(selectedAnswers = mapOf(1L to "translation", 2L to "translation"))

        assertFalse(QuestionPracticeRules.canSubmitTranslationAnswers(incomplete))
        assertTrue(QuestionPracticeRules.canSubmitTranslationAnswers(complete))
    }

    @Test
    fun `load data builds display paragraphs from legacy passage text`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = questionBankRepository(
            group = questionGroup(
                type = QuestionType.WRITING,
                passageText = "First paragraph.\n\nSecond paragraph.",
                items = listOf(questionItem(id = 1L, correctAnswer = null))
            )
        )

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("First paragraph.", "Second paragraph."), viewModel.uiState.value.paragraphs.map { it.text })
        assertTrue(viewModel.uiState.value.paragraphs.all { it.id < 0 })
    }

    private fun createViewModel(repository: QuestionBankRepository): QuestionBankReaderViewModel {
        val ttsState = MutableStateFlow(TtsState())
        val ttsManager = mockk<TtsManager>(relaxed = true)
        every { ttsManager.state } returns ttsState
        every { ttsManager.prewarmArticle(any(), any()) } just Runs

        val dictionaryRepository = mockk<DictionaryRepository>()
        every { dictionaryRepository.getAllDictionaries() } returns flowOf(emptyList<Dictionary>())

        val taskRepository = mockk<BackgroundTaskRepository>()
        every { taskRepository.observeTasksByTypes(any()) } returns flowOf(emptyList())

        val planAutoProgressTracker = mockk<PlanAutoProgressTracker>()
        coEvery { planAutoProgressTracker.onQuestionSubmitted(any()) } just Runs

        return QuestionBankReaderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("groupId" to GROUP_ID)),
            appContext = mockk<Context>(relaxed = true),
            repository = repository,
            aiRepository = mockk<QuestionBankAiRepository>(relaxed = true),
            scanWordLinks = mockk<ScanWordLinksUseCase>(relaxed = true),
            analyzeParagraph = mockk<AnalyzeParagraphUseCase>(relaxed = true),
            translateParagraph = mockk<TranslateParagraphUseCase>(relaxed = true),
            quickAnalyzeWord = mockk<QuickAnalyzeWordUseCase>(relaxed = true),
            saveWord = mockk<SaveWordUseCase>(relaxed = true),
            ttsManager = ttsManager,
            settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
            dictionaryRepository = dictionaryRepository,
            wordPhraseRepository = mockk<WordPhraseRepository>(relaxed = true),
            unitRepository = mockk<UnitRepository>(relaxed = true),
            backgroundOrganizeManager = mockk<BackgroundOrganizeManager>(relaxed = true),
            backgroundTaskManager = mockk<BackgroundTaskManager>(relaxed = true),
            taskRepository = taskRepository,
            imageCompressionManager = mockk<ImageCompressionManager>(relaxed = true),
            planAutoProgressTracker = planAutoProgressTracker
        )
    }

    private fun questionBankRepository(group: QuestionGroup): QuestionBankRepository {
        val repository = mockk<QuestionBankRepository>(relaxed = true)
        coEvery { repository.getGroupById(GROUP_ID) } returns group
        coEvery { repository.getExamPaperById(any()) } returns null
        coEvery { repository.getWrongItemIds(GROUP_ID) } returns emptyList()
        coEvery { repository.getItemsByGroup(GROUP_ID) } returns group.items
        coEvery { repository.insertPracticeRecords(any()) } just Runs
        coEvery { repository.incrementWrongCount(any()) } just Runs
        return repository
    }

    private fun questionGroup(
        type: QuestionType,
        passageText: String = "",
        items: List<QuestionItem> = emptyList()
    ): QuestionGroup = QuestionGroup(
        id = GROUP_ID,
        uid = "group-$GROUP_ID",
        examPaperId = 10L,
        questionType = type,
        passageText = passageText,
        createdAt = 1L,
        updatedAt = 1L,
        items = items
    )

    private fun questionItem(
        id: Long,
        correctAnswer: String?
    ): QuestionItem = QuestionItem(
        id = id,
        questionGroupId = GROUP_ID,
        questionNumber = id.toInt(),
        questionText = "Question $id",
        optionA = "A",
        optionB = "B",
        optionC = "C",
        optionD = "D",
        correctAnswer = correctAnswer,
        orderInGroup = id.toInt()
    )

    private companion object {
        const val GROUP_ID = 42L
    }
}
