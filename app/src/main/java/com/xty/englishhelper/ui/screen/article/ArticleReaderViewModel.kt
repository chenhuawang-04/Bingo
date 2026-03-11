package com.xty.englishhelper.ui.screen.article

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.article.SentenceSplitter
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleStatistics
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.QuickWordAnalysis
import com.xty.englishhelper.domain.model.SentenceAnalysisResult
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.organize.BackgroundOrganizeManager
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.GuardianRepository
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.usecase.article.AnalyzeParagraphUseCase
import com.xty.englishhelper.domain.usecase.article.AnalyzeSentenceUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleDetailUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleStatisticsUseCase
import com.xty.englishhelper.domain.usecase.article.QuickAnalyzeWordUseCase
import com.xty.englishhelper.domain.usecase.article.ScanWordLinksUseCase
import com.xty.englishhelper.domain.usecase.article.TranslateParagraphUseCase
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase
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
import javax.inject.Inject

data class CollectedWord(
    val word: String,
    val contextSentence: String,
    val analysis: QuickWordAnalysis? = null,
    val isAnalyzing: Boolean = false
)

data class ArticleReaderUiState(
    val article: Article? = null,
    val paragraphs: List<ArticleParagraph>? = null,
    val sentencesByParagraph: Map<Long, List<ArticleSentence>> = emptyMap(),
    val wordLinks: List<ArticleWordLink> = emptyList(),
    val paragraphAnalysis: Map<Long, ParagraphAnalysisResult> = emptyMap(),
    val analyzingParagraphId: Long = 0L,
    val statistics: ArticleStatistics? = null,
    val analyzeError: String? = null,
    val isSavingToLocal: Boolean = false,
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
    val isAnalyzing: Long = 0L
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
    private val guardianRepository: GuardianRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val unitRepository: UnitRepository,
    private val backgroundOrganizeManager: BackgroundOrganizeManager,
    private val settingsDataStore: SettingsDataStore,
    private val repository: ArticleRepository
) : ViewModel() {

    private val articleId: Long = savedStateHandle["articleId"] ?: 0L
    val scrollToSentenceId: Long = savedStateHandle["scrollToSentenceId"] ?: 0L

    private val _uiState = MutableStateFlow(ArticleReaderUiState())
    val uiState: StateFlow<ArticleReaderUiState> = _uiState.asStateFlow()

    private val _navigateBack = MutableSharedFlow<Unit>(replay = 0)
    val navigateBack: Flow<Unit> = _navigateBack

    private var pollStarted = false
    private var translationJob: Job? = null

    init {
        subscribeToArticleUpdates()
        loadDictionaries()
        if (articleId != 0L) {
            startParseStatusPolling()
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

                try {
                    loadArticleData()
                } catch (e: Exception) {
                    Log.w("ArticleReaderVM", "Data loading failed for articleId=$articleId", e)
                }
            }
        }
    }

    private suspend fun loadArticleData() {
        // Load paragraphs
        val paragraphs = repository.getParagraphs(articleId)
        val sentences = repository.getSentences(articleId)
        val article = _uiState.value.article

        val wordLinks = if (article?.isSaved == true) {
            // Local/saved article: read persisted word links from DB
            repository.getWordLinks(articleId)
        } else {
            // Online article: in-memory word scanning (no DB writes)
            scanWordLinks(paragraphs)
        }

        // Group sentences by paragraph
        val sentencesByParagraph = sentences.groupBy { it.paragraphId }

        _uiState.update {
            it.copy(
                paragraphs = paragraphs,
                sentences = sentences,
                sentencesByParagraph = sentencesByParagraph,
                wordLinks = wordLinks
            )
        }

        // For unsaved articles, compute statistics in-memory from paragraph text
        // For saved articles, load from DB
        if (article?.isSaved == false && paragraphs.isNotEmpty()) {
            computeInMemoryStatistics(paragraphs, article)
        } else if (article?.isSaved == true) {
            loadStatistics()
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
        val paragraphs = _uiState.value.paragraphs ?: return
        val textParagraphs = paragraphs.filter { it.text.isNotBlank() }
        val alreadyTranslated = _uiState.value.paragraphTranslations.keys

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            val config = settingsDataStore.getFastAiConfig()
            if (config.apiKey.isBlank()) {
                _uiState.update { it.copy(analyzeError = "请先在设置中配置 API Key") }
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
                _uiState.update { it.copy(analyzeError = "请先在设置中配置 API Key") }
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

    // ── Collection notebook ──

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
                _uiState.update { it.copy(analyzeError = "加入词典失败：${e.message}") }
            }
        }
    }

    // ── Paragraph analysis ──

    fun analyzeParagraph(paragraphId: Long, paragraphText: String) {
        _uiState.update { it.copy(analyzingParagraphId = paragraphId, analyzeError = null) }

        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.ARTICLE)

                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(analyzingParagraphId = 0L, analyzeError = "请先在设置中配置 API Key") }
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
                _uiState.update { it.copy(analyzingParagraphId = 0L, analyzeError = "分析失败：${e.message}") }
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
                val apiKey = settingsDataStore.apiKey.first()
                val model = settingsDataStore.model.first()
                val baseUrl = settingsDataStore.baseUrl.first()
                val provider = settingsDataStore.provider.first()

                if (apiKey.isBlank()) {
                    _uiState.update { it.copy(isAnalyzing = 0L, analyzeError = "请先在设置中配置 API Key") }
                    return@launch
                }

                val result = analyzeSentence(articleId, sentenceId, sentenceText, apiKey, model, baseUrl, provider)
                _uiState.update {
                    it.copy(
                        sentenceAnalysis = it.sentenceAnalysis + (sentenceId to result),
                        isAnalyzing = 0L
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = 0L, analyzeError = "分析失败：${e.message}") }
            }
        }
    }

    fun saveToLocal() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSavingToLocal = true) }
                guardianRepository.saveToLocal(articleId)
                _uiState.update { it.copy(isSavingToLocal = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSavingToLocal = false, analyzeError = "保存失败：${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(analyzeError = null) }
    }
}
