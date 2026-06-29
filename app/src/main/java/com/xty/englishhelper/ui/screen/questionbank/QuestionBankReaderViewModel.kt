package com.xty.englishhelper.ui.screen.questionbank

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.image.ImageCompressionManager
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.AnswerSource
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.CollectedWord
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.DifficultyLevel
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.PracticeRecord
import com.xty.englishhelper.domain.model.QuestionAnswerGeneratePayload
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.QuestionSourceVerifyPayload
import com.xty.englishhelper.domain.model.QuestionWritingSamplePayload
import com.xty.englishhelper.domain.model.SourceVerifyStatus
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.TtsState
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.plan.PlanAutoProgressTracker
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.TranslationScore
import com.xty.englishhelper.domain.repository.TranslationScoreInput
import com.xty.englishhelper.domain.repository.WordPhraseRepository
import com.xty.englishhelper.domain.repository.WritingScore
import com.xty.englishhelper.domain.usecase.article.AnalyzeParagraphUseCase
import com.xty.englishhelper.domain.usecase.article.QuickAnalyzeWordUseCase
import com.xty.englishhelper.domain.usecase.article.ScanWordLinksUseCase
import com.xty.englishhelper.domain.usecase.article.TranslateParagraphUseCase
import com.xty.englishhelper.domain.usecase.questionbank.questionBankContentId
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.WritingPracticePhraseCandidate
import com.xty.englishhelper.domain.model.WritingPracticePhraseRequirement
import com.xty.englishhelper.domain.model.WritingPracticePhraseUsage
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.organize.BackgroundOrganizeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

