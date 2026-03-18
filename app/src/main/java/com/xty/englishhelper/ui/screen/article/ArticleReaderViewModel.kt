package com.xty.englishhelper.ui.screen.article

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.article.SentenceSplitter
import com.xty.englishhelper.domain.article.SmartParagraphSplitter
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleStatistics
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.CollectedWord
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.QuickWordAnalysis
import com.xty.englishhelper.domain.model.SentenceAnalysisResult
import com.xty.englishhelper.domain.model.TtsState
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.QuestionGeneratePayload
import com.xty.englishhelper.domain.organize.BackgroundOrganizeManager
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.usecase.article.AnalyzeParagraphUseCase
import com.xty.englishhelper.domain.usecase.article.AnalyzeSentenceUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleDetailUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleStatisticsUseCase
import com.xty.englishhelper.domain.usecase.article.ParseArticleUseCase
import com.xty.englishhelper.domain.usecase.article.QuickAnalyzeWordUseCase
import com.xty.englishhelper.domain.usecase.article.ScanWordLinksUseCase
import com.xty.englishhelper.domain.usecase.article.TranslateParagraphUseCase
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ArticleReaderUiState(
    val article: Article? = null,
    val paragraphs: List<ArticleParagraph>? = null,
    val sentencesByParagraph: Map<Long, List<ArticleSentence>> = emptyMap(),
    val wordLinks: List<ArticleWordLink> = emptyList(),
    val wordLinkMap: Map<String, List<ArticleWordLink>> = emptyMap(),
    val paragraphAnalysis: Map<Long, ParagraphAnalysisResult> = emptyMap(),
    val analyzingParagraphId: Long = 0L,
    val statistics: ArticleStatistics? = null,
    val analyzeError: String? = null,
    val isSavingToLocal: Boolean = false,
    val ttsState: TtsState = TtsState(),
    // Translation
    val translationEnabled: Boolean = false,
    val paragraphTranslations: Map<Long, String> = emptyMap(),
    val translatingParagraphIds: Set<Long> = emptySet(),
    val translationFailedParagraphIds: Set<Long> = emptySet(),
    val expandedParagraphIds: Set<Long> = emptySet(),
    // Collection notebook
    val collectedWords: List<CollectedWord> = emptyList(),
    val showNotebook: Boolean = false,
    val dictionaries: List<Dictionary> = emptyList(),
    // Legacy sentence-level analysis (kept for compatibility)
    val sentences: List<ArticleSentence>? = null,
    val sentenceAnalysis: Map<Long, SentenceAnalysisResult> = emptyMap(),
    val isAnalyzing: Long = 0L,
    val isGeneratingQuestions: Boolean = false,
    val generateError: String? = null
)

