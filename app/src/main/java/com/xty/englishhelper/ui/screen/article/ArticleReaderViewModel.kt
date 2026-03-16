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
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.organize.BackgroundOrganizeManager
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
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
    private val ttsManager: TtsManager,
    private val questionBankRepository: QuestionBankRepository,
    private val questionBankAiRepository: QuestionBankAiRepository,
    private val backgroundTaskManager: BackgroundTaskManager
) : ViewModel() {

    private val articleId: Long = savedStateHandle["articleId"] ?: 0L
    val scrollToSentenceId: Long = savedStateHandle["scrollToSentenceId"] ?: 0L

    private val _uiState = MutableStateFlow(ArticleReaderUiState())
    val uiState: StateFlow<ArticleReaderUiState> = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(replay = 0)
    val navigateBack: Flow<Unit> = _navigateBack

    private val _generatedGroupId = MutableSharedFlow<Long>(replay = 0)
    val generatedGroupId: Flow<Long> = _generatedGroupId

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

    init {
        observeTtsState()
        subscribeToArticleUpdates()
        loadDictionaries()
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
                viewModelScope.launch {
                    try {
                        parseArticle(articleId)
                    } catch (e: Exception) {
                        Log.w("ArticleReaderVM", "Re-parse failed for articleId=$articleId", e)
                    }
                }
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
        viewModelScope.launch {
            try {
                parseArticle(articleId)
            } catch (e: Exception) {
                Log.w("ArticleReaderVM", "Parse recovery failed for articleId=$articleId", e)
            }
        }
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
                parseArticle(articleId)
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

                val articleText = buildArticleText(article, state.paragraphs.orEmpty())
                if (articleText.isBlank()) {
                    _uiState.update { it.copy(isGeneratingQuestions = false, generateError = "文章内容为空，无法出题") }
                    return@launch
                }
                val scanResult = questionBankAiRepository.generateQuestionsFromArticle(
                    articleTitle = article.title,
                    articleText = articleText,
                    questionType = questionType.name,
                    variant = variant,
                    apiKey = config.apiKey,
                    model = config.model,
                    baseUrl = config.baseUrl,
                    provider = config.provider
                )

                val rawGroup = scanResult.questionGroups.firstOrNull()
                    ?: throw IllegalStateException("出题失败：未返回题组")

                val normalizedGroup = normalizeGeneratedGroup(
                    rawGroup = rawGroup,
                    questionType = questionType,
                    variant = variant,
                    article = article
                )
                validateGeneratedGroup(normalizedGroup, questionType, variant)

                val now = System.currentTimeMillis()
                val finalTitle = paperTitle.ifBlank { buildDefaultPaperTitle(article.title, now) }
                val paper = ExamPaper(
                    uid = UUID.randomUUID().toString(),
                    title = finalTitle,
                    createdAt = now,
                    updatedAt = now
                )

                val sentenceOptionsJson = if (
                    questionType == QuestionType.SENTENCE_INSERTION ||
                    questionType == QuestionType.COMMENT_OPINION_MATCH ||
                    questionType == QuestionType.SUBHEADING_MATCH ||
                    questionType == QuestionType.INFORMATION_MATCH
                ) {
                    buildSentenceInsertionExtraData(normalizedGroup.sentenceOptions)
                } else null

                val passageParagraphs = if (
                    questionType == QuestionType.INFORMATION_MATCH &&
                    normalizedGroup.passageParagraphs.isEmpty() &&
                    normalizedGroup.sentenceOptions.isNotEmpty()
                ) {
                    normalizedGroup.sentenceOptions
                } else {
                    normalizedGroup.passageParagraphs
                }

                val group = QuestionGroup(
                    uid = UUID.randomUUID().toString(),
                    examPaperId = 0,
                    questionType = questionType,
                    sectionLabel = normalizedGroup.sectionLabel?.takeIf { it.isNotBlank() }
                        ?: defaultSectionLabel(questionType, variant),
                    orderInPaper = 0,
                    directions = normalizedGroup.directions,
                    passageText = passageParagraphs.joinToString("\n"),
                    sourceInfo = normalizedGroup.sourceInfo,
                    sourceUrl = normalizedGroup.sourceUrl,
                    wordCount = normalizedGroup.wordCount,
                    difficultyLevel = normalizedGroup.difficultyLevel?.let { level ->
                        com.xty.englishhelper.domain.model.DifficultyLevel.entries.find { it.name == level }
                    },
                    difficultyScore = normalizedGroup.difficultyScore,
                    createdAt = now,
                    updatedAt = now,
                    paragraphs = passageParagraphs.mapIndexed { index, text ->
                        ArticleParagraph(paragraphIndex = index, text = text)
                    },
                    items = normalizedGroup.questions.mapIndexed { index, q ->
                        QuestionItem(
                            questionGroupId = 0,
                            questionNumber = if (q.questionNumber > 0) q.questionNumber else index + 1,
                            questionText = q.questionText,
                            optionA = q.optionA.ifBlank { null },
                            optionB = q.optionB.ifBlank { null },
                            optionC = q.optionC.ifBlank { null },
                            optionD = q.optionD.ifBlank { null },
                            orderInGroup = index,
                            wordCount = q.wordCount,
                            difficultyLevel = q.difficultyLevel?.let { level ->
                                com.xty.englishhelper.domain.model.DifficultyLevel.entries.find { it.name == level }
                            },
                            difficultyScore = q.difficultyScore,
                            extraData = sentenceOptionsJson
                        )
                    }
                )

                val paperId = questionBankRepository.saveScannedPaper(paper, listOf(group))
                val groupList = questionBankRepository.getGroupsByPaper(paperId).first()
                val firstGroup = groupList.firstOrNull()

                if (firstGroup != null) {
                    if (!article.isSaved) {
                        repository.markArticleSaved(article.id)
                        viewModelScope.launch {
                            try {
                                parseArticle(article.id)
                            } catch (e: Exception) {
                                Log.w("ArticleReaderVM", "Parse failed for articleId=${article.id}", e)
                            }
                        }
                    }
                    questionBankRepository.linkSourceArticle(firstGroup.id, article.id)
                    questionBankRepository.updateSourceVerification(firstGroup.id, 1, null)
                    val sourceUrl = article.domain.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    if (!sourceUrl.isNullOrBlank()) {
                        questionBankRepository.updateSourceUrl(firstGroup.id, sourceUrl)
                    }

                    enqueueGeneratedTasks(firstGroup, finalTitle)
                    _generatedGroupId.emit(firstGroup.id)
                }

                _uiState.update { it.copy(isGeneratingQuestions = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingQuestions = false, generateError = "出题失败：${e.message}") }
            }
        }
    }

    private fun enqueueGeneratedTasks(group: QuestionGroup, paperTitle: String) {
        if (group.questionType == QuestionType.WRITING) {
            val snippet = group.items.firstOrNull()?.questionText?.take(300).orEmpty()
            backgroundTaskManager.enqueueQuestionWritingSampleSearch(
                groupId = group.id,
                paperTitle = paperTitle,
                questionSnippet = snippet
            )
        } else {
            backgroundTaskManager.enqueueQuestionAnswerGeneration(
                groupId = group.id,
                paperTitle = paperTitle,
                sectionLabel = group.sectionLabel.orEmpty()
            )
        }
    }

    private fun normalizeGeneratedGroup(
        rawGroup: com.xty.englishhelper.domain.repository.ScannedQuestionGroup,
        questionType: QuestionType,
        variant: String?,
        article: Article
    ): com.xty.englishhelper.domain.repository.ScannedQuestionGroup {
        val sourceUrl = article.domain.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val sourceInfo = article.source.ifBlank { article.title }
        val sectionLabel = rawGroup.sectionLabel?.takeIf { it.isNotBlank() }
            ?: defaultSectionLabel(questionType, variant)
        val directions = rawGroup.directions?.takeIf { it.isNotBlank() } ?: defaultDirections(questionType, variant)
        val questions = rawGroup.questions.mapIndexed { index, q ->
            q.copy(questionNumber = if (q.questionNumber > 0) q.questionNumber else index + 1)
        }
        return rawGroup.copy(
            questionType = questionType.name,
            sectionLabel = sectionLabel,
            directions = directions,
            sourceUrl = sourceUrl,
            sourceInfo = sourceInfo,
            questions = questions
        )
    }

    private fun validateGeneratedGroup(
        group: com.xty.englishhelper.domain.repository.ScannedQuestionGroup,
        questionType: QuestionType,
        variant: String?
    ) {
        val passageText = group.passageParagraphs.joinToString("\n")
        val blankRegex = Regex("__(\\d+)__")
        val blankMatches = blankRegex.findAll(passageText).toList()
        val blankCount = blankMatches.size
        val blankNumbers = blankMatches.mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }.toSet()
        val questionNumbers = group.questions.mapNotNull { q ->
            q.questionNumber.takeIf { it > 0 }
        }
        val translationMarkerRegex = Regex("\\(\\((\\d+)\\)\\)")

        when (questionType.name) {
            "READING_COMPREHENSION" -> {
                if (group.passageParagraphs.isEmpty()) throw IllegalStateException("阅读理解未生成文章段落")
                if (group.questions.size != 5) throw IllegalStateException("阅读理解题数必须为 5")
            }
            "CLOZE" -> {
                if (group.passageParagraphs.isEmpty()) throw IllegalStateException("完形填空未生成文章")
                if (group.questions.size != 20) throw IllegalStateException("完形填空题数必须为 20")
                if (blankCount < group.questions.size) throw IllegalStateException("完形填空未标注足够的空位")
                if (questionNumbers.any { it !in blankNumbers }) {
                    throw IllegalStateException("完形填空空位编号与题号不一致")
                }
            }
            "TRANSLATION" -> {
                if (group.passageParagraphs.isEmpty()) throw IllegalStateException("翻译未生成文章")
                if (group.questions.isEmpty()) throw IllegalStateException("翻译题未生成题目")
                if (variant == "ENG1" && group.questions.size != 5) {
                    throw IllegalStateException("翻译（英语一）题数必须为 5")
                }
                if (variant == "ENG2" && group.questions.size != 1) {
                    throw IllegalStateException("翻译（英语二）题数必须为 1")
                }
                if (variant == "ENG1") {
                    val markerCount = translationMarkerRegex.findAll(passageText).count()
                    if (markerCount < 5) throw IllegalStateException("翻译（英语一）未标注划线句段")
                }
            }
            "WRITING" -> {
                if (group.questions.isEmpty() || group.questions.first().questionText.isBlank()) {
                    throw IllegalStateException("写作题干缺失")
                }
            }
            "PARAGRAPH_ORDER" -> {
                if (group.passageParagraphs.size < 8) throw IllegalStateException("段落排序需至少 8 段")
                if (group.questions.size != 5) throw IllegalStateException("段落排序题数必须为 5")
            }
            "SENTENCE_INSERTION" -> {
                if (group.sentenceOptions.size < 7) throw IllegalStateException("句子插入需 7 个候选句")
                if (group.questions.size != 5) throw IllegalStateException("句子插入题数必须为 5")
                if (blankCount < group.questions.size) throw IllegalStateException("句子插入未标注足够的空位")
                if (questionNumbers.any { it !in blankNumbers }) {
                    throw IllegalStateException("句子插入空位编号与题号不一致")
                }
            }
            "COMMENT_OPINION_MATCH", "SUBHEADING_MATCH" -> {
                if (group.sentenceOptions.size < 7) throw IllegalStateException("匹配题需 7 个选项")
                if (group.questions.size != 5) throw IllegalStateException("匹配题题数必须为 5")
            }
            "INFORMATION_MATCH" -> {
                if (group.passageParagraphs.size < 7 && group.sentenceOptions.size < 7) {
                    throw IllegalStateException("信息匹配需 7 个选项")
                }
                if (group.questions.size != 5) throw IllegalStateException("信息匹配题数必须为 5")
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

    private fun defaultSectionLabel(questionType: QuestionType, variant: String?): String {
        return when (questionType) {
            QuestionType.TRANSLATION -> if (variant == "ENG1") "翻译（英语一）" else "翻译（英语二）"
            QuestionType.WRITING -> if (variant == "SMALL") "写作（小作文）" else "写作（大作文）"
            QuestionType.READING_COMPREHENSION -> "阅读理解"
            QuestionType.CLOZE -> "完形填空"
            QuestionType.PARAGRAPH_ORDER -> "段落排序"
            QuestionType.SENTENCE_INSERTION -> "句子插入"
            QuestionType.COMMENT_OPINION_MATCH -> "评论观点匹配"
            QuestionType.SUBHEADING_MATCH -> "小标题匹配"
            QuestionType.INFORMATION_MATCH -> "信息匹配"
            else -> questionType.displayName
        }
    }

    private fun defaultDirections(questionType: QuestionType, variant: String?): String {
        return when (questionType) {
            QuestionType.READING_COMPREHENSION -> "Read the passage and answer the questions."
            QuestionType.CLOZE -> "Read the passage and choose the best word for each blank."
            QuestionType.TRANSLATION -> if (variant == "ENG1") "Translate the underlined sentences into Chinese." else "Translate the following passage into Chinese."
            QuestionType.WRITING -> if (variant == "SMALL") "Write an application letter of about 100 words." else "Write an essay of 160-200 words."
            QuestionType.PARAGRAPH_ORDER -> "Reorder the paragraphs to form a coherent passage. Fill in the blanks."
            QuestionType.SENTENCE_INSERTION -> "Choose the best sentence for each blank."
            QuestionType.COMMENT_OPINION_MATCH -> "Match each comment with the best summary opinion."
            QuestionType.SUBHEADING_MATCH -> "Match each paragraph with the most suitable heading."
            QuestionType.INFORMATION_MATCH -> "Match each statement with the correct information."
            else -> ""
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
        _uiState.update { it.copy(analyzeError = null, generateError = null) }
    }
}

