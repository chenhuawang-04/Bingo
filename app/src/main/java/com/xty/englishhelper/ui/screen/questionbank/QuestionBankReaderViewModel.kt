package com.xty.englishhelper.ui.screen.questionbank

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.AnswerSource
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.CollectedWord
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.DifficultyLevel
import com.xty.englishhelper.domain.model.ParagraphAnalysisResult
import com.xty.englishhelper.domain.model.PracticeRecord
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.SourceVerifyStatus
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.TtsState
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.usecase.article.AnalyzeParagraphUseCase
import com.xty.englishhelper.domain.usecase.article.CreateArticleUseCase
import com.xty.englishhelper.domain.usecase.article.ParseArticleUseCase
import com.xty.englishhelper.domain.usecase.article.QuickAnalyzeWordUseCase
import com.xty.englishhelper.domain.usecase.article.ScanWordLinksUseCase
import com.xty.englishhelper.domain.usecase.article.TranslateParagraphUseCase
import com.xty.englishhelper.domain.usecase.questionbank.questionBankContentId
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.organize.BackgroundOrganizeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
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
    val showingAnswers: Boolean = false,
    val wrongItemIds: Set<Long> = emptySet(),
    val practiceResults: Map<Long, Boolean> = emptyMap(),
    // Source
    val linkedArticleId: Long? = null,
    val isVerifying: Boolean = false,
    val isScanningAnswers: Boolean = false,
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
    private val createArticle: CreateArticleUseCase,
    private val parseArticle: ParseArticleUseCase,
    private val ttsManager: TtsManager,
    private val settingsDataStore: SettingsDataStore,
    private val dictionaryRepository: DictionaryRepository,
    private val unitRepository: UnitRepository,
    private val backgroundOrganizeManager: BackgroundOrganizeManager
) : ViewModel() {

    private val groupId: Long = savedStateHandle["groupId"] ?: 0L
    private val contentId: Long = questionBankContentId(groupId)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var translationJob: Job? = null

    init {
        observeTtsState()
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

                val paragraphs = repository.getParagraphs(groupId)
                val items = repository.getItemsByGroup(groupId)
                val wrongIds = repository.getWrongItemIds(groupId).toSet()
                val linkedId = repository.getLinkedArticleId(groupId)
                val paper = repository.getExamPaperById(group.examPaperId)

                // Scan word links
                val links = if (paragraphs.isNotEmpty()) {
                    val paraWithContentId = paragraphs.map { it.copy(articleId = contentId) }
                    scanWordLinks(paraWithContentId)
                } else emptyList()

                val linkMap = links.groupBy { it.matchedToken.lowercase() }

                _uiState.update {
                    it.copy(
                        group = group,
                        paperTitle = paper?.title ?: "",
                        paragraphs = paragraphs,
                        items = items,
                        wordLinks = links,
                        wordLinkMap = linkMap,
                        wrongItemIds = wrongIds,
                        linkedArticleId = linkedId,
                        isLoading = false
                    )
                }

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
        val existing = _uiState.value.collectedWords.find { it.word == word }
        if (existing != null) return
        _uiState.update {
            it.copy(collectedWords = it.collectedWords + CollectedWord(word, contextSentence, isAnalyzing = true))
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
        if (_uiState.value.isSubmitted) return
        _uiState.update { it.copy(selectedAnswers = it.selectedAnswers + (itemId to answer)) }
    }

    fun submitAnswers() {
        val state = _uiState.value
        if (state.isSubmitted || state.items.isEmpty()) return

        viewModelScope.launch {
            val results = mutableMapOf<Long, Boolean>()
            val records = mutableListOf<PracticeRecord>()
            val now = System.currentTimeMillis()

            for (item in state.items) {
                val userAnswer = state.selectedAnswers[item.id]
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
                    practiceResults = results,
                    wrongItemIds = wrongIds,
                    items = refreshedItems
                )
            }
        }
    }

    fun showAnswers() {
        _uiState.update { it.copy(showingAnswers = true) }
    }

    fun retryPractice() {
        _uiState.update {
            it.copy(
                selectedAnswers = emptyMap(),
                isSubmitted = false,
                showingAnswers = false,
                practiceResults = emptyMap()
            )
        }
    }

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
        _uiState.update { it.copy(isVerifying = true) }
        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.SEARCH)
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isVerifying = false, error = "请先配置搜索模型") }
                    return@launch
                }

                val result = aiRepository.verifySource(
                    group.passageText, group.sourceUrl ?: "",
                    config.apiKey, config.model, config.baseUrl, config.provider
                )

                if (result.matched) {
                    repository.updateSourceVerification(groupId, 1, null)
                    if (!result.sourceUrl.isNullOrBlank()) {
                        repository.updateSourceUrl(groupId, result.sourceUrl)
                        // updateSourceUrl resets verification, so re-mark as verified
                        repository.updateSourceVerification(groupId, 1, null)
                    }
                    createLinkedArticle(result, group)
                } else {
                    repository.updateSourceVerification(groupId, -1, result.errorMessage)
                }

                val updated = repository.getGroupById(groupId)
                val linkedId = repository.getLinkedArticleId(groupId)
                _uiState.update { it.copy(group = updated, linkedArticleId = linkedId, isVerifying = false) }
            } catch (e: Exception) {
                repository.updateSourceVerification(groupId, -1, e.message)
                val updated = repository.getGroupById(groupId)
                _uiState.update { it.copy(group = updated, isVerifying = false) }
            }
        }
    }

    private suspend fun createLinkedArticle(
        result: com.xty.englishhelper.domain.repository.VerifyResult,
        group: QuestionGroup
    ) {
        val articleId = createArticle(
            title = result.articleTitle ?: group.sectionLabel ?: "来源文章",
            content = result.articleContent ?: "",
            sourceType = ArticleSourceType.AI,
            author = result.articleAuthor ?: "",
            source = result.sourceUrl ?: group.sourceUrl ?: "",
            summary = result.articleSummary ?: "",
            paragraphs = result.articleParagraphs?.mapIndexed { i, text ->
                ArticleParagraph(paragraphIndex = i, text = text)
            } ?: emptyList()
        )
        parseArticle(articleId)
        repository.linkSourceArticle(groupId, articleId)
    }

    // ── Scan Answers ──

    fun scanAnswerImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(isScanningAnswers = true) }
        viewModelScope.launch {
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.OCR)
                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isScanningAnswers = false, error = "请先配置 OCR 模型") }
                    return@launch
                }

                val imageBytes = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                }

                val numbers = _uiState.value.items.map { it.questionNumber }
                val results = aiRepository.scanAnswers(
                    imageBytes, numbers,
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

    // ── Utility ──

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        translationJob?.cancel()
    }
}
