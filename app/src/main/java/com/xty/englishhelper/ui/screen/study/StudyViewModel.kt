package com.xty.englishhelper.ui.screen.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.model.EdgeNeighbor
import com.xty.englishhelper.domain.model.EdgeType
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.BrainstormDailyGoal
import com.xty.englishhelper.domain.model.BrainstormProgressResult
import com.xty.englishhelper.domain.model.WordNoteOrganizePayload
import com.xty.englishhelper.domain.plan.PlanAutoProgressTracker
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.WordEdgeNeighborPreview
import com.xty.englishhelper.domain.study.Rating
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
import com.xty.englishhelper.domain.usecase.study.SubmitStudyWordNoteUseCase
import com.xty.englishhelper.domain.usecase.study.StudyWordNoteOutcome
import com.xty.englishhelper.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val getCloudWordExamples: GetCloudWordExamplesUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager,
    private val backgroundTaskRepository: BackgroundTaskRepository,
    private val planAutoProgressTracker: PlanAutoProgressTracker,
    private val getBrainstormDailyGoal: GetBrainstormDailyGoalUseCase,
    private val saveBrainstormDailyGoal: SaveBrainstormDailyGoalUseCase,
    private val updateBrainstormProgress: UpdateBrainstormProgressUseCase,
    private val collectRelatedGroup: CollectRelatedGroupUseCase,
    private val buildBrainstormSession: BuildBrainstormSessionUseCase,
    private val getStudyWordEdgePreviews: GetStudyWordEdgePreviewsUseCase,
    private val searchStudyWordNoteSuggestions: SearchStudyWordNoteSuggestionsUseCase,
    private val submitStudyWordNote: SubmitStudyWordNoteUseCase
) : ViewModel() {
    private companion object {
        const val WORD_NOTE_SUGGESTION_MIN_QUERY_LENGTH = 2
        const val WORD_NOTE_SUGGESTION_DEBOUNCE_MS = 120L
    }

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
    // Brainstorm edge graph: wordId -> Map<neighborId, Set<EdgeType>>
    private var brainstormEdgeMap: Map<Long, Map<Long, Set<EdgeType>>> = emptyMap()
    // wordId -> spelling for displaying related words tag
    private var wordIdToSpelling: Map<Long, String> = emptyMap()
    // wordId -> 簇下标（阶段B 选词产出；供阶段C 簇掌握度、阶段D 面包屑使用）
    private var brainstormClusterOf: Map<Long, Int> = emptyMap()
    // 阶段C：会话内富边邻接（reason/example 记忆钩子 + 选择题干扰项来源）
    private var brainstormDetailedAdj: Map<Long, List<EdgeNeighbor>> = emptyMap()
    // 阶段C：本会话是否开启「关联主动回忆」选择题
    private var brainstormActiveRecall = false
    // 阶段C：簇掌握度（会话内）——每簇总词数 / 已掌握数
    private var clusterTotalsByIndex: Map<Int, Int> = emptyMap()
    private val clusterLearnedByIndex = mutableMapOf<Int, Int>()
    // Guard against re-rating during wait or while reviewWord is in-flight
    private var isProcessingRating = false
    private var cloudExamplesJob: Job? = null
    private var cloudExamplesRequestVersion: Long = 0L
    private val pendingWordNoteTargetWordIds = linkedSetOf<Long>()
    private var wordNoteSuggestionJob: Job? = null
    private var wordNoteSuggestionRequestVersion: Long = 0L

    // Brainstorm daily goal state
    private var brainstormDailyGoal: BrainstormDailyGoal? = null
    private var brainstormGoalReached = false
    private var brainstormFinishGroupWordIds = emptySet<Long>()  // 目标达成后需背完的关联词组
    private var brainstormFinishGroupIndex = 0
    private var brainstormAllWordIds = emptySet<Long>()  // 本次会话所有词 ID
    private var dueWordIds = emptySet<Long>()  // 本次会话中的复习词 ID

    init {
        observeWordNoteSetting()
        observeWordNoteTasks()
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            try {
                // BRAINSTORM mode: show daily goal dialog first
                if (studyMode == StudyMode.BRAINSTORM) {
                    brainstormDailyGoal = getBrainstormDailyGoal()
                    _uiState.update {
                        it.copy(
                            showBrainstormGoalDialog = true,
                            brainstormGoalTarget = brainstormDailyGoal?.targetCount ?: 200
                        )
                    }
                    return@launch  // Wait for user confirmation
                }
                // NORMAL mode: load directly
                loadStudyContent()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, phase = StudyPhase.Finished) }
            }
        }
    }

    private fun observeWordNoteSetting() {
        viewModelScope.launch {
            settingsDataStore.studyWordNoteEnabled.collect { enabled ->
                _uiState.update { state ->
                    state.copy(
                        wordNoteEnabled = enabled,
                        wordNoteInput = if (enabled) state.wordNoteInput else "",
                        wordNoteSuggestions = if (enabled) state.wordNoteSuggestions else emptyList(),
                        wordNoteSuggestionsLoading = if (enabled) state.wordNoteSuggestionsLoading else false,
                        wordNoteSuggestionsExpanded = if (enabled) state.wordNoteSuggestionsExpanded else false,
                        wordNoteMessage = if (enabled) state.wordNoteMessage else null,
                        wordNoteError = if (enabled) state.wordNoteError else null
                    )
                }
                if (!enabled) {
                    cancelWordNoteSuggestionRequest()
                }
                refreshCurrentWordEdgesIfNeeded(enabled)
            }
        }
    }

    private fun observeWordNoteTasks() {
        viewModelScope.launch {
            backgroundTaskRepository.observeTasksByTypes(listOf(BackgroundTaskType.WORD_NOTE_ORGANIZE)).collect { tasks ->
                val currentWord = _uiState.value.currentWord ?: return@collect
                if (pendingWordNoteTargetWordIds.isEmpty()) return@collect

                val relevantTasks = tasks.mapNotNull { task ->
                    val payload = task.payload as? WordNoteOrganizePayload ?: return@mapNotNull null
                    if (payload.sourceWordId != currentWord.id ||
                        payload.targetWordId !in pendingWordNoteTargetWordIds
                    ) {
                        return@mapNotNull null
                    }
                    task to payload
                }
                if (relevantTasks.isEmpty()) return@collect

                val successTasks = relevantTasks.filter { it.first.status == BackgroundTaskStatus.SUCCESS }
                val failedTasks = relevantTasks.filter { it.first.status == BackgroundTaskStatus.FAILED }
                val activeTasks = relevantTasks.filter {
                    it.first.status == BackgroundTaskStatus.PENDING ||
                        it.first.status == BackgroundTaskStatus.RUNNING
                }

                successTasks.forEach { (_, payload) ->
                    pendingWordNoteTargetWordIds.remove(payload.targetWordId)
                }
                failedTasks.forEach { (_, payload) ->
                    pendingWordNoteTargetWordIds.remove(payload.targetWordId)
                }

                if (successTasks.isNotEmpty()) {
                    refreshCurrentWordEdgesIfNeeded(_uiState.value.wordNoteEnabled)
                }

                when {
                    successTasks.isNotEmpty() -> {
                        val successSpellings = successTasks.map { it.second.targetSpelling }
                        val failureCount = failedTasks.size
                        val message = buildWordNoteCompletionMessage(successSpellings, failureCount)
                        _uiState.update { state ->
                            state.copy(
                                wordNoteSubmitting = false,
                                wordNoteMessage = message,
                                wordNoteError = null
                            )
                        }
                    }

                    failedTasks.isNotEmpty() -> {
                        val error = buildWordNoteFailureMessage(failedTasks)
                        _uiState.update { state ->
                            state.copy(
                                wordNoteSubmitting = false,
                                wordNoteError = error,
                                wordNoteMessage = null
                            )
                        }
                    }

                    activeTasks.isNotEmpty() -> {
                        val latestActive = activeTasks.maxByOrNull { it.first.updatedAt } ?: return@collect
                        _uiState.update { state ->
                            state.copy(
                                wordNoteSubmitting = false,
                                wordNoteMessage = "${latestActive.second.targetSpelling} 正在后台整理",
                                wordNoteError = null
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    // Called when user confirms brainstorm goal
    fun confirmBrainstormGoal(targetCount: Int) {
        viewModelScope.launch {
            saveBrainstormDailyGoal(targetCount)
            brainstormDailyGoal = brainstormDailyGoal?.copy(targetCount = targetCount)
            _uiState.update { it.copy(showBrainstormGoalDialog = false) }
            loadStudyContent()
        }
    }

    private suspend fun loadStudyContent() {
        try {
            // Get due words (most overdue first) and new words
            val dueWords = getDueWords(unitIds, studyMode)
            val newWords = getNewWords(unitIds, studyMode)
            sessionHasDueWords = dueWords.isNotEmpty()
            sessionHasNewWords = newWords.isNotEmpty()
            sessionProgressTracked = false

            queue.clear()

            val finalOrder: List<WordDetails> = if (studyMode == StudyMode.BRAINSTORM) {
                // 阶段B：质量门槛 + 关联评分 + 学习簇装配 + 新词锚定已知词。
                val clusterSize = settingsDataStore.getBrainstormClusterSize()
                val minConfidence = settingsDataStore.getBrainstormQualityMinConfidence().toDouble()
                val session = buildBrainstormSession(dueWords, newWords, clusterSize, minConfidence)

                brainstormClusterOf = session.clusterOf
                brainstormDetailedAdj = session.gatedAdjacency
                brainstormActiveRecall = settingsDataStore.getBrainstormActiveRecall()
                clusterTotalsByIndex = session.orderedWords
                    .groupingBy { session.clusterOf[it.id] ?: -1 }
                    .eachCount()
                clusterLearnedByIndex.clear()
                val gated = session.gatedAdjacency
                // 关联词标签（拼写）与边类型预览：均从会话内、已过滤的邻接派生。
                brainstormRelated = gated.mapValues { (_, ns) -> ns.map { it.neighborId }.toSet() }
                brainstormEdgeMap = gated.mapValues { (_, ns) ->
                    ns.groupBy { it.neighborId }.mapValues { (_, list) -> list.map { it.type }.toSet() }
                }
                dueWordIds = dueWords.map { it.id }.toSet()
                brainstormAllWordIds = session.orderedWords.map { it.id }.toSet()
                wordIdToSpelling = session.orderedWords.associate { it.id to it.spelling }
                session.orderedWords
            } else {
                // NORMAL：到期词在前、新词在后，去重。
                val seen = mutableSetOf<Long>()
                val ordered = mutableListOf<WordDetails>()
                for (word in dueWords) if (seen.add(word.id)) ordered.add(word)
                for (word in newWords) if (seen.add(word.id)) ordered.add(word)
                ordered
            }

            finalOrder.forEach { queue.addLast(QueueEntry(it)) }
            totalUniqueWords = queue.size

            _uiState.update {
                it.copy(
                    studyMode = studyMode,
                    brainstormTargetCount = brainstormDailyGoal?.targetCount ?: 0
                )
            }

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

    private suspend fun showNextWord() {
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
            val noteEnabled = settingsDataStore.getStudyWordNoteEnabled()
            val relatedSpellings = if (studyMode == StudyMode.BRAINSTORM) {
                val related = brainstormRelated[entry.word.id] ?: emptySet()
                related.take(5).mapNotNull { wordIdToSpelling[it] }
            } else {
                emptyList()
            }
            val edgePreviews = buildCurrentWordEdges(entry.word, noteEnabled)

            // 阶段C：当前词所在簇的掌握进度 + 记忆钩子 + 是否出选择题
            val clusterIdx = brainstormClusterOf[entry.word.id]
            val clusterTotal = clusterIdx?.let { clusterTotalsByIndex[it] } ?: 0
            val clusterLearned = clusterIdx?.let { clusterLearnedByIndex[it] } ?: 0
            val hook = if (studyMode == StudyMode.BRAINSTORM) buildHook(entry.word.id) else null
            val quiz = if (studyMode == StudyMode.BRAINSTORM && brainstormActiveRecall) {
                buildQuiz(entry.word)
            } else {
                null
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
                    currentWordEdges = edgePreviews,
                    wordNoteEnabled = noteEnabled,
                    wordNoteInput = "",
                    wordNoteSuggestions = emptyList(),
                    wordNoteSuggestionsLoading = false,
                    wordNoteSuggestionsExpanded = false,
                    wordNoteSubmitting = false,
                    wordNoteMessage = null,
                    wordNoteError = null,
                    brainstormClusterLearned = clusterLearned,
                    brainstormClusterTotal = clusterTotal,
                    currentWordHook = hook,
                    brainstormQuiz = quiz,
                    cloudExamplesLoading = false,
                    cloudExamples = emptyList(),
                    cloudExamplesError = null
                )
            }
            pendingWordNoteTargetWordIds.clear()
            cancelWordNoteSuggestionRequest()
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
                if (studyMode == StudyMode.BRAINSTORM) {
                    // Track brainstorm session for plan module
                    val totalLearned = _uiState.value.brainstormLearnedCount
                    planAutoProgressTracker.onBrainstormSessionCompleted(
                        unitIds = unitIds,
                        totalWordsLearned = totalLearned
                    )
                } else {
                    planAutoProgressTracker.onStudySessionCompleted(
                        unitIds = unitIds,
                        studyMode = studyMode,
                        hasDueWords = sessionHasDueWords,
                        hasNewWords = sessionHasNewWords
                    )
                }
            }
        }
    }

    fun onRevealAnswer() {
        if (_uiState.value.phase != StudyPhase.Studying) return
        val word = _uiState.value.currentWord ?: return
        viewModelScope.launch {
            val intervals = previewIntervals(word.id, studyMode)
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
                val result = reviewWord(word.id, rating, studyMode)

                when (rating) {
                    Rating.Again -> againCount++
                    Rating.Hard -> hardCount++
                    Rating.Good -> goodCount++
                    Rating.Easy -> easyCount++
                }

                if (rating == Rating.Again) {
                    // Re-queue with the scheduled due time so the word waits
                    queue.addLast(QueueEntry(word, dueAt = result.due))
                    // 阶段C：把最强关联词拉到队首，先以关联作记忆钩子复现，再回到这个难词
                    if (studyMode == StudyMode.BRAINSTORM) pullStrongestAssociateForward(word.id)
                } else {
                    // Mark as processed
                    processedWordIds.add(word.id)

                    // Brainstorm: update progress
                    if (studyMode == StudyMode.BRAINSTORM) {
                        // 阶段C：簇掌握度 +1（每词仅在首次非「重来」时计入）
                        brainstormClusterOf[word.id]?.let { idx ->
                            clusterLearnedByIndex[idx] = (clusterLearnedByIndex[idx] ?: 0) + 1
                        }
                        val isDueWord = word.id in dueWordIds
                        val progressResult = updateBrainstormProgress.onWordLearned(isDueWord)
                        val (learned, target) = when (progressResult) {
                            is BrainstormProgressResult.InProgress -> progressResult.learned to progressResult.target
                            is BrainstormProgressResult.GoalReached -> progressResult.learned to progressResult.target
                            is BrainstormProgressResult.NotStarted -> 0 to 0
                        }
                        _uiState.update {
                            it.copy(
                                brainstormLearnedCount = learned,
                                brainstormTargetCount = target,
                                brainstormDueLearned = if (isDueWord) it.brainstormDueLearned + 1 else it.brainstormDueLearned,
                                brainstormNewLearned = if (!isDueWord) it.brainstormNewLearned + 1 else it.brainstormNewLearned
                            )
                        }
                        if (progressResult is BrainstormProgressResult.GoalReached && !brainstormGoalReached) {
                            brainstormGoalReached = true
                            // Collect current related group (words not yet processed)
                            val relatedGroup = collectRelatedGroup(
                                dictionaryId = word.dictionaryId,
                                startWordId = word.id,
                                wordIds = brainstormAllWordIds,
                                processedWordIds = processedWordIds
                            )
                            brainstormFinishGroupWordIds = relatedGroup.toSet()
                            if (relatedGroup.isEmpty()) {
                                showGoalReachedDialog()
                            }
                            // Otherwise continue studying; counter checked below
                        } else if (brainstormGoalReached && word.id in brainstormFinishGroupWordIds) {
                            // Only count words that are part of the related group
                            brainstormFinishGroupIndex++
                            if (brainstormFinishGroupIndex >= brainstormFinishGroupWordIds.size) {
                                showGoalReachedDialog()
                            }
                        }
                    }
                }

                showNextWord()
            } catch (e: Exception) {
                isProcessingRating = false
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private fun showGoalReachedDialog() {
        _uiState.update { it.copy(showBrainstormGoalReachedDialog = true) }
    }

    fun onContinueAfterGoal() {
        viewModelScope.launch {
            updateBrainstormProgress.onContinueAfterGoal()
            // Defensive reset: DB isCompleted=true prevents re-entry, but clear stale state
            brainstormGoalReached = false
            brainstormFinishGroupWordIds = emptySet()
            brainstormFinishGroupIndex = 0
            _uiState.update { it.copy(showBrainstormGoalReachedDialog = false) }
        }
    }

    fun onExitAfterGoal() {
        viewModelScope.launch {
            updateBrainstormProgress.onExitAfterGoal()
            _uiState.update {
                it.copy(
                    showBrainstormGoalReachedDialog = false,
                    phase = StudyPhase.Finished,
                    stats = buildStats()
                )
            }
        }
    }

    private fun selectDisplayEdgeType(types: Set<EdgeType>): EdgeType {
        val priority = listOf(
            EdgeType.LEARNING_CONFUSABLE,
            EdgeType.FORM_SPELLING,
            EdgeType.SEMANTIC_SYNONYM,
            EdgeType.SEMANTIC_ANTONYM,
            EdgeType.FAMILY_SAME_ROOT
        )
        return priority.firstOrNull { it in types } ?: types.first()
    }

    private suspend fun buildCurrentWordEdges(
        word: WordDetails,
        wordNoteEnabled: Boolean
    ): List<WordEdgePreview> {
        val brainstormEdges = if (studyMode == StudyMode.BRAINSTORM) {
            val neighbors = brainstormEdgeMap[word.id] ?: emptyMap()
            neighbors.entries
                .sortedByDescending { it.value.size }
                .take(5)
                .mapNotNull { (neighborId, edgeTypes) ->
                    val spelling = wordIdToSpelling[neighborId] ?: return@mapNotNull null
                    WordEdgePreview(
                        wordId = neighborId,
                        spelling = spelling,
                        edgeType = selectDisplayEdgeType(edgeTypes)
                    )
                }
        } else {
            emptyList()
        }
        if (!wordNoteEnabled) {
            return if (studyMode == StudyMode.BRAINSTORM) brainstormEdges else emptyList()
        }

        val confirmedEdges = getStudyWordEdgePreviews(
            dictionaryId = word.dictionaryId,
            wordId = word.id,
            minConfidence = 1.0
        ).map { preview ->
            WordEdgePreview(
                wordId = preview.neighborId,
                spelling = preview.spelling,
                edgeType = selectDisplayEdgeType(preview.edgeTypes)
            )
        }

        return if (studyMode == StudyMode.BRAINSTORM) {
            mergeEdgePreviews(brainstormEdges, confirmedEdges)
        } else {
            confirmedEdges.take(8)
        }
    }

    private fun mergeEdgePreviews(
        primary: List<WordEdgePreview>,
        secondary: List<WordEdgePreview>
    ): List<WordEdgePreview> {
        val merged = LinkedHashMap<String, WordEdgePreview>()
        (primary + secondary).forEach { preview ->
            merged.putIfAbsent(preview.spelling.lowercase(), preview)
        }
        return merged.values.take(8)
    }

    private fun refreshCurrentWordEdgesIfNeeded(wordNoteEnabled: Boolean) {
        val currentWord = _uiState.value.currentWord ?: return
        val state = _uiState.value
        if (state.phase != StudyPhase.Studying || state.showAnswer) return
        viewModelScope.launch {
            val refreshed = buildCurrentWordEdges(currentWord, wordNoteEnabled)
            if (_uiState.value.currentWord?.id != currentWord.id || _uiState.value.showAnswer) return@launch
            _uiState.update {
                it.copy(currentWordEdges = refreshed)
            }
        }
    }

    // ── 阶段C：记忆钩子 / 选择题 / Again 拉钩 ──

    /** 关系的简短可读标签（用于钩子与选择题题干）。 */
    private fun relationLabel(type: EdgeType): Int = when (type) {
        EdgeType.SEMANTIC_SYNONYM -> R.string.edge_type_synonym
        EdgeType.SEMANTIC_ANTONYM -> R.string.edge_type_antonym
        EdgeType.LEARNING_CONFUSABLE -> R.string.edge_type_confusable
        EdgeType.LEARNING_MISUSE_PAIR -> R.string.edge_type_misuse
        EdgeType.FORM_SPELLING -> R.string.edge_type_spelling
        EdgeType.FORM_MINIMAL_PAIR -> R.string.edge_type_minimal_pair
        EdgeType.FAMILY_SAME_ROOT -> R.string.edge_type_same_root
        EdgeType.FAMILY_DERIVATION -> R.string.edge_type_derivation
        else -> R.string.common_error
    }

    private fun hookScore(n: EdgeNeighbor): Double =
        n.relationStrength.coerceIn(1, 5) * n.confidence.coerceIn(0.0, 1.0) * n.learningValue.coerceIn(1, 5)

    /** 揭示答案时的记忆钩子：取当前词最强、且带关系依据 / 例句的关联。 */
    private fun buildHook(wordId: Long): BrainstormHook? {
        val best = brainstormDetailedAdj[wordId].orEmpty()
            .filter { !it.reason.isNullOrBlank() || !it.exampleSentence.isNullOrBlank() }
            .maxByOrNull { hookScore(it) } ?: return null
        val spelling = wordIdToSpelling[best.neighborId] ?: return null
        return BrainstormHook(
            relatedSpelling = spelling,
            relationLabel = relationLabel(best.type),
            reason = best.reason?.takeIf { it.isNotBlank() },
            example = best.exampleSentence?.takeIf { it.isNotBlank() }
        )
    }

    /** 关联主动回忆选择题：对有明确近义 / 反义 / 易混 / 形近关联且能凑足 3 个干扰项的词出题。 */
    private fun buildQuiz(word: WordDetails): BrainstormQuiz? {
        val quizableTypes = setOf(
            EdgeType.SEMANTIC_SYNONYM,
            EdgeType.SEMANTIC_ANTONYM,
            EdgeType.LEARNING_CONFUSABLE,
            EdgeType.FORM_SPELLING
        )
        val correct = brainstormDetailedAdj[word.id].orEmpty()
            .filter { it.type in quizableTypes && wordIdToSpelling.containsKey(it.neighborId) }
            .maxByOrNull { hookScore(it) } ?: return null
        val correctSpelling = wordIdToSpelling[correct.neighborId] ?: return null

        // 干扰项：会话内其他词，排除目标词、正确项以及目标词的全部关联词（避免「也算对」歧义）。
        val excluded = (brainstormRelated[word.id] ?: emptySet()) + word.id + correct.neighborId
        val distractors = wordIdToSpelling.keys
            .filter { it !in excluded }
            .shuffled()
            .take(3)
            .mapNotNull { id -> wordIdToSpelling[id]?.let { BrainstormQuizOption(id, it) } }
        if (distractors.size < 3) return null

        val options = (distractors + BrainstormQuizOption(correct.neighborId, correctSpelling)).shuffled()
        return BrainstormQuiz(
            targetSpelling = word.spelling,
            relationLabel = relationLabel(correct.type),
            options = options,
            correctWordId = correct.neighborId
        )
    }

    /** 评「重来」后，把该词最强、尚未背过的关联词拉到队首先复现，作为记忆锚点。 */
    private fun pullStrongestAssociateForward(wordId: Long) {
        val bestNeighborId = brainstormDetailedAdj[wordId].orEmpty()
            .filter { it.neighborId !in processedWordIds }
            .maxByOrNull { hookScore(it) }
            ?.neighborId ?: return
        val idx = queue.indexOfFirst { it.word.id == bestNeighborId }
        if (idx > 0) {
            val entry = queue.removeAt(idx)
            queue.addFirst(entry.copy(dueAt = 0))
        }
    }

    /** 用户选中选择题选项（仅标记选择并显示对错，等「继续」再应用评分）。 */
    fun onQuizAnswer(optionWordId: Long) {
        val quiz = _uiState.value.brainstormQuiz ?: return
        if (quiz.answered) return
        _uiState.update { it.copy(brainstormQuiz = quiz.copy(selectedWordId = optionWordId)) }
    }

    /** 选择题答完后继续：答对记「良好」、答错记「重来」，按普通 FSRS 评分推进。 */
    fun onQuizContinue() {
        val quiz = _uiState.value.brainstormQuiz ?: return
        if (!quiz.answered) return
        val rating = if (quiz.isCorrect) Rating.Good else Rating.Again
        _uiState.update { it.copy(brainstormQuiz = null) }
        onRate(rating)
    }

    fun onWordNoteInputChange(value: String) {
        _uiState.update {
            it.copy(
                wordNoteInput = value,
                wordNoteSuggestionsExpanded = value.isNotBlank(),
                wordNoteError = null,
                wordNoteMessage = null
            )
        }
        requestWordNoteSuggestions(value)
    }

    fun onWordNoteSuggestionSelected(suggestion: String) {
        cancelWordNoteSuggestionRequest()
        _uiState.update {
            it.copy(
                wordNoteInput = suggestion,
                wordNoteSuggestionsExpanded = false,
                wordNoteError = null,
                wordNoteMessage = null
            )
        }
    }

    fun setWordNoteSuggestionsExpanded(expanded: Boolean) {
        _uiState.update { state ->
            state.copy(
                wordNoteSuggestionsExpanded = expanded && state.wordNoteSuggestions.isNotEmpty()
            )
        }
    }

    fun submitWordNote() {
        val state = _uiState.value
        if (!state.wordNoteEnabled || state.wordNoteSubmitting) return
        val currentWord = state.currentWord ?: return
        cancelWordNoteSuggestionRequest()
        _uiState.update {
            it.copy(
                wordNoteSuggestions = emptyList(),
                wordNoteSuggestionsLoading = false,
                wordNoteSuggestionsExpanded = false
            )
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    wordNoteSubmitting = true,
                    wordNoteError = null,
                    wordNoteMessage = null
                )
            }
            try {
                val result = submitStudyWordNote(
                    currentWord = currentWord,
                    rawInput = state.wordNoteInput,
                    fallbackUnitIds = unitIds
                )
                if (_uiState.value.currentWord?.id != currentWord.id) return@launch

                when (result.outcome) {
                    StudyWordNoteOutcome.PROMOTED -> pendingWordNoteTargetWordIds.remove(result.relatedWordId)
                    StudyWordNoteOutcome.QUEUED,
                    StudyWordNoteOutcome.ALREADY_QUEUED -> pendingWordNoteTargetWordIds.add(result.relatedWordId)
                }

                val refreshedEdges = if (result.outcome == StudyWordNoteOutcome.PROMOTED) {
                    buildCurrentWordEdges(currentWord, _uiState.value.wordNoteEnabled)
                } else {
                    _uiState.value.currentWordEdges
                }

                _uiState.update {
                    it.copy(
                        currentWordEdges = refreshedEdges,
                        wordNoteInput = "",
                        wordNoteSuggestions = emptyList(),
                        wordNoteSuggestionsLoading = false,
                        wordNoteSuggestionsExpanded = false,
                        wordNoteSubmitting = false,
                        wordNoteMessage = result.message,
                        wordNoteError = null
                    )
                }
            } catch (e: Exception) {
                if (_uiState.value.currentWord?.id != currentWord.id) return@launch
                _uiState.update {
                    it.copy(
                        wordNoteSubmitting = false,
                        wordNoteError = e.message ?: "单词便签处理失败",
                        wordNoteMessage = null
                    )
                }
            }
        }
    }

    private fun requestWordNoteSuggestions(rawInput: String) {
        val currentWord = _uiState.value.currentWord
        if (!_uiState.value.wordNoteEnabled || currentWord == null) {
            clearWordNoteSuggestions()
            return
        }

        val normalizedQuery = rawInput.trim().lowercase()
        if (normalizedQuery.length < WORD_NOTE_SUGGESTION_MIN_QUERY_LENGTH) {
            clearWordNoteSuggestions()
            return
        }

        cancelWordNoteSuggestionRequest()
        val requestVersion = ++wordNoteSuggestionRequestVersion
        _uiState.update {
            it.copy(
                wordNoteSuggestions = emptyList(),
                wordNoteSuggestionsLoading = true,
                wordNoteSuggestionsExpanded = true
            )
        }

        wordNoteSuggestionJob = viewModelScope.launch {
            delay(WORD_NOTE_SUGGESTION_DEBOUNCE_MS)
            runCatching {
                searchStudyWordNoteSuggestions(
                    currentWord = currentWord,
                    rawInput = normalizedQuery
                )
            }.onSuccess { suggestions ->
                val state = _uiState.value
                if (requestVersion != wordNoteSuggestionRequestVersion ||
                    state.currentWord?.id != currentWord.id ||
                    state.wordNoteInput.trim().lowercase() != normalizedQuery
                ) {
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        wordNoteSuggestions = suggestions,
                        wordNoteSuggestionsLoading = false,
                        wordNoteSuggestionsExpanded = suggestions.isNotEmpty() && it.wordNoteSuggestionsExpanded
                    )
                }
            }.onFailure {
                val state = _uiState.value
                if (requestVersion != wordNoteSuggestionRequestVersion ||
                    state.currentWord?.id != currentWord.id ||
                    state.wordNoteInput.trim().lowercase() != normalizedQuery
                ) {
                    return@onFailure
                }
                _uiState.update {
                    it.copy(
                        wordNoteSuggestions = emptyList(),
                        wordNoteSuggestionsLoading = false,
                        wordNoteSuggestionsExpanded = false
                    )
                }
            }
        }
    }

    private fun clearWordNoteSuggestions() {
        cancelWordNoteSuggestionRequest()
        _uiState.update {
            it.copy(
                wordNoteSuggestions = emptyList(),
                wordNoteSuggestionsLoading = false,
                wordNoteSuggestionsExpanded = false
            )
        }
    }

    private fun cancelWordNoteSuggestionRequest() {
        wordNoteSuggestionJob?.cancel()
        wordNoteSuggestionJob = null
        wordNoteSuggestionRequestVersion++
    }

    private fun buildWordNoteCompletionMessage(
        successSpellings: List<String>,
        failureCount: Int
    ): String {
        val successPart = if (successSpellings.size == 1) {
            "${successSpellings.first()} 已加入强关联"
        } else {
            "已加入 ${successSpellings.size} 个强关联词"
        }
        return if (failureCount > 0) {
            "$successPart，另有 $failureCount 个整理失败"
        } else {
            successPart
        }
    }

    private fun buildWordNoteFailureMessage(
        failedTasks: List<Pair<com.xty.englishhelper.domain.model.BackgroundTask, WordNoteOrganizePayload>>
    ): String {
        if (failedTasks.size == 1) {
            val (task, payload) = failedTasks.first()
            return task.errorMessage ?: "${payload.targetSpelling} 后台整理失败"
        }
        return "${failedTasks.size} 个关联词后台整理失败"
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