data class ReaderUiState(
    val group: QuestionGroup? = null,
    val paperTitle: String = "",
    val paragraphs: List<ArticleParagraph> = emptyList(),
    val items: List<QuestionItem> = emptyList(),
    val wordLinks: List<ArticleWordLink> = emptyList(),
    val wordLinkMap: Map<String, List<ArticleWordLink>> = emptyMap(),
    // Paragraph analysis
    val paragraphAnalysis: Map<Long, ParagraphAnalysisResult> = emptyMap(),
    val analyzingParagraphId: Long = 0L,
    val expandedParagraphIds: Set<Long> = emptySet(),
    // TTS
    val ttsState: TtsState = TtsState(),
    // Translation
    val translationEnabled: Boolean = false,
    val paragraphTranslations: Map<Long, String> = emptyMap(),
    val translatingParagraphIds: Set<Long> = emptySet(),
    val translationFailedParagraphIds: Set<Long> = emptySet(),
    // Collection notebook
    val collectedWords: List<CollectedWord> = emptyList(),
    val showNotebook: Boolean = false,
    val dictionaries: List<Dictionary> = emptyList(),
    // Practice
    val selectedAnswers: Map<Long, String> = emptyMap(),
    val isSubmitted: Boolean = false,
    val isSubmitting: Boolean = false,
    val showingAnswers: Boolean = false,
    val wrongItemIds: Set<Long> = emptySet(),
    val practiceResults: Map<Long, Boolean> = emptyMap(),
    val sentenceInsertionOptions: List<String> = emptyList(),
    val showSentenceOptionsEditor: Boolean = false,
    val sentenceOptionsDraft: String = "",
    // Translation scoring
    val translationScores: Map<Long, TranslationScore> = emptyMap(),
    val isScoringTranslation: Boolean = false,
    // Writing scoring
    val writingScores: Map<Long, WritingScore> = emptyMap(),
    val isScoringWriting: Boolean = false,
    val isOcrWriting: Boolean = false,
    val isSearchingWritingSample: Boolean = false,
    val isSearchingWritingSource: Boolean = false,
    val pendingWritingAutoSubmit: Boolean = false,
    val writingSampleError: String? = null,
    val writingPracticeEnabled: Boolean = false,
    val isPreparingWritingPractice: Boolean = false,
    val writingPracticePhrases: List<WritingPracticePhraseRequirement> = emptyList(),
    val writingPracticeUsage: List<WritingPracticePhraseUsage> = emptyList(),
    val writingPracticeError: String? = null,
    // Source
    val linkedArticleId: Long? = null,
    val isVerifying: Boolean = false,
    val isScanningAnswers: Boolean = false,
    val isCompressingAnswers: Boolean = false,
    val isCompressingWriting: Boolean = false,
    // General
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class QuestionBankReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repository: QuestionBankRepository,
    private val aiRepository: QuestionBankAiRepository,
    private val scanWordLinks: ScanWordLinksUseCase,
    private val analyzeParagraph: AnalyzeParagraphUseCase,
    private val translateParagraph: TranslateParagraphUseCase,
    private val quickAnalyzeWord: QuickAnalyzeWordUseCase,
    private val saveWord: SaveWordUseCase,
    private val ttsManager: TtsManager,
    private val settingsDataStore: SettingsDataStore,
    private val dictionaryRepository: DictionaryRepository,
    private val wordPhraseRepository: WordPhraseRepository,
    private val unitRepository: UnitRepository,
    private val backgroundOrganizeManager: BackgroundOrganizeManager,
    private val backgroundTaskManager: BackgroundTaskManager,
    private val taskRepository: BackgroundTaskRepository,
    private val imageCompressionManager: ImageCompressionManager,
    private val planAutoProgressTracker: PlanAutoProgressTracker
) : ViewModel() {

    private val groupId: Long = savedStateHandle["groupId"] ?: 0L
    private val contentId: Long = questionBankContentId(groupId)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _notebookMessage = MutableSharedFlow<String>(replay = 0)
    val notebookMessage: Flow<String> = _notebookMessage

    private var translationJob: Job? = null
    private var writingPracticeJob: Job? = null

    init {
        observeTtsState()
        observeBackgroundTasks()
        loadData()
        loadDictionaries()
    }

    // ── Data Loading ──

    private fun loadData() {
        viewModelScope.launch {
            try {
                val group = repository.getGroupById(groupId)
                if (group == null) {
                    _uiState.update { it.copy(isLoading = false, error = "题组不存在") }
                    return@launch
                }

                val paragraphs = group.paragraphs.ifEmpty {
                    buildFallbackParagraphs(group.passageText)
                }
                val items = group.items
                val wrongIds = repository.getWrongItemIds(groupId).toSet()
                val linkedId = group.linkedArticleId
                val paperTitle = group.examPaperTitle
                    ?: repository.getExamPaperById(group.examPaperId)?.title.orEmpty()
                val sentenceOptions = if (
                    group.questionType == QuestionType.SENTENCE_INSERTION ||
                    group.questionType == QuestionType.COMMENT_OPINION_MATCH ||
                    group.questionType == QuestionType.SUBHEADING_MATCH ||
                    group.questionType == QuestionType.INFORMATION_MATCH
                ) {
                    parseSentenceInsertionOptions(items)
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        group = group,
                        paperTitle = paperTitle,
                        paragraphs = paragraphs,
                        items = items,
                        wrongItemIds = wrongIds,
                        sentenceInsertionOptions = sentenceOptions,
                        linkedArticleId = linkedId,
                        isLoading = false
                    )
                }

                scanWordLinksAsync(paragraphs)

                // Prewarm TTS
                val texts = paragraphs
                    .filter { it.text.isNotBlank() }
                    .map { it.text }
                if (texts.isNotEmpty()) {
                    ttsManager.prewarmArticle(contentId, texts)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "加载失败：${e.message}") }
            }
        }
    }

    private fun buildFallbackParagraphs(passageText: String): List<ArticleParagraph> {
        return passageText
            .split(Regex("\\n\\s*\\n|\\r\\n\\s*\\r\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, text ->
                ArticleParagraph(
                    id = -1L - index,
                    articleId = contentId,
                    paragraphIndex = index,
                    text = text
                )
            }
    }

    private fun loadDictionaries() {
        viewModelScope.launch {
            dictionaryRepository.getAllDictionaries().collect { dicts ->
                _uiState.update { it.copy(dictionaries = dicts) }
            }
        }
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            ttsManager.state.collect { tts ->
                _uiState.update { it.copy(ttsState = tts) }
            }
        }
    }

    private fun observeBackgroundTasks() {
        viewModelScope.launch {
            var lastAnswerStatus: BackgroundTaskStatus? = null
            var lastVerifyStatus: BackgroundTaskStatus? = null
            var lastSampleStatus: BackgroundTaskStatus? = null
            val taskTypes = listOf(
                BackgroundTaskType.QUESTION_ANSWER_GENERATE,
                BackgroundTaskType.QUESTION_SOURCE_VERIFY,
                BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH
            )
            taskRepository.observeTasksByTypes(taskTypes).collect { tasks ->
                val answerTask = tasks
                    .filter { it.type == BackgroundTaskType.QUESTION_ANSWER_GENERATE }
                    .filter { (it.payload as? QuestionAnswerGeneratePayload)?.groupId == groupId }
                    .maxByOrNull { it.updatedAt }
                val sampleTask = tasks
                    .filter { it.type == BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH }
                    .filter { (it.payload as? QuestionWritingSamplePayload)?.groupId == groupId }
                    .maxByOrNull { it.updatedAt }
                val verifyTask = tasks
                    .filter { it.type == BackgroundTaskType.QUESTION_SOURCE_VERIFY }
                    .filter { (it.payload as? QuestionSourceVerifyPayload)?.groupId == groupId }
                    .maxByOrNull { it.updatedAt }

                val verifying = verifyTask?.status == BackgroundTaskStatus.PENDING ||
                    verifyTask?.status == BackgroundTaskStatus.RUNNING
                if (_uiState.value.isVerifying != verifying) {
                    _uiState.update { it.copy(isVerifying = verifying) }
                }

                val searchingSample = sampleTask?.status == BackgroundTaskStatus.PENDING ||
                    sampleTask?.status == BackgroundTaskStatus.RUNNING
                if (_uiState.value.isSearchingWritingSample != searchingSample) {
                    _uiState.update { it.copy(isSearchingWritingSample = searchingSample) }
                }

                if (answerTask != null && answerTask.status != lastAnswerStatus) {
                    if (answerTask.status == BackgroundTaskStatus.SUCCESS) {
                        refreshItemsAndResults()
                    }
                }
                if (sampleTask != null && sampleTask.status != lastSampleStatus) {
                    if (sampleTask.status == BackgroundTaskStatus.SUCCESS) {
                        refreshItemsAndResults()
                        _uiState.update { it.copy(writingSampleError = null) }
                    } else if (sampleTask.status == BackgroundTaskStatus.FAILED) {
                        val msg = sampleTask.errorMessage ?: "范文检索失败"
                        _uiState.update { it.copy(error = msg, writingSampleError = msg) }
                    }
                }
                if (verifyTask != null && verifyTask.status != lastVerifyStatus) {
                    if (verifyTask.status == BackgroundTaskStatus.FAILED) {
                        _uiState.update {
                            it.copy(error = verifyTask.errorMessage ?: "来源审查失败")
                        }
                    } else if (verifyTask.status == BackgroundTaskStatus.SUCCESS) {
                        _uiState.update { it.copy(error = null) }
                    }
                    if (verifyTask.status != BackgroundTaskStatus.PENDING &&
                        verifyTask.status != BackgroundTaskStatus.RUNNING
                    ) {
                        refreshSourceInfo()
                    }
                }
                lastAnswerStatus = answerTask?.status
                lastVerifyStatus = verifyTask?.status
                lastSampleStatus = sampleTask?.status
            }
        }
    }

    private suspend fun refreshItemsAndResults() {
        val refreshedItems = repository.getItemsByGroup(groupId)
        val wrongIds = repository.getWrongItemIds(groupId).toSet()
        _uiState.update { it.copy(items = refreshedItems, wrongItemIds = wrongIds) }
    }

    private suspend fun refreshSourceInfo() {
        val updatedGroup = repository.getGroupById(groupId)
        _uiState.update { it.copy(group = updatedGroup, linkedArticleId = updatedGroup?.linkedArticleId) }
    }

    // ── Translation ──

    fun toggleTranslation() {
        val enabled = !_uiState.value.translationEnabled
        _uiState.update { it.copy(translationEnabled = enabled) }
        if (enabled) {
            translateAllParagraphs()
        } else {
            translationJob?.cancel()
            translationJob = null
            _uiState.update { it.copy(translatingParagraphIds = emptySet()) }
        }
    }

    private fun translateAllParagraphs() {
        val paragraphs = _uiState.value.paragraphs
        val textParagraphs = paragraphs.filter { it.text.isNotBlank() }
        val alreadyTranslated = _uiState.value.paragraphTranslations.keys

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val config = settingsDataStore.getFastAiConfig()
            if (config.apiKey.isBlank()) {
                _uiState.update { it.copy(error = "请先在设置中配置 API Key") }
                return@launch
            }

            for (paragraph in textParagraphs) {
                ensureActive()
                if (paragraph.id in alreadyTranslated) continue
                _uiState.update { it.copy(translatingParagraphIds = it.translatingParagraphIds + paragraph.id) }
                try {
                    val translation = translateParagraph(
                        contentId, paragraph.id, paragraph.text,
                        config.apiKey, config.model, config.baseUrl, config.provider, false
                    )
                    _uiState.update {
                        it.copy(
                            paragraphTranslations = it.paragraphTranslations + (paragraph.id to translation),
                            translatingParagraphIds = it.translatingParagraphIds - paragraph.id,
                            translationFailedParagraphIds = it.translationFailedParagraphIds - paragraph.id
                        )
                    }
                } catch (_: Exception) {
                    _uiState.update {
                        it.copy(
                            translatingParagraphIds = it.translatingParagraphIds - paragraph.id,
                            translationFailedParagraphIds = it.translationFailedParagraphIds + paragraph.id
                        )
                    }
                }
            }
        }
    }

    fun retryTranslateParagraph(paragraphId: Long, paragraphText: String) {
        viewModelScope.launch {
            val config = settingsDataStore.getFastAiConfig()
            if (config.apiKey.isBlank()) {
                _uiState.update { it.copy(error = "请先在设置中配置 API Key") }
                return@launch
            }
            _uiState.update { it.copy(translatingParagraphIds = it.translatingParagraphIds + paragraphId) }
            try {
                val translation = translateParagraph(
                    contentId, paragraphId, paragraphText,
                    config.apiKey, config.model, config.baseUrl, config.provider, false
                )
                _uiState.update {
                    it.copy(
                        paragraphTranslations = it.paragraphTranslations + (paragraphId to translation),
                        translatingParagraphIds = it.translatingParagraphIds - paragraphId,
                        translationFailedParagraphIds = it.translationFailedParagraphIds - paragraphId
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        translatingParagraphIds = it.translatingParagraphIds - paragraphId,
                        translationFailedParagraphIds = it.translationFailedParagraphIds + paragraphId
                    )
                }
            }
        }
    }

    // ── Paragraph Analysis ──

    fun analyzeParagraph(paragraphId: Long, paragraphText: String) {
        if (_uiState.value.analyzingParagraphId != 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(analyzingParagraphId = paragraphId) }
            try {
                val config = settingsDataStore.getFastAiConfig()
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(analyzingParagraphId = 0L, error = "请先在设置中配置 API Key") }
                    return@launch
                }
                val result = analyzeParagraph(
                    contentId, paragraphId, paragraphText,
                    config.apiKey, config.model, config.baseUrl, config.provider, false
                )
                _uiState.update {
                    it.copy(
                        paragraphAnalysis = it.paragraphAnalysis + (paragraphId to result),
                        analyzingParagraphId = 0L,
                        expandedParagraphIds = it.expandedParagraphIds + paragraphId
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(analyzingParagraphId = 0L, error = "分析失败：${e.message}") }
            }
        }
    }

    fun toggleParagraphAnalysisExpanded(paragraphId: Long) {
        _uiState.update {
            val expanded = if (paragraphId in it.expandedParagraphIds)
                it.expandedParagraphIds - paragraphId
            else
                it.expandedParagraphIds + paragraphId
            it.copy(expandedParagraphIds = expanded)
        }
    }

    // ── TTS ──

    fun toggleSpeakArticle() {
        val ttsState = _uiState.value.ttsState
        val sessionId = ttsManager.articleSessionId(contentId)
        val isCurrent = ttsState.sessionId == sessionId

        if (ttsState.isSpeaking && isCurrent) {
            ttsManager.pause()
            return
        }

        val texts = _uiState.value.paragraphs
            .filter { it.text.isNotBlank() }
            .map { it.text }
        if (texts.isEmpty()) return

        val atEnd = isCurrent &&
            !ttsState.isSpeaking &&
            texts.isNotEmpty() &&
            ttsState.currentIndex >= texts.lastIndex
        val startIndex = if (!isCurrent) 0 else if (atEnd) 0 else ttsState.currentIndex
        viewModelScope.launch {
            ttsManager.speakArticle(contentId, texts, startIndex)
        }
    }

    fun stopSpeaking() { ttsManager.stop() }
    fun nextParagraph() { ttsManager.next() }
    fun previousParagraph() { ttsManager.previous() }
    fun clearTtsError() { ttsManager.clearError() }

    // ── Collection Notebook ──

    fun collectWord(word: String, contextSentence: String) {
        val existing = _uiState.value.collectedWords.find { it.word.equals(word, ignoreCase = true) }
        if (existing != null) {
            viewModelScope.launch {
                _notebookMessage.emit("「$word」已在收纳本中")
            }
            return
        }
        _uiState.update {
            it.copy(collectedWords = it.collectedWords + CollectedWord(word, contextSentence, isAnalyzing = true))
        }
        viewModelScope.launch {
            _notebookMessage.emit("已加入收纳本：$word")
        }
        viewModelScope.launch {
            try {
                val config = settingsDataStore.getFastAiConfig()
                if (config.apiKey.isBlank()) {
                    _uiState.update {
                        it.copy(collectedWords = it.collectedWords.map { cw ->
                            if (cw.word == word) cw.copy(isAnalyzing = false) else cw
                        })
                    }
                    return@launch
                }
                val analysis = quickAnalyzeWord(word, contextSentence, config.apiKey, config.model, config.baseUrl, config.provider)
                _uiState.update {
                    it.copy(collectedWords = it.collectedWords.map { cw ->
                        if (cw.word == word) cw.copy(analysis = analysis, isAnalyzing = false) else cw
                    })
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(collectedWords = it.collectedWords.map { cw ->
                        if (cw.word == word) cw.copy(isAnalyzing = false) else cw
                    })
                }
            }
        }
    }

    fun removeCollectedWord(word: String) {
        _uiState.update { it.copy(collectedWords = it.collectedWords.filter { cw -> cw.word != word }) }
    }

    fun toggleNotebook() {
        _uiState.update { it.copy(showNotebook = !it.showNotebook) }
    }

    fun dismissNotebook() {
        _uiState.update { it.copy(showNotebook = false) }
    }

    suspend fun getUnitsForDictionary(dictionaryId: Long): List<StudyUnit> {
        return unitRepository.getUnitsByDictionary(dictionaryId)
    }

    fun addToDictionary(word: String, dictionaryId: Long, unitId: Long?) {
        viewModelScope.launch {
            try {
                val cw = _uiState.value.collectedWords.find { it.word.equals(word, ignoreCase = true) } ?: return@launch
                val meanings = if (cw.analysis != null) {
                    val posMeanings = cw.analysis.commonMeanings.ifEmpty {
                        listOf(cw.analysis.contextMeaning)
                    }
                    posMeanings.map { m ->
                        Meaning(pos = cw.analysis.partOfSpeech, definition = m)
                    }
                } else emptyList()

                val details = WordDetails(
                    dictionaryId = dictionaryId,
                    spelling = word,
                    phonetic = cw.analysis?.phonetic ?: "",
                    meanings = meanings
                )
                val wordId = saveWord(details)
                if (unitId != null) {
                    unitRepository.addWordsToUnit(unitId, listOf(wordId))
                }
                backgroundOrganizeManager.enqueue(wordId, dictionaryId, word)
                removeCollectedWord(word)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "添加失败：${e.message}") }
            }
        }
    }

    // ── Practice ──

    fun selectAnswer(itemId: Long, answer: String) {
        if (!QuestionPracticeRules.canSelectAnswer(_uiState.value)) return
        _uiState.update {
            if (!QuestionPracticeRules.canSelectAnswer(it)) {
                it
            } else {
                it.copy(selectedAnswers = it.selectedAnswers + (itemId to answer))
            }
        }
    }

    fun submitAnswers() {
        val state = _uiState.value
        if (state.isSubmitted || state.isSubmitting || state.items.isEmpty()) return

        val validationError = QuestionPracticeRules.submissionValidationError(state)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        _uiState.update {
            if (it.isSubmitted || it.isSubmitting || it.items.isEmpty()) {
                it
            } else {
                it.copy(isSubmitting = true, error = null)
            }
        }

        viewModelScope.launch {
            try {
                val submitState = _uiState.value
                val records = mutableListOf<PracticeRecord>()
                val now = System.currentTimeMillis()

                val isWriting = submitState.group?.questionType == QuestionType.WRITING
                val isTranslation = submitState.group?.questionType == QuestionType.TRANSLATION
                if (isWriting) {
                    val item = submitState.items.firstOrNull()
                    val essayText = item?.let { submitState.selectedAnswers[it.id]?.trim() }.orEmpty()
                    if (item == null || essayText.isBlank()) {
                        _uiState.update { it.copy(isSubmitting = false, error = "请先输入作文内容") }
                        return@launch
                    }
                    val phraseUsage = if (submitState.writingPracticeEnabled) {
                        evaluateWritingPracticeUsage(submitState.writingPracticePhrases, essayText)
                    } else {
                        emptyList()
                    }
                    records.add(
                        PracticeRecord(
                            questionItemId = item.id,
                            userAnswer = essayText,
                            isCorrect = true,
                            practicedAt = now
                        )
                    )
                    repository.insertPracticeRecords(records)
                    if (submitState.writingPracticeEnabled) {
                        wordPhraseRepository.incrementPracticeCounts(submitState.writingPracticePhrases.map { it.phraseId })
                    }
                    _uiState.update {
                        it.copy(
                            isSubmitted = true,
                            isSubmitting = false,
                            isScoringWriting = true,
                            writingPracticeUsage = phraseUsage
                        )
                    }
                    trackQuestionPracticeProgress()
                    scoreWritingAnswers()
                    return@launch
                }
                if (isTranslation) {
                    for (item in submitState.items) {
                        val userAnswer = submitState.selectedAnswers[item.id] ?: continue
                        records.add(
                            PracticeRecord(
                                questionItemId = item.id,
                                userAnswer = userAnswer,
                                isCorrect = true,
                                practicedAt = now
                            )
                        )
                    }
                    if (records.isNotEmpty()) {
                        repository.insertPracticeRecords(records)
                    }
                    _uiState.update {
                        it.copy(
                            isSubmitted = true,
                            isSubmitting = false,
                            isScoringTranslation = true
                        )
                    }
                    trackQuestionPracticeProgress()
                    scoreTranslationAnswers()
                    return@launch
                }

                val results = mutableMapOf<Long, Boolean>()

                for (item in submitState.items) {
                    val userAnswer = submitState.selectedAnswers[item.id]
                    if (userAnswer == null) continue
                    // Skip scoring when no correct answer is available
                    if (item.correctAnswer == null) {
                        records.add(
                            PracticeRecord(
                                questionItemId = item.id,
                                userAnswer = userAnswer,
                                isCorrect = false,
                                practicedAt = now
                            )
                        )
                        continue
                    }
                    val correct = userAnswer.equals(item.correctAnswer, ignoreCase = true)
                    results[item.id] = correct
                    records.add(
                        PracticeRecord(
                            questionItemId = item.id,
                            userAnswer = userAnswer,
                            isCorrect = correct,
                            practicedAt = now
                        )
                    )
                    if (!correct) {
                        repository.incrementWrongCount(item.id)
                    }
                }

                if (records.isNotEmpty()) {
                    repository.insertPracticeRecords(records)
                }

                // Refresh wrong IDs and items
                val wrongIds = repository.getWrongItemIds(groupId).toSet()
                val refreshedItems = repository.getItemsByGroup(groupId)

                _uiState.update {
                    it.copy(
                        isSubmitted = true,
                        isSubmitting = false,
                        practiceResults = results,
                        wrongItemIds = wrongIds,
                        items = refreshedItems
                    )
                }
                trackQuestionPracticeProgress()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isSubmitting = false, error = "提交失败：${e.message}") }
            }
        }
    }

    private fun trackQuestionPracticeProgress() {
        viewModelScope.launch {
            runCatching {
                planAutoProgressTracker.onQuestionSubmitted(groupId)
            }
        }
    }

    private fun scoreTranslationAnswers() {
        viewModelScope.launch {
            try {
                val config = settingsDataStore.getFastAiConfig()
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isScoringTranslation = false, error = "请先配置 AI 模型") }
                    return@launch
                }
                val state = _uiState.value
                val inputs = state.items.mapNotNull { item ->
                    val userAnswer = state.selectedAnswers[item.id] ?: return@mapNotNull null
                    val reference = item.correctAnswer ?: return@mapNotNull null
                    TranslationScoreInput(
                        questionNumber = item.questionNumber,
                        originalText = item.questionText,
                        referenceTranslation = reference,
                        userTranslation = userAnswer
                    )
                }
                if (inputs.isEmpty()) {
                    _uiState.update { it.copy(isScoringTranslation = false) }
                    return@launch
                }
                val scores = aiRepository.scoreTranslations(
                    inputs, config.apiKey, config.model, config.baseUrl, config.provider
                )
                val scoreMap = mutableMapOf<Long, TranslationScore>()
                for (score in scores) {
                    val item = state.items.find { it.questionNumber == score.questionNumber }
                    if (item != null) scoreMap[item.id] = score
                }
                val missing = inputs.size - scoreMap.size
                val errorMsg = if (missing > 0) "AI 评分不完整（缺少 $missing 题），请重试" else null
                _uiState.update {
                    it.copy(
                        translationScores = scoreMap,
                        isScoringTranslation = false,
                        error = errorMsg ?: it.error
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScoringTranslation = false, error = "AI 评分失败：${e.message}") }
            }
        }
    }

    private fun scoreWritingAnswers() {
        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.MAIN)
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isScoringWriting = false, error = "请先配置主模型") }
                    return@launch
                }
                val state = _uiState.value
                val item = state.items.firstOrNull()
                val essayText = item?.let { state.selectedAnswers[it.id] } ?: ""
                if (item == null || essayText.isBlank()) {
                    _uiState.update { it.copy(isScoringWriting = false, error = "请先输入作文内容") }
                    return@launch
                }
                val score = aiRepository.scoreWriting(
                    item.questionText,
                    essayText,
                    config.apiKey,
                    config.model,
                    config.baseUrl,
                    config.provider
                )
                _uiState.update {
                    it.copy(
                        writingScores = mapOf(item.id to score),
                        isScoringWriting = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScoringWriting = false, error = "作文批阅失败：${e.message}") }
            }
        }
    }

    fun showAnswers() {
        _uiState.update {
            if (it.isSubmitted || it.isSubmitting) {
                it
            } else {
                it.copy(showingAnswers = true)
            }
        }
    }

    fun retryPractice() {
        _uiState.update {
            it.copy(
                selectedAnswers = emptyMap(),
                isSubmitted = false,
                isSubmitting = false,
                showingAnswers = false,
                practiceResults = emptyMap(),
                translationScores = emptyMap(),
                isScoringTranslation = false,
                writingScores = emptyMap(),
                isScoringWriting = false,
                writingPracticeUsage = emptyList()
            )
        }
    }

    private fun scanWordLinksAsync(paragraphs: List<ArticleParagraph>) {
        if (paragraphs.isEmpty()) return
        val paraWithContentId = paragraphs.map { it.copy(articleId = contentId) }
        viewModelScope.launch {
            try {
                val links = withContext(Dispatchers.Default) {
                    scanWordLinks(paraWithContentId)
                }
                val linkMap = links.groupBy { it.matchedToken.lowercase() }
                _uiState.update {
                    it.copy(
                        wordLinks = links,
                        wordLinkMap = linkMap
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(error = "词链扫描失败：${e.message}") }
            }
        }
    }

    fun setWritingPracticeEnabled(enabled: Boolean) {
        val state = _uiState.value
        if (state.isSubmitted || state.isSubmitting || state.showingAnswers) return
        if (state.group?.questionType != QuestionType.WRITING) return
        writingPracticeJob?.cancel()
        if (!enabled) {
            writingPracticeJob = null
            _uiState.update {
                it.copy(
                    writingPracticeEnabled = false,
                    isPreparingWritingPractice = false,
                    writingPracticePhrases = emptyList(),
                    writingPracticeUsage = emptyList(),
                    writingPracticeError = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                writingPracticeEnabled = true,
                isPreparingWritingPractice = true,
                writingPracticePhrases = emptyList(),
                writingPracticeUsage = emptyList(),
                writingPracticeError = null
            )
        }
        writingPracticeJob = viewModelScope.launch {
            prepareWritingPracticePhrases()
        }
    }

    fun refreshWritingPracticePhrases() {
        val state = _uiState.value
        if (state.group?.questionType != QuestionType.WRITING || state.isSubmitted || state.isSubmitting) return
        setWritingPracticeEnabled(true)
    }

    private suspend fun prepareWritingPracticePhrases() {
        try {
            val config = settingsDataStore.getAiConfig(AiSettingsScope.WRITING_PRACTICE)
            if (config.apiKey.isBlank()) {
                _uiState.update {
                    it.copy(
                        isPreparingWritingPractice = false,
                        writingPracticeError = "请先配置写作练习模型",
                        error = "请先配置写作练习模型"
                    )
                }
                return
            }

            val state = _uiState.value
            val item = state.items.firstOrNull()
            if (item == null) {
                _uiState.update {
                    it.copy(
                        isPreparingWritingPractice = false,
                        writingPracticeError = "作文题不存在"
                    )
                }
                return
            }

            val selected = mutableListOf<WritingPracticePhraseRequirement>()
            val selectedIds = mutableSetOf<Long>()
            var offset = 0
            val pageSize = WRITING_PRACTICE_CANDIDATE_PAGE_SIZE
            val targetCount = WRITING_PRACTICE_TARGET_PHRASE_COUNT
            val backgroundText = buildWritingBackgroundText(state)

            while (selected.size < targetCount) {
                val page = wordPhraseRepository.getWritingPracticeCandidates(pageSize, offset)
                if (page.isEmpty()) break
                val remaining = targetCount - selected.size
                val choices = aiRepository.selectWritingPracticePhrases(
                    questionText = item.questionText,
                    backgroundText = backgroundText,
                    candidates = page.filter { it.phraseId !in selectedIds },
                    maxCount = remaining,
                    apiKey = config.apiKey,
                    model = config.model,
                    baseUrl = config.baseUrl,
                    provider = config.provider
                )
                val candidateById = page.associateBy { it.phraseId }
                choices.forEach { choice ->
                    val candidate = candidateById[choice.phraseId] ?: return@forEach
                    if (selectedIds.add(candidate.phraseId)) {
                        selected += candidate.toRequirement(choice.reason)
                    }
                }
                offset += pageSize
            }

            val error = when {
                selected.isEmpty() -> "未找到可用于本题的写作练习短语"
                selected.size < targetCount -> "当前仅选出 ${selected.size} 个写作练习短语"
                else -> null
            }
            _uiState.update {
                it.copy(
                    isPreparingWritingPractice = false,
                    writingPracticePhrases = selected,
                    writingPracticeError = error,
                    error = error ?: it.error
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isPreparingWritingPractice = false,
                    writingPracticeError = "写作练习短语准备失败：${e.message}",
                    error = "写作练习短语准备失败：${e.message}"
                )
            }
        }
    }

    private fun buildWritingBackgroundText(state: ReaderUiState): String {
        val parts = buildList {
            val passage = state.group?.passageText.orEmpty()
            if (passage.isNotBlank()) add(passage)
            state.paragraphs.mapNotNullTo(this) { it.text.takeIf(String::isNotBlank) }
        }
        return parts.joinToString("\n\n").take(2400)
    }

    private fun WritingPracticePhraseCandidate.toRequirement(reason: String): WritingPracticePhraseRequirement =
        WritingPracticePhraseRequirement(
            phraseId = phraseId,
            phrase = phrase,
            reason = reason,
            practiceCount = practiceCount
        )

    private fun evaluateWritingPracticeUsage(
        requirements: List<WritingPracticePhraseRequirement>,
        essayText: String
    ): List<WritingPracticePhraseUsage> {
        val normalizedEssay = normalizeWritingPracticeText(essayText)
        return requirements.map { requirement ->
            val normalizedPhrase = normalizeWritingPracticeText(requirement.phrase)
            val used = normalizedPhrase.isNotBlank() &&
                " $normalizedEssay ".contains(" $normalizedPhrase ")
            WritingPracticePhraseUsage(requirement = requirement, used = used)
        }
    }

    private fun normalizeWritingPracticeText(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    // ── Source Operations ──

    fun editSourceUrl(url: String) {
        viewModelScope.launch {
            try {
                repository.updateSourceUrl(groupId, url)
                val updated = repository.getGroupById(groupId)
                _uiState.update { it.copy(group = updated) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "更新失败：${e.message}") }
            }
        }
    }

    fun verifySource() {
        val group = _uiState.value.group ?: return
        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.SEARCH)
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(error = "请先配置搜索模型") }
                    return@launch
                }
                backgroundTaskManager.enqueueQuestionSourceVerify(
                    groupId = group.id,
                    paperTitle = _uiState.value.paperTitle,
                    sectionLabel = group.sectionLabel.orEmpty(),
                    sourceUrlOverride = group.sourceUrl,
                    force = true
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "来源验证任务创建失败：${e.message}") }
            }
        }
    }

    // ── Scan Answers ──

    fun scanAnswerImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(isScanningAnswers = true, isCompressingAnswers = false) }
        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.OCR)
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isScanningAnswers = false, error = "请先配置 OCR 模型") }
                    return@launch
                }

                val compressionConfig = settingsDataStore.getImageCompressionConfig()
                if (compressionConfig.enabled) {
                    _uiState.update { it.copy(isCompressingAnswers = true) }
                }
                val compressed = try {
                    imageCompressionManager.readAndCompressAll(uris, compressionConfig) { uri ->
                        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                } finally {
                    if (compressionConfig.enabled) {
                        _uiState.update { it.copy(isCompressingAnswers = false) }
                    }
                }

                val numbers = _uiState.value.items.map { it.questionNumber }
                val results = aiRepository.scanAnswers(
                    compressed, numbers,
                    config.apiKey, config.model, config.baseUrl, config.provider
                )

                val items = _uiState.value.items
                for (answer in results) {
                    val item = items.find { it.questionNumber == answer.questionNumber } ?: continue
                    repository.updateAnswer(
                        item.id, answer.answer, "SCANNED",
                        answer.explanation, answer.difficultyLevel, answer.difficultyScore
                    )
                }
                repository.markHasScannedAnswer(groupId)

                // Refresh items
                val refreshedItems = repository.getItemsByGroup(groupId)
                _uiState.update { it.copy(items = refreshedItems, isScanningAnswers = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanningAnswers = false, error = "扫描答案失败：${e.message}") }
            }
        }
    }

    fun searchWritingSample(force: Boolean = true) {
        val group = _uiState.value.group ?: return
        if (group.questionType != QuestionType.WRITING) return
        val snippet = _uiState.value.items.firstOrNull()?.questionText?.take(300).orEmpty()
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(writingSampleError = null) }
                backgroundTaskManager.enqueueQuestionWritingSampleSearch(
                    groupId = group.id,
                    paperTitle = _uiState.value.paperTitle,
                    questionSnippet = snippet,
                    force = force
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "范文检索任务创建失败：${e.message}") }
            }
        }
    }

    fun searchWritingPromptSource() {
        val group = _uiState.value.group ?: return
        if (group.questionType != QuestionType.WRITING) return
        val questionText = _uiState.value.items.firstOrNull()?.questionText.orEmpty()
        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.SEARCH)
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(error = "请先配置搜索模型") }
                    return@launch
                }
                _uiState.update { it.copy(isSearchingWritingSource = true) }
                val result = aiRepository.searchWritingPromptSource(
                    _uiState.value.paperTitle,
                    questionText,
                    config.apiKey,
                    config.model,
                    config.baseUrl,
                    config.provider
                )
                if (!result.matched || result.sourceUrl.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            isSearchingWritingSource = false,
                            error = result.errorMessage ?: "未找到题干来源"
                        )
                    }
                    return@launch
                }
                repository.updateSourceMeta(group.id, result.sourceUrl, result.sourceInfo)
                refreshSourceInfo()
                _uiState.update { it.copy(isSearchingWritingSource = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearchingWritingSource = false, error = "检索来源失败：${e.message}") }
            }
        }
    }

    fun scanWritingImages(uris: List<Uri>) {
        val state = _uiState.value
        if (uris.isEmpty() || state.isSubmitted || state.isSubmitting) return
        _uiState.update { it.copy(isOcrWriting = true, isCompressingWriting = false) }
        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.OCR)
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isOcrWriting = false, error = "请先配置 OCR 模型") }
                    return@launch
                }

                val compressionConfig = settingsDataStore.getImageCompressionConfig()
                if (compressionConfig.enabled) {
                    _uiState.update { it.copy(isCompressingWriting = true) }
                }
                val compressed = try {
                    imageCompressionManager.readAndCompressAll(uris, compressionConfig) { uri ->
                        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                } finally {
                    if (compressionConfig.enabled) {
                        _uiState.update { it.copy(isCompressingWriting = false) }
                    }
                }

                val essayText = aiRepository.extractWritingFromImages(
                    compressed, config.apiKey, config.model, config.baseUrl, config.provider
                ).trim()
                if (essayText.isBlank()) {
                    _uiState.update { it.copy(isOcrWriting = false, pendingWritingAutoSubmit = false, error = "未识别到作文内容") }
                    return@launch
                }
                val item = _uiState.value.items.firstOrNull()
                if (item != null) {
                    val currentState = _uiState.value
                    if (currentState.isSubmitted || currentState.isSubmitting) {
                        _uiState.update { it.copy(isOcrWriting = false, pendingWritingAutoSubmit = false) }
                        return@launch
                    }
                    val shouldAutoSubmit = currentState.pendingWritingAutoSubmit
                    _uiState.update {
                        it.copy(
                            selectedAnswers = it.selectedAnswers + (item.id to essayText),
                            isOcrWriting = false,
                            pendingWritingAutoSubmit = false
                        )
                    }
                    if (shouldAutoSubmit) {
                        submitAnswers()
                    }
                } else {
                    _uiState.update { it.copy(isOcrWriting = false, pendingWritingAutoSubmit = false, error = "作文题不存在") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isOcrWriting = false, pendingWritingAutoSubmit = false, error = "OCR 失败：${e.message}") }
            }
        }
    }

    fun prepareWritingOcrSubmit() {
        _uiState.update {
            if (it.isSubmitted || it.isSubmitting) {
                it
            } else {
                it.copy(pendingWritingAutoSubmit = true)
            }
        }
    }

    fun cancelWritingOcrSubmit() {
        _uiState.update { it.copy(pendingWritingAutoSubmit = false) }
    }

    // ── Sentence insertion options ──

    fun openSentenceOptionsEditor() {
        val draft = _uiState.value.sentenceInsertionOptions.joinToString("\n")
        _uiState.update { it.copy(showSentenceOptionsEditor = true, sentenceOptionsDraft = draft) }
    }

    fun dismissSentenceOptionsEditor() {
        _uiState.update { it.copy(showSentenceOptionsEditor = false) }
    }

    fun updateSentenceOptionsDraft(text: String) {
        _uiState.update { it.copy(sentenceOptionsDraft = text) }
    }

    fun saveSentenceOptions() {
        val group = _uiState.value.group ?: return
        val options = parseSentenceOptionsInput(_uiState.value.sentenceOptionsDraft)
        if (options.size != 7) {
            _uiState.update { it.copy(error = "该题型需要 7 个选项") }
            return
        }
        val extraData = buildSentenceInsertionExtraData(options)
        viewModelScope.launch {
            try {
                repository.updateItemsExtraDataByGroup(group.id, extraData)
                _uiState.update {
                    it.copy(
                        sentenceInsertionOptions = options,
                        showSentenceOptionsEditor = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "保存选项失败：${e.message}") }
            }
        }
    }

    // ── Utility ──

    private fun parseSentenceInsertionOptions(items: List<QuestionItem>): List<String> {
        val raw = items.firstOrNull()?.extraData ?: return emptyList()
        return runCatching {
            val obj = org.json.JSONObject(raw)
            val arr = obj.optJSONArray("options") ?: return@runCatching emptyList()
            val result = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val value = arr.optString(i)
                if (!value.isNullOrBlank()) result.add(value)
            }
            result
        }.getOrDefault(emptyList())
    }

    private fun parseSentenceOptionsInput(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()
        val inlineMatches = parseInlineSentenceOptions(trimmed)
        if (inlineMatches.size >= 2) return inlineMatches
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val labeledLines = lines.filter { it.matches(Regex("^[A-G][\\.、\\)]\\s*.+")) }
        if (labeledLines.isNotEmpty()) return labeledLines
        return lines.mapIndexed { index, line ->
            "${('A'.code + index).toChar()}. $line"
        }
    }

    private fun parseInlineSentenceOptions(input: String): List<String> {
        val pattern = Regex("([A-G])[\\.、\\)]\\s*(.+?)(?=\\s*[A-G][\\.、\\)]\\s*|$)", RegexOption.DOT_MATCHES_ALL)
        val matches = pattern.findAll(input).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.map { match ->
            val label = match.groupValues[1]
            val content = match.groupValues[2].trim()
            "$label. $content"
        }
    }

    private fun buildSentenceInsertionExtraData(options: List<String>): String {
        val arr = org.json.JSONArray()
        options.filter { it.isNotBlank() }.forEach { arr.put(it) }
        val obj = org.json.JSONObject()
        obj.put("options", arr)
        return obj.toString()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        translationJob?.cancel()
        writingPracticeJob?.cancel()
    }

    private companion object {
        const val WRITING_PRACTICE_TARGET_PHRASE_COUNT = 10
        const val WRITING_PRACTICE_CANDIDATE_PAGE_SIZE = 40
    }
}
