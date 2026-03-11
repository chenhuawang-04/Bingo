package com.xty.englishhelper.ui.screen.guardian

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.repository.GuardianRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class GuardianSection(val key: String, val label: String, val group: String = "")

data class GuardianBrowseItem(
    val title: String,
    val url: String,
    val trailText: String? = null,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val coverImageUrl: String? = null,
    val wordCount: Int? = null,
    val isAuthorLoading: Boolean = false,
    val isWordCountLoading: Boolean = false,
    val detailError: String? = null
)

val defaultSections = listOf(
    // Main
    GuardianSection("international", "\u9996\u9875", "Main"),
    GuardianSection("world", "World", "Main"),
    GuardianSection("us-news", "US News", "Main"),
    GuardianSection("uk-news", "UK News", "Main"),
    // Topics
    GuardianSection("science", "Science", "Topics"),
    GuardianSection("uk/technology", "Tech", "Topics"),
    GuardianSection("uk/environment", "Environment", "Topics"),
    GuardianSection("global-development", "Development", "Topics"),
    GuardianSection("uk/business", "Business", "Topics"),
    // Culture
    GuardianSection("books", "Books", "Culture"),
    GuardianSection("uk/culture", "Culture", "Culture"),
    GuardianSection("uk/film", "Film", "Culture"),
    GuardianSection("music", "Music", "Culture"),
    GuardianSection("stage", "Stage", "Culture"),
    GuardianSection("uk/tv-and-radio", "TV & Radio", "Culture"),
    GuardianSection("artanddesign", "Art", "Culture"),
    GuardianSection("games", "Games", "Culture"),
    // Lifestyle
    GuardianSection("food", "Food", "Lifestyle"),
    GuardianSection("fashion", "Fashion", "Lifestyle"),
    GuardianSection("uk/travel", "Travel", "Lifestyle"),
    // Sport
    GuardianSection("uk/sport", "Sport", "Sport"),
    GuardianSection("football", "Football", "Sport"),
    GuardianSection("sport/cricket", "Cricket", "Sport"),
    GuardianSection("sport/tennis", "Tennis", "Sport"),
    GuardianSection("sport/formulaone", "F1", "Sport"),
    // Opinion
    GuardianSection("uk/commentisfree", "Opinion", "Opinion"),
    // World regions
    GuardianSection("world/middleeast", "Middle East", "World"),
    GuardianSection("world/ukraine", "Ukraine", "World"),
    GuardianSection("us-news/us-politics", "US Politics", "World")
)

data class GuardianBrowseUiState(
    val sections: List<GuardianSection> = defaultSections,
    val selectedSection: String = "international",
    val articles: List<GuardianBrowseItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingArticle: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GuardianBrowseViewModel @Inject constructor(
    private val guardianRepository: GuardianRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuardianBrowseUiState())
    val uiState: StateFlow<GuardianBrowseUiState> = _uiState.asStateFlow()

    private var detailJob: Job? = null

    init {
        loadSection("international")
    }

    fun loadSection(section: String) {
        detailJob?.cancel()
        detailJob = null
        _uiState.update { it.copy(selectedSection = section, isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val articles = guardianRepository.getSectionArticles(section)
                val items = articles.map { preview ->
                    GuardianBrowseItem(
                        title = preview.title,
                        url = preview.url,
                        trailText = preview.trailText,
                        thumbnailUrl = preview.thumbnailUrl,
                        author = preview.author,
                        isAuthorLoading = true,
                        isWordCountLoading = true
                    )
                }
                _uiState.update { it.copy(articles = items, isLoading = false) }
                detailJob = viewModelScope.launch {
                    loadArticleDetails(items)
                }
            } catch (e: Exception) {
                Log.e("GuardianBrowseVM", "Failed to load section: $section", e)
                _uiState.update { it.copy(isLoading = false, error = "加载失败：${e.message}") }
            }
        }
    }

    private suspend fun loadArticleDetails(items: List<GuardianBrowseItem>) {
        if (items.isEmpty()) return
        val concurrency = settingsDataStore.guardianDetailConcurrency.first().coerceIn(1, 10)
        val semaphore = Semaphore(concurrency)
        // Build URL -> index map for O(1) lookup instead of O(n) scan per update
        val urlToIndex = items.withIndex().associate { (i, item) -> item.url to i }
        coroutineScope {
            items.forEach { item ->
                launch {
                    semaphore.withPermit {
                        try {
                            val detail = guardianRepository.getArticleDetail(item.url)
                            val wordCount = detail.paragraphs
                                .joinToString(" ") { it.text }
                                .split(Regex("\\s+"))
                                .count { it.isNotBlank() }
                            _uiState.update { state ->
                                val idx = urlToIndex[item.url]
                                if (idx != null && idx < state.articles.size && state.articles[idx].url == item.url) {
                                    val updated = state.articles.toMutableList()
                                    updated[idx] = updated[idx].copy(
                                        author = detail.author.takeIf { it.isNotBlank() },
                                        coverImageUrl = detail.coverImageUrl,
                                        wordCount = wordCount,
                                        isAuthorLoading = false,
                                        isWordCountLoading = false,
                                        detailError = null
                                    )
                                    state.copy(articles = updated)
                                } else {
                                    state
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("GuardianBrowseVM", "Detail load failed: ${item.url}", e)
                            _uiState.update { state ->
                                val idx = urlToIndex[item.url]
                                if (idx != null && idx < state.articles.size && state.articles[idx].url == item.url) {
                                    val updated = state.articles.toMutableList()
                                    updated[idx] = updated[idx].copy(
                                        isAuthorLoading = false,
                                        isWordCountLoading = false,
                                        detailError = e.message
                                    )
                                    state.copy(articles = updated)
                                } else {
                                    state
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        loadSection(_uiState.value.selectedSection)
    }

    fun openArticle(
        articleUrl: String,
        onNavigate: (Long) -> Unit
    ) {
        if (_uiState.value.isLoadingArticle) return
        _uiState.update { it.copy(isLoadingArticle = true, error = null) }

        viewModelScope.launch {
            try {
                val detail = guardianRepository.getArticleDetail(articleUrl)
                val articleId = guardianRepository.createTemporaryArticle(detail)
                _uiState.update { it.copy(isLoadingArticle = false) }
                onNavigate(articleId)
            } catch (e: Exception) {
                Log.e("GuardianBrowseVM", "Failed to open article: $articleUrl", e)
                _uiState.update { it.copy(isLoadingArticle = false, error = "文章加载失败：${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