@HiltViewModel
class ArticleReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArticleDetail: GetArticleDetailUseCase,
    private val getStatistics: GetArticleStatisticsUseCase,
    private val analyzeSentence: AnalyzeSentenceUseCase,
    private val analyzeParagraph: AnalyzeParagraphUseCase,
    private val scanWordLinks: ScanWordLinksUseCase,
    private val translateParagraph: TranslateParagraphUseCase,
    private val quickAnalyzeWord: QuickAnalyzeWordUseCase,
    private val saveWord: SaveWordUseCase,
    private val parseArticle: ParseArticleUseCase,
    private val dictionaryRepository: DictionaryRepository,
    private val unitRepository: UnitRepository,
    private val backgroundOrganizeManager: BackgroundOrganizeManager,
    private val settingsDataStore: SettingsDataStore,
    private val repository: ArticleRepository,
    private val backgroundTaskRepository: BackgroundTaskRepository,
    private val ttsManager: TtsManager,
    private val backgroundTaskManager: BackgroundTaskManager
) : ViewModel() {

    private val articleId: Long = savedStateHandle["articleId"] ?: 0L
    val scrollToSentenceId: Long = savedStateHandle["scrollToSentenceId"] ?: 0L

    private val _uiState = MutableStateFlow(ArticleReaderUiState())
    val uiState: StateFlow<ArticleReaderUiState> = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(replay = 0)
    val navigateBack: Flow<Unit> = _navigateBack

    private val _generateMessage = MutableSharedFlow<String>(replay = 0)
    val generateMessage: Flow<String> = _generateMessage

    private var pollStarted = false
    private var translationJob: Job? = null
    // Track content fingerprint to avoid redundant reloads
    private var lastLoadedParseStatus: ArticleParseStatus? = null
    private var lastLoadedContentHash: String? = null
    private var dataLoaded = false
    // Cache for online article word link scanning
    private var cachedOnlineWordLinks: List<ArticleWordLink>? = null
    private var cachedFallbackWordLinks: List<ArticleWordLink>? = null
    private var parseRecoveryTriggered = false
    private val staleParseTimeoutMs = java.util.concurrent.TimeUnit.MINUTES.toMillis(3)
    private val taskObserveStartAt = System.currentTimeMillis()
    private val notifiedTaskIds = mutableSetOf<Long>()

    init {
        observeTtsState()
        subscribeToArticleUpdates()
        loadDictionaries()
        observeQuestionGenerationTasks()
        if (articleId != 0L) {
            startParseStatusPolling()
        }
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            ttsManager.state.collect { tts ->
                _uiState.update { it.copy(ttsState = tts) }
            }
        }
    }

    private fun subscribeToArticleUpdates() {
        viewModelScope.launch {
            getArticleDetail(articleId).collect { article ->
                _uiState.update { it.copy(article = article) }

                if (article == null) {
                    _navigateBack.emit(Unit)
                    return@collect
                }

                // Only reload data when content actually changes
                val contentFingerprint = "${article.content.length}|${article.wordCount}"
                val needsReload = !dataLoaded
                    || article.parseStatus != lastLoadedParseStatus
                    || contentFingerprint != lastLoadedContentHash

                if (needsReload) {
                    try {
                        loadArticleData()
                        lastLoadedParseStatus = article.parseStatus
                        lastLoadedContentHash = contentFingerprint
                        dataLoaded = true
                    } catch (e: Exception) {
                        Log.w("ArticleReaderVM", "Data loading failed for articleId=$articleId", e)
                    }
                }
            }
        }
    }

    private suspend fun loadArticleData() {
        // Load paragraphs
        var paragraphs = repository.getParagraphs(articleId)
        var sentences = repository.getSentences(articleId)
        val article = _uiState.value.article

        if (article?.isSaved == true && article.content.isNotBlank()) {
            val paragraphTextLen = paragraphs.sumOf { it.text.length }
            val contentLen = article.content.length
            val needsRepair = paragraphs.isEmpty() || paragraphTextLen < contentLen * 0.6f
            if (needsRepair) {
                val rebuilt = SmartParagraphSplitter.split(article.content).mapIndexed { index, text ->
                    ArticleParagraph(
                        articleId = articleId,
                        paragraphIndex = index,
                        text = text
                    )
                }
                repository.deleteParagraphsByArticle(articleId)
                repository.insertParagraphs(rebuilt)
                paragraphs = rebuilt
                sentences = emptyList()
                launchParseArticle(articleId, "Re-parse failed for articleId=$articleId")
            }
        }

        val wordLinks = if (article?.isSaved == true) {
            // Local/saved article: read persisted word links from DB
            val persisted = repository.getWordLinks(articleId)
            if (persisted.isNotEmpty()) {
                cachedFallbackWordLinks = null
                persisted
            } else if (article.parseStatus != ArticleParseStatus.DONE) {
                cachedFallbackWordLinks ?: scanWordLinks(paragraphs).also {
                    cachedFallbackWordLinks = it
                }
            } else {
                persisted
            }
        } else {
            // Online article: use cached scan result or scan fresh
            cachedOnlineWordLinks ?: scanWordLinks(paragraphs).also {
                cachedOnlineWordLinks = it
            }
        }

        // Group sentences by paragraph
        val sentencesByParagraph = sentences.groupBy { it.paragraphId }

        val wordLinkMap = wordLinks.groupBy { it.matchedToken.lowercase() }

        maybeRecoverStaleParsing(article, sentences, wordLinks)

        _uiState.update {
            it.copy(
                paragraphs = paragraphs,
                sentences = sentences,
                sentencesByParagraph = sentencesByParagraph,
                wordLinks = wordLinks,
                wordLinkMap = wordLinkMap
            )
        }

        // Prewarm full-article TTS so each paragraph can start quickly
        if (article != null && paragraphs.isNotEmpty()) {
            val texts = buildTtsParagraphs(article.title, paragraphs)
            if (texts.isNotEmpty()) {
                ttsManager.prewarmArticle(article.id, texts)
            }
        }

        // For unsaved articles, compute statistics in-memory from paragraph text
        // For saved articles, load from DB
        if (article?.isSaved == false && paragraphs.isNotEmpty()) {
            computeInMemoryStatistics(paragraphs, article)
        } else if (article?.isSaved == true) {
            loadStatistics()
        }
    }

    private fun observeQuestionGenerationTasks() {
        viewModelScope.launch {
            backgroundTaskRepository.observeTasksByTypes(listOf(BackgroundTaskType.QUESTION_GENERATE))
                .collect { tasks ->
                    tasks.asSequence()
                        .filter { it.createdAt >= taskObserveStartAt }
                        .filter { it.id !in notifiedTaskIds }
                        .forEach { task ->
                            val payload = task.payload as? QuestionGeneratePayload ?: return@forEach
                            if (payload.articleId != articleId) return@forEach
                            when (task.status) {
                                BackgroundTaskStatus.SUCCESS -> {
                                    notifiedTaskIds.add(task.id)
                                    _generateMessage.emit("出题完成，可在题库中查看")
                                }
                                BackgroundTaskStatus.FAILED -> {
                                    notifiedTaskIds.add(task.id)
                                    val reason = task.errorMessage?.takeIf { it.isNotBlank() } ?: "未知错误"
                                    _generateMessage.emit("出题失败：$reason")
                                }
                                else -> Unit
                            }
                        }
                }
        }
    }

    private fun maybeRecoverStaleParsing(
        article: Article?,
        sentences: List<ArticleSentence>,
        wordLinks: List<ArticleWordLink>
    ) {
        if (article == null || !article.isSaved) return
        if (article.parseStatus != ArticleParseStatus.PROCESSING) return
        if (parseRecoveryTriggered) return
        if (sentences.isNotEmpty() || wordLinks.isNotEmpty()) return

        val age = System.currentTimeMillis() - article.updatedAt
        if (age < staleParseTimeoutMs) return

        parseRecoveryTriggered = true
        launchParseArticle(articleId, "Parse recovery failed for articleId=$articleId")
    }

    private fun computeInMemoryStatistics(paragraphs: List<ArticleParagraph>, article: Article) {
        val allText = paragraphs.joinToString(" ") { it.text }
        val wordCount = allText.split(Regex("\\s+")).count { it.isNotBlank() }
        val sentenceCount = paragraphs.sumOf { p ->
            if (p.text.isBlank()) 0 else SentenceSplitter.split(p.text).size.toLong()
        }.toInt()
        val charCount = allText.length

        _uiState.update {
            it.copy(
                statistics = ArticleStatistics(
                    wordCount = wordCount,
                    sentenceCount = sentenceCount,
                    charCount = charCount
                )
            )
        }

        // Also update the in-DB wordCount so it shows in the meta row
        if (article.wordCount == 0 && wordCount > 0) {
            viewModelScope.launch {
                try {
                    repository.updateWordCount(articleId, wordCount)
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun loadArticleOnce() {
        try {
            val article = repository.getArticleByIdOnce(articleId)
            if (article != null) {
                _uiState.update { it.copy(article = article) }
                loadArticleData()
            }
        } catch (e: Exception) {
            Log.w("ArticleReaderVM", "Loading failure for articleId=$articleId", e)
        }
    }

    private fun launchParseArticle(targetId: Long, logMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                parseArticle(targetId)
            } catch (e: Exception) {
                Log.w("ArticleReaderVM", logMessage, e)
            }
        }
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val stats = getStatistics(articleId)
                _uiState.update { it.copy(statistics = stats) }
            } catch (e: Exception) {
                Log.w("ArticleReaderVM", "Stats loading failed for articleId=$articleId", e)
            }
        }
    }

    private fun loadDictionaries() {
        viewModelScope.launch {
            dictionaryRepository.getAllDictionaries().collect { dicts ->
                _uiState.update { it.copy(dictionaries = dicts) }
            }
        }
    }

    private fun startParseStatusPolling() {
        if (pollStarted) return
        pollStarted = true

        viewModelScope.launch {
            while (true) {
                val article = _uiState.value.article
                if (article == null ||
                    article.parseStatus == ArticleParseStatus.DONE ||
                    article.parseStatus == ArticleParseStatus.FAILED) {
                    break
                }
                delay(2000)
                loadArticleOnce()
            }
        }
    }

    // 鈹€鈹€ Translation 鈹€鈹€

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
        val paragraphs = _uiState.value.paragraphs ?: return
        val textParagraphs = paragraphs.filter { it.text.isNotBlank() }
        val alreadyTranslated = _uiState.value.paragraphTranslations.keys

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val config = settingsDataStore.getFastAiConfig()
            if (config.apiKey.isBlank()) {
                _uiState.update { it.copy(analyzeError = "璇峰厛鍦ㄨ缃腑閰嶇疆 API Key") }
                return@launch
            }
            val isSaved = _uiState.value.article?.isSaved ?: true

            for (paragraph in textParagraphs) {
                ensureActive()
                if (paragraph.id in alreadyTranslated) continue
                _uiState.update { it.copy(translatingParagraphIds = it.translatingParagraphIds + paragraph.id) }
                try {
                    val translation = translateParagraph(
                        articleId, paragraph.id, paragraph.text,
                        config.apiKey, config.model, config.baseUrl, config.provider, isSaved
                    )
                    _uiState.update {
                        it.copy(
                            paragraphTranslations = it.paragraphTranslations + (paragraph.id to translation),
                            translatingParagraphIds = it.translatingParagraphIds - paragraph.id,
                            translationFailedParagraphIds = it.translationFailedParagraphIds - paragraph.id
                        )
                    }
                } catch (e: Exception) {
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
                _uiState.update { it.copy(analyzeError = "璇峰厛鍦ㄨ缃腑閰嶇疆 API Key") }
                return@launch
            }
            val isSaved = _uiState.value.article?.isSaved ?: true
            _uiState.update { it.copy(translatingParagraphIds = it.translatingParagraphIds + paragraphId) }
            try {
                val translation = translateParagraph(
                    articleId, paragraphId, paragraphText,
                    config.apiKey, config.model, config.baseUrl, config.provider, isSaved
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

    // 鈹€鈹€ Collection notebook 鈹€鈹€

    fun collectWord(word: String, contextSentence: String) {
        if (_uiState.value.collectedWords.any { it.word.equals(word, ignoreCase = true) }) return

        val entry = CollectedWord(word = word, contextSentence = contextSentence, isAnalyzing = true)
        _uiState.update { it.copy(collectedWords = it.collectedWords + entry) }

        viewModelScope.launch {
            try {
                val config = settingsDataStore.getFastAiConfig()
                if (config.apiKey.isBlank()) {
                    _uiState.update { state ->
                        state.copy(collectedWords = state.collectedWords.map {
                            if (it.word.equals(word, ignoreCase = true)) it.copy(isAnalyzing = false) else it
                        })
                    }
                    return@launch
                }
                val analysis = quickAnalyzeWord(
                    word, contextSentence,
                    config.apiKey, config.model, config.baseUrl, config.provider
                )
                _uiState.update { state ->
                    state.copy(collectedWords = state.collectedWords.map {
                        if (it.word.equals(word, ignoreCase = true)) it.copy(analysis = analysis, isAnalyzing = false) else it
                    })
                }
            } catch (_: Exception) {
                _uiState.update { state ->
                    state.copy(collectedWords = state.collectedWords.map {
                        if (it.word.equals(word, ignoreCase = true)) it.copy(isAnalyzing = false) else it
                    })
                }
            }
        }
    }

    fun removeCollectedWord(word: String) {
        _uiState.update { state ->
            state.copy(collectedWords = state.collectedWords.filter { !it.word.equals(word, ignoreCase = true) })
        }
    }

    fun toggleNotebook() {
        _uiState.update { it.copy(showNotebook = !it.showNotebook) }
    }

    fun dismissNotebook() {
        _uiState.update { it.copy(showNotebook = false) }
    }

    suspend fun getUnitsForDictionary(dictionaryId: Long) = unitRepository.getUnitsByDictionary(dictionaryId)

    fun addToDictionary(word: String, dictionaryId: Long, unitId: Long?) {
        val collected = _uiState.value.collectedWords.find { it.word.equals(word, ignoreCase = true) }
            ?: return

        viewModelScope.launch {
            try {
                val meanings = if (collected.analysis != null) {
                    val posMeanings = collected.analysis.commonMeanings.ifEmpty {
                        listOf(collected.analysis.contextMeaning)
                    }
                    posMeanings.map { m ->
                        Meaning(pos = collected.analysis.partOfSpeech, definition = m)
                    }
                } else emptyList()

                val wordDetails = WordDetails(
                    dictionaryId = dictionaryId,
                    spelling = word,
                    phonetic = collected.analysis?.phonetic ?: "",
                    meanings = meanings
                )
                val wordId = saveWord(wordDetails)

                if (unitId != null) {
                    unitRepository.addWordsToUnit(unitId, listOf(wordId))
                }

                // Background AI organize to enrich word details
                backgroundOrganizeManager.enqueue(wordId, dictionaryId, word)

                // Remove from notebook
                removeCollectedWord(word)
            } catch (e: Exception) {
                _uiState.update { it.copy(analyzeError = "鍔犲叆璇嶅吀澶辫触锛?{e.message}") }
            }
        }
    }

    // 鈹€鈹€ Paragraph analysis 鈹€鈹€

    fun analyzeParagraph(paragraphId: Long, paragraphText: String) {
        _uiState.update { it.copy(analyzingParagraphId = paragraphId, analyzeError = null) }

        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.ARTICLE)

                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(analyzingParagraphId = 0L, analyzeError = "璇峰厛鍦ㄨ缃腑閰嶇疆 API Key") }
                    return@launch
                }

                val isSaved = _uiState.value.article?.isSaved ?: true
                val result = analyzeParagraph(
                    articleId, paragraphId, paragraphText,
                    config.apiKey, config.model, config.baseUrl, config.provider,
                    isSaved
                )

                _uiState.update {
                    it.copy(
                        paragraphAnalysis = it.paragraphAnalysis + (paragraphId to result),
                        analyzingParagraphId = 0L,
                        expandedParagraphIds = it.expandedParagraphIds + paragraphId
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(analyzingParagraphId = 0L, analyzeError = "鍒嗘瀽澶辫触锛?{e.message}") }
            }
        }
    }

    fun toggleParagraphAnalysisExpanded(paragraphId: Long) {
        _uiState.update {
            val expanded = it.expandedParagraphIds
            if (paragraphId in expanded) {
                it.copy(expandedParagraphIds = expanded - paragraphId)
            } else {
                it.copy(expandedParagraphIds = expanded + paragraphId)
            }
        }
    }

    fun analyzeSentence(sentenceId: Long, sentenceText: String) {
        _uiState.update { it.copy(isAnalyzing = sentenceId, analyzeError = null) }

        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.MAIN)

                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isAnalyzing = 0L, analyzeError = "璇峰厛鍦ㄨ缃腑閰嶇疆 API Key") }
                    return@launch
                }

                val result = analyzeSentence(articleId, sentenceId, sentenceText, config.apiKey, config.model, config.baseUrl, config.provider)
                _uiState.update {
                    it.copy(
                        sentenceAnalysis = it.sentenceAnalysis + (sentenceId to result),
                        isAnalyzing = 0L
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = 0L, analyzeError = "鍒嗘瀽澶辫触锛?{e.message}") }
            }
        }
    }

    fun saveToLocal() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSavingToLocal = true) }
                repository.markArticleSaved(articleId)
                withContext(Dispatchers.IO) {
                    parseArticle(articleId)
                }
                _uiState.update { it.copy(isSavingToLocal = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSavingToLocal = false, analyzeError = "淇濆瓨澶辫触锛?{e.message}")
                }
            }
        }
    }

    fun toggleSpeakArticle() {
        val article = _uiState.value.article ?: return
        val paragraphs = _uiState.value.paragraphs ?: return
        val sessionId = ttsManager.articleSessionId(article.id)
        val isCurrent = _uiState.value.ttsState.sessionId == sessionId

        if (_uiState.value.ttsState.isSpeaking && isCurrent) {
            ttsManager.pause()
            return
        }

        val texts = buildTtsParagraphs(article.title, paragraphs)
        if (texts.isEmpty()) return

        val ttsState = _uiState.value.ttsState
        val atEnd = isCurrent &&
            !ttsState.isSpeaking &&
            texts.isNotEmpty() &&
            ttsState.currentIndex >= texts.lastIndex
        val startIndex = if (!isCurrent) 0 else if (atEnd) 0 else ttsState.currentIndex
        viewModelScope.launch {
            ttsManager.speakArticle(article.id, texts, startIndex)
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun nextParagraph() {
        ttsManager.next()
    }

    fun previousParagraph() {
        ttsManager.previous()
    }

    fun clearTtsError() {
        ttsManager.clearError()
    }

    private fun buildTtsParagraphs(title: String, paragraphs: List<ArticleParagraph>): List<String> {
        val texts = paragraphs
            .filter { it.paragraphType != com.xty.englishhelper.domain.model.ParagraphType.IMAGE }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
        return if (title.isBlank()) texts else listOf(title) + texts
    }

    fun generateQuestions(paperTitle: String, questionType: QuestionType, variant: String?) {
        val state = _uiState.value
        if (state.isGeneratingQuestions) return
        val article = state.article ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingQuestions = true, generateError = null) }
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.MAIN)
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isGeneratingQuestions = false, generateError = "请先在设置中配置主模型") }
                    return@launch
                }

                val articleText = withContext(Dispatchers.Default) {
                    buildArticleText(article, state.paragraphs.orEmpty())
                }
                if (articleText.isBlank()) {
                    _uiState.update { it.copy(isGeneratingQuestions = false, generateError = "文章内容为空，无法出题") }
                    return@launch
                }
                val now = System.currentTimeMillis()
                val finalTitle = paperTitle.ifBlank { buildDefaultPaperTitle(article.title, now) }
                backgroundTaskManager.enqueueQuestionGenerateFromArticle(
                    articleId = article.id,
                    paperTitle = finalTitle,
                    questionType = questionType,
                    variant = variant
                )
                _generateMessage.emit("已加入后台出题任务，可在后台任务管理查看")
                _uiState.update { it.copy(isGeneratingQuestions = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingQuestions = false, generateError = "出题失败：${e.message}") }
            }
        }
    }

    private fun buildArticleText(article: Article, paragraphs: List<ArticleParagraph>): String {
        val text = if (paragraphs.isNotEmpty()) {
            paragraphs
                .filter { it.paragraphType != com.xty.englishhelper.domain.model.ParagraphType.IMAGE }
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        } else {
            article.content
        }
        val normalized = text
            .split(Regex("\\n\\s*\\n"))
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        return if (normalized.length > 6000) normalized.take(6000) else normalized
    }

    private fun buildDefaultPaperTitle(title: String, now: Long): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(now))
        val safeTitle = title.ifBlank { "未命名文章" }
        return "文章出题 - $safeTitle - $date"
    }

    fun clearError() {
        _uiState.update { it.copy(analyzeError = null, generateError = null) }
    }
}

