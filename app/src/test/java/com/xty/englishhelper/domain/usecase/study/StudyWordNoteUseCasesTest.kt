package com.xty.englishhelper.domain.usecase.study

import com.xty.englishhelper.domain.background.BackgroundTaskEnqueueResult
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.usecase.unit.AddWordsToUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitIdsForWordUseCase
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyWordNoteUseCasesTest {

    @Test
    fun `search suggestions skips blank input without touching repository`() = runTest {
        val wordRepository = mockk<WordRepository>()
        val useCase = SearchStudyWordNoteSuggestionsUseCase(wordRepository)

        val result = useCase(currentWord(), "   ")

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { wordRepository.suggestWordSpellings(any(), any(), any(), any()) }
    }

    @Test
    fun `search suggestions skips one character input without touching repository`() = runTest {
        val wordRepository = mockk<WordRepository>()
        val useCase = SearchStudyWordNoteSuggestionsUseCase(wordRepository)

        val result = useCase(currentWord(), "a")

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { wordRepository.suggestWordSpellings(any(), any(), any(), any()) }
    }

    @Test
    fun `search suggestions filters current word and duplicates while preserving order`() = runTest {
        val wordRepository = mockk<WordRepository>()
        val useCase = SearchStudyWordNoteSuggestionsUseCase(wordRepository)

        coEvery { wordRepository.suggestWordSpellings(1L, "ad", 1L, 12) } returns listOf(
            "ad",
            "adapt",
            "add",
            "adapter",
            "Adapter",
            "adopt"
        )

        val result = useCase(currentWord(), " ad ", limit = 3)

        assertEquals(listOf("ad", "add", "adopt"), result)
        coVerify(exactly = 1) { wordRepository.suggestWordSpellings(1L, "ad", 1L, 12) }
    }

    @Test
    fun `promotes existing relation without enqueuing background task`() = runTest {
        val wordRepository = mockk<WordRepository>()
        val wordPoolRepository = mockk<WordPoolRepository>()
        val backgroundTaskRepository = mockk<BackgroundTaskRepository>()
        val backgroundTaskManager = mockk<BackgroundTaskManager>()
        val useCase = subject(
            wordRepository = wordRepository,
            wordPoolRepository = wordPoolRepository,
            backgroundTaskRepository = backgroundTaskRepository,
            backgroundTaskManager = backgroundTaskManager
        )
        val currentWord = currentWord()
        val relatedWord = WordDetails(
            id = 2L,
            dictionaryId = 1L,
            spelling = "adopt",
            normalizedSpelling = "adopt"
        )

        coEvery { wordRepository.findByNormalizedSpelling(1L, "adopt") } returns relatedWord
        coEvery { wordPoolRepository.confirmWordRelation(1L, 1L, 2L) } returns true

        val result = useCase(currentWord, " adopt ")

        assertEquals(StudyWordNoteOutcome.PROMOTED, result.outcome)
        assertEquals(2L, result.relatedWordId)
        assertEquals("adopt", result.relatedSpelling)
        assertFalse(result.createdWord)
        coVerify(exactly = 0) {
            backgroundTaskManager.enqueueWordNoteOrganize(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `connects existing word immediately when no relation exists yet`() = runTest {
        val wordRepository = mockk<WordRepository>()
        val wordPoolRepository = mockk<WordPoolRepository>(relaxed = true)
        val backgroundTaskRepository = mockk<BackgroundTaskRepository>()
        val backgroundTaskManager = mockk<BackgroundTaskManager>()
        val useCase = subject(
            wordRepository = wordRepository,
            wordPoolRepository = wordPoolRepository,
            backgroundTaskRepository = backgroundTaskRepository,
            backgroundTaskManager = backgroundTaskManager
        )
        val currentWord = currentWord()
        val relatedWord = WordDetails(
            id = 3L,
            dictionaryId = 1L,
            spelling = "adapter",
            normalizedSpelling = "adapter"
        )

        coEvery { wordRepository.findByNormalizedSpelling(1L, "adapter") } returns relatedWord
        coEvery { wordPoolRepository.confirmWordRelation(1L, 1L, 3L) } returns false
        coEvery { backgroundTaskRepository.getTaskByDedupeKey("word_note:1:1:3") } returns null

        val result = useCase(currentWord, "adapter")

        assertEquals(StudyWordNoteOutcome.PROMOTED, result.outcome)
        assertEquals(3L, result.relatedWordId)
        assertFalse(result.createdWord)
        coVerify(exactly = 1) { wordPoolRepository.organizeWordNoteRelation(1L, 1L, 3L) }
        coVerify(exactly = 0) {
            backgroundTaskManager.enqueueWordNoteOrganize(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `returns already queued when repeated submit hits pending word note organize task`() = runTest {
        val wordRepository = mockk<WordRepository>()
        val wordPoolRepository = mockk<WordPoolRepository>(relaxed = true)
        val backgroundTaskRepository = mockk<BackgroundTaskRepository>()
        val backgroundTaskManager = mockk<BackgroundTaskManager>()
        val useCase = subject(
            wordRepository = wordRepository,
            wordPoolRepository = wordPoolRepository,
            backgroundTaskRepository = backgroundTaskRepository,
            backgroundTaskManager = backgroundTaskManager
        )
        val currentWord = currentWord()
        val relatedWord = WordDetails(
            id = 7L,
            dictionaryId = 1L,
            spelling = "adaptation",
            normalizedSpelling = "adaptation"
        )
        val pendingTask = BackgroundTask(
            id = 99L,
            type = BackgroundTaskType.WORD_NOTE_ORGANIZE,
            status = BackgroundTaskStatus.PENDING,
            payload = null,
            progressCurrent = 0,
            progressTotal = 0,
            progressMessage = null,
            attempt = 0,
            errorMessage = null,
            createdAt = 1L,
            updatedAt = 1L,
            dedupeKey = "word_note:1:1:7"
        )

        coEvery { wordRepository.findByNormalizedSpelling(1L, "adaptation") } returns relatedWord
        coEvery { wordPoolRepository.confirmWordRelation(1L, 1L, 7L) } returns false
        coEvery { backgroundTaskRepository.getTaskByDedupeKey("word_note:1:1:7") } returns pendingTask

        val result = useCase(currentWord, "adaptation")

        assertEquals(StudyWordNoteOutcome.ALREADY_QUEUED, result.outcome)
        assertEquals(7L, result.relatedWordId)
        assertFalse(result.createdWord)
        coVerify(exactly = 0) { wordPoolRepository.organizeWordNoteRelation(any(), any(), any()) }
        coVerify(exactly = 0) {
            backgroundTaskManager.enqueueWordNoteOrganize(
                any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `creates missing word in first unit and enqueues background relation organize`() = runTest {
        val wordRepository = mockk<WordRepository>()
        val wordPoolRepository = mockk<WordPoolRepository>()
        val saveWord = mockk<SaveWordUseCase>()
        val getUnitIdsForWord = mockk<GetUnitIdsForWordUseCase>()
        val addWordsToUnit = mockk<AddWordsToUnitUseCase>(relaxed = true)
        val backgroundTaskManager = mockk<BackgroundTaskManager>()
        val useCase = subject(
            wordRepository = wordRepository,
            wordPoolRepository = wordPoolRepository,
            saveWord = saveWord,
            getUnitIdsForWord = getUnitIdsForWord,
            addWordsToUnit = addWordsToUnit,
            backgroundTaskManager = backgroundTaskManager
        )
        val currentWord = currentWord()
        val createdWord = WordDetails(
            id = 7L,
            dictionaryId = 1L,
            spelling = "adaptation",
            normalizedSpelling = "adaptation"
        )

        coEvery { wordRepository.findByNormalizedSpelling(1L, "adaptation") } returns null
        coEvery { saveWord(any()) } returns 7L
        coEvery { getUnitIdsForWord(1L) } returns listOf(11L, 12L)
        coEvery { wordRepository.getWordById(7L) } returns createdWord
        coEvery { wordPoolRepository.confirmWordRelation(1L, 1L, 7L) } returns false
        coEvery {
            backgroundTaskManager.enqueueWordNoteOrganize(
                dictionaryId = 1L,
                sourceWordId = 1L,
                sourceSpelling = "adapt",
                targetWordId = 7L,
                targetSpelling = "adaptation",
                organizeTargetWordFirst = true,
                targetReferenceHints = listOf("adapt"),
                force = true
            )
        } returns BackgroundTaskEnqueueResult.ENQUEUED

        val result = useCase(currentWord, "adaptation")

        assertTrue(result.createdWord)
        assertEquals(StudyWordNoteOutcome.QUEUED, result.outcome)
        assertEquals(7L, result.relatedWordId)
        coVerify(exactly = 1) { addWordsToUnit(11L, listOf(7L)) }
        coVerify(exactly = 1) {
            backgroundTaskManager.enqueueWordNoteOrganize(
                dictionaryId = 1L,
                sourceWordId = 1L,
                sourceSpelling = "adapt",
                targetWordId = 7L,
                targetSpelling = "adaptation",
                organizeTargetWordFirst = true,
                targetReferenceHints = listOf("adapt"),
                force = true
            )
        }
    }

    private fun subject(
        wordRepository: WordRepository = mockk(relaxed = true),
        wordPoolRepository: WordPoolRepository = mockk(relaxed = true),
        saveWord: SaveWordUseCase = mockk(relaxed = true),
        getUnitIdsForWord: GetUnitIdsForWordUseCase = mockk(relaxed = true),
        addWordsToUnit: AddWordsToUnitUseCase = mockk(relaxed = true),
        backgroundTaskRepository: BackgroundTaskRepository = mockk(relaxed = true),
        backgroundTaskManager: BackgroundTaskManager = mockk(relaxed = true)
    ): SubmitStudyWordNoteUseCase = SubmitStudyWordNoteUseCase(
        wordRepository = wordRepository,
        wordPoolRepository = wordPoolRepository,
        saveWord = saveWord,
        getUnitIdsForWord = getUnitIdsForWord,
        addWordsToUnit = addWordsToUnit,
        backgroundTaskRepository = backgroundTaskRepository,
        backgroundTaskManager = backgroundTaskManager
    )

    private fun currentWord(): WordDetails = WordDetails(
        id = 1L,
        dictionaryId = 1L,
        spelling = "adapt",
        normalizedSpelling = "adapt"
    )
}
