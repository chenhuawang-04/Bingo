package com.xty.englishhelper.ui.screen.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.plan.PlanAutoProgressTracker
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.domain.study.Rating
import com.xty.englishhelper.domain.usecase.dictionary.GetCloudWordExamplesUseCase
import com.xty.englishhelper.domain.usecase.study.GetDueWordsUseCase
import com.xty.englishhelper.domain.usecase.study.GetNewWordsUseCase
import com.xty.englishhelper.domain.usecase.study.PreviewIntervalsUseCase
import com.xty.englishhelper.domain.usecase.study.ReviewWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A queued word with its earliest eligible display time.
 * [dueAt] is 0 for initial queue entries (show immediately),
 * or a future timestamp used as a soft ordering hint after an Again rating.
 */
private data class QueueEntry(
    val word: WordDetails,
    val dueAt: Long = 0
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDueWords: GetDueWordsUseCase,
    private val getNewWords: GetNewWordsUseCase,
    private val reviewWord: ReviewWordUseCase,
    private val previewIntervals: PreviewIntervalsUseCase,
    private val wordPoolRepository: WordPoolRepository,
    private val getCloudWordExamples: GetCloudWordExamplesUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager,
    private val planAutoProgressTracker: PlanAutoProgressTracker
) : ViewModel() {

    private val unitIdsStr: String = savedStateHandle["unitIds"] ?: ""
    private val unitIds: List<Long> = unitIdsStr.split(",").mapNotNull { it.toLongOrNull() }
    private val modeStr: String = savedStateHandle["mode"] ?: "NORMAL"
    private val studyMode: StudyMode = runCatching { StudyMode.valueOf(modeStr) }.getOrDefault(StudyMode.NORMAL)

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private val queue = ArrayDeque<QueueEntry>()
    private val processedWordIds = mutableSetOf<Long>()
    private var againCount = 0
    private var hardCount = 0
    private var goodCount = 0
    private var easyCount = 0
    private var totalUniqueWords = 0
    private var sessionHasDueWords = false
    private var sessionHasNewWords = false
    private var sessionProgressTracked = false

    // Brainstorm data: wordId -> set of related wordIds (via pool union)
    private var brainstormRelated: Map<Long, Set<Long>> = emptyMap()
    // wordId -> spelling for displaying related words tag
    private var wordIdToSpelling: Map<Long, String> = emptyMap()
    // Guard against re-rating during wait or while reviewWord is in-flight
    private var isProcessingRating = false
    private var cloudExamplesJob: Job? = null
    private var cloudExamplesRequestVersion: Long = 0L

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            try {
                // Get due words (most overdue first) and new words
                val dueWords = getDueWords(unitIds)
                val newWords = getNewWords(unitIds)
                sessionHasDueWords = dueWords.isNotEmpty()
                sessionHasNewWords = newWords.isNotEmpty()
                sessionProgressTracked = false

                // Build queue: due words first, then new words
                queue.clear()
                val addedIds = mutableSetOf<Long>()
                val orderedWords = mutableListOf<WordDetails>()
                for (word in dueWords) {
                    if (word.id !in addedIds) {
                        orderedWords.add(word)
                        addedIds.add(word.id)
                    }
                }
                for (word in newWords) {
                    if (word.id !in addedIds) {
                        orderedWords.add(word)
                        addedIds.add(word.id)
                    }
                }

                // Build spelling lookup for brainstorm tags
                wordIdToSpelling = orderedWords.associate { it.id to it.spelling }

                // Apply brainstorm reordering if needed
                val finalOrder = if (studyMode == StudyMode.BRAINSTORM && orderedWords.isNotEmpty()) {
                    val dictionaryId = orderedWords.first().dictionaryId
                    applyBrainstormOrder(orderedWords, dictionaryId)
                } else {
                    orderedWords
                }

                finalOrder.forEach { queue.addLast(QueueEntry(it)) }
                totalUniqueWords = queue.size

                _uiState.update { it.copy(studyMode = studyMode) }

                if (queue.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            phase = StudyPhase.Finished,
                            stats = StudyStats(totalWords = 0)
                        )
                    }
                } else {
                    showNextWord()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, phase = StudyPhase.Finished) }
            }
        }
    }

    private suspend fun applyBrainstormOrder(
        words: List<WordDetails>,
        dictionaryId: Long
    ): List<WordDetails> {
        return try {
            val wordToPoolsMap = wordPoolRepository.getWordToPoolsMap(dictionaryId)
            val poolToMembersMap = wordPoolRepository.getPoolToMembersMap(dictionaryId)

            // Build wordId -> set of all related wordIds (union of all pools)
            val relatedMap = mutableMapOf<Long, MutableSet<Long>>()
            wordToPoolsMap.forEach { (wordId, poolIds) ->
                val related = mutableSetOf<Long>()
                poolIds.forEach { poolId ->
                    poolToMembersMap[poolId]?.forEach { memberId ->
                        if (memberId != wordId) related.add(memberId)
                    }
                }
                relatedMap[wordId] = related
            }
            brainstormRelated = relatedMap

            // Stable insertion algorithm
            val wordMap = words.associateBy { it.id }
            val remainingIds = words.map { it.id }
            val emittedIds = mutableSetOf<Long>()
            val output = mutableListOf<WordDetails>()

            for (wId in remainingIds) {
                if (wId in emittedIds) continue
                val word = wordMap[wId] ?: continue
                output.add(word)
                emittedIds.add(wId)

                // Insert related words that are in the queue, maintaining their original relative order
                val related = relatedMap[wId] ?: emptySet()
                for (rId in remainingIds) {
                    if (rId in emittedIds) continue
                    if (rId in related) {
                        val rWord = wordMap[rId] ?: continue
                        output.add(rWord)
                        emittedIds.add(rId)
                    }
                }
            }

            output
        } catch (e: Exception) {
            // Fall back to original order if pool data unavailable
            words
        }
    }

    private fun showNextWord() {
        if (queue.isEmpty()) {
            isProcessingRating = false
            trackStudySessionProgressIfNeeded()
            _uiState.update {
                it.copy(
                    phase = StudyPhase.Finished,
                    stats = buildStats()
                )
            }
            return
        }

        val now = System.currentTimeMillis()

        // Prefer entries whose soft due time has arrived.
        val readyIndex = queue.indexOfFirst { it.dueAt <= now }
        val nextIndex = if (readyIndex >= 0) {
            readyIndex
        } else {
            // When all cards were rated Again, the queue can contain only future-due entries.
            // Fall back to the earliest one instead of blocking the whole session behind a spinner.
            queue.withIndex().minByOrNull { it.value.dueAt }?.index ?: -1
        }

        if (nextIndex >= 0) {
            isProcessingRating = false
            val entry = queue.removeAt(nextIndex)

            // Compute related spellings for brainstorm tag
            val relatedSpellings = if (studyMode == StudyMode.BRAINSTORM) {
                val related = brainstormRelated[entry.word.id] ?: emptySet()
                related.take(5).mapNotNull { wordIdToSpelling[it] }
            } else {
                emptyList()
            }

            _uiState.update {
                it.copy(
                    phase = StudyPhase.Studying,
                    currentWord = entry.word,
                    showAnswer = false,
                    previewIntervals = emptyMap(),
                    progress = processedWordIds.size,
                    total = totalUniqueWords,
                    stats = buildStats(),
                    currentWordRelatedSpellings = relatedSpellings,
                    cloudExamplesLoading = false,
                    cloudExamples = emptyList(),
                    cloudExamplesError = null
                )
            }
            cloudExamplesJob?.cancel()

            // Auto-speak the word when it appears
            viewModelScope.launch {
                val enabled = settingsDataStore.ttsAutoStudy.first()
                if (enabled) {
                    ttsManager.speakWord(entry.word.id, entry.word.spelling)
                }
            }
        }
    }

    private fun trackStudySessionProgressIfNeeded() {
        if (sessionProgressTracked) return
        sessionProgressTracked = true
        viewModelScope.launch {
            runCatching {
                planAutoProgressTracker.onStudySessionCompleted(
                    unitIds = unitIds,
                    studyMode = studyMode,
                    hasDueWords = sessionHasDueWords,
                    hasNewWords = sessionHasNewWords
                )
            }
        }
    }

    fun onRevealAnswer() {
        if (_uiState.value.phase != StudyPhase.Studying) return
        val word = _uiState.value.currentWord ?: return
        viewModelScope.launch {
            val intervals = previewIntervals(word.id)
            _uiState.update {
                it.copy(
                    showAnswer = true,
                    previewIntervals = intervals
                )
            }
            if (_uiState.value.cloudExamples.isEmpty() && !_uiState.value.cloudExamplesLoading) {
                loadCloudExamples(word.spelling, _uiState.value.cloudExampleSource)
            }
        }
    }

    fun onRate(rating: Rating) {
        if (isProcessingRating) return
        val word = _uiState.value.currentWord ?: return
        isProcessingRating = true
        viewModelScope.launch {
            try {
                val result = reviewWord(word.id, rating)

                when (rating) {
                    Rating.Again -> againCount++
                    Rating.Hard -> hardCount++
                    Rating.Good -> goodCount++
                    Rating.Easy -> easyCount++
                }

                if (rating == Rating.Again) {
                    // Re-queue with the scheduled due time so the word waits
                    queue.addLast(QueueEntry(word, dueAt = result.due))
                } else {
                    // Only count as fully processed when not re-queued
                    processedWordIds.add(word.id)
                }

                showNextWord()
            } catch (e: Exception) {
                isProcessingRating = false
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun buildStats(): StudyStats {
        return StudyStats(
            totalWords = totalUniqueWords,
            againCount = againCount,
            hardCount = hardCount,
            goodCount = goodCount,
            easyCount = easyCount
        )
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun selectCloudExampleSource(source: CloudExampleSource) {
        val state = _uiState.value
        if (state.cloudExampleSource == source) return
        _uiState.update {
            it.copy(
                cloudExampleSource = source,
                cloudExamples = emptyList(),
                cloudExamplesError = null
            )
        }
        val currentWord = state.currentWord ?: return
        if (state.showAnswer) {
            loadCloudExamples(currentWord.spelling, source)
        }
    }

    private fun loadCloudExamples(word: String, source: CloudExampleSource) {
        cloudExamplesJob?.cancel()
        val requestVersion = ++cloudExamplesRequestVersion
        cloudExamplesJob = viewModelScope.launch {
            _uiState.update { it.copy(cloudExamplesLoading = true, cloudExamplesError = null) }
            runCatching {
                getCloudWordExamples(word = word, source = source)
            }.onSuccess { examples ->
                val currentState = _uiState.value
                if (requestVersion != cloudExamplesRequestVersion ||
                    currentState.currentWord?.spelling != word ||
                    currentState.cloudExampleSource != source
                ) {
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        cloudExamples = examples,
                        cloudExamplesLoading = false,
                        cloudExamplesError = null
                    )
                }
            }.onFailure { error ->
                val currentState = _uiState.value
                if (requestVersion != cloudExamplesRequestVersion ||
                    currentState.currentWord?.spelling != word ||
                    currentState.cloudExampleSource != source
                ) {
                    return@onFailure
                }
                _uiState.update {
                    it.copy(
                        cloudExamples = emptyList(),
                        cloudExamplesLoading = false,
                        cloudExamplesError = error.message
                    )
                }
            }
        }
    }
}
