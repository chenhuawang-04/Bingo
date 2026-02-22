package com.xty.englishhelper.ui.screen.article

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSentence
import com.xty.englishhelper.domain.model.ArticleStatistics
import com.xty.englishhelper.domain.model.ArticleWordLink
import com.xty.englishhelper.domain.model.SentenceAnalysisResult
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.usecase.article.AnalyzeSentenceUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleDetailUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleStatisticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArticleReaderUiState(
    val article: Article? = null,
    val statistics: ArticleStatistics? = null,
    val sentences: List<ArticleSentence>? = null,
    val wordLinks: List<ArticleWordLink>? = null,
    val sentenceAnalysis: Map<Long, SentenceAnalysisResult> = emptyMap(),
    val isAnalyzing: Long = 0L,
    val analyzeError: String? = null
)

@HiltViewModel
class ArticleReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArticleDetail: GetArticleDetailUseCase,
    private val getStatistics: GetArticleStatisticsUseCase,
    private val analyzeSentence: AnalyzeSentenceUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val repository: ArticleRepository
) : ViewModel() {

    private val articleId: Long = savedStateHandle["articleId"] ?: 0L
    val scrollToSentenceId: Long = savedStateHandle["scrollToSentenceId"] ?: 0L

    private val _uiState = MutableStateFlow(ArticleReaderUiState())
    val uiState: StateFlow<ArticleReaderUiState> = _uiState.asStateFlow()

    private var pollStarted = false

    init {
        subscribeToArticleUpdates()
        loadStatistics()
        if (articleId != 0L) {
            startParseStatusPolling()
        }
    }

    fun getArticleFlow(): Flow<Article?> {
        return getArticleDetail(articleId)
    }

    private fun subscribeToArticleUpdates() {
        viewModelScope.launch {
            getArticleDetail(articleId).collect { article ->
                _uiState.update { it.copy(article = article) }

                // Load sentences and word links from database
                if (article != null) {
                    try {
                        val sentences = repository.getSentences(articleId)
                        val wordLinks = repository.getWordLinks(articleId)
                        _uiState.update {
                            it.copy(sentences = sentences, wordLinks = wordLinks)
                        }
                    } catch (_: Exception) {
                        // Data loading failure is non-critical
                    }
                }
            }
        }
    }

    private suspend fun loadArticleOnce() {
        try {
            val article = repository.getArticleByIdOnce(articleId)
            if (article != null) {
                _uiState.update { it.copy(article = article) }
                val sentences = repository.getSentences(articleId)
                val wordLinks = repository.getWordLinks(articleId)
                _uiState.update {
                    it.copy(sentences = sentences, wordLinks = wordLinks)
                }
            }
        } catch (_: Exception) {
            // Loading failure is non-critical
        }
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val stats = getStatistics(articleId)
                _uiState.update { it.copy(statistics = stats) }
            } catch (_: Exception) {
                // Stats loading failure is non-critical
            }
        }
    }

    private fun startParseStatusPolling() {
        if (pollStarted) return  // Prevent duplicate polling
        pollStarted = true

        viewModelScope.launch {
            while (true) {
                val article = _uiState.value.article
                if (article?.parseStatus == ArticleParseStatus.DONE || article?.parseStatus == ArticleParseStatus.FAILED) {
                    break
                }
                delay(2000)
                // Use suspend function directly to avoid nested coroutine
                loadArticleOnce()
            }
        }
    }

    fun analyzeSentence(sentenceId: Long, sentenceText: String) {
        val analyzeId = sentenceId
        _uiState.update { it.copy(isAnalyzing = analyzeId, analyzeError = null) }

        viewModelScope.launch {
            try {
                val apiKey = settingsDataStore.apiKey.first()
                val model = settingsDataStore.model.first()
                val baseUrl = settingsDataStore.baseUrl.first()

                if (apiKey.isBlank()) {
                    _uiState.update { it.copy(isAnalyzing = 0L, analyzeError = "请先在设置中配置 API Key") }
                    return@launch
                }

                val result = analyzeSentence(articleId, sentenceId, sentenceText, apiKey, model, baseUrl)
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

    fun clearError() {
        _uiState.update { it.copy(analyzeError = null) }
    }
}
