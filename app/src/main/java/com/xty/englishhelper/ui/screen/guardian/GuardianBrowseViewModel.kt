package com.xty.englishhelper.ui.screen.guardian

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.remote.guardian.GuardianArticlePreview
import com.xty.englishhelper.domain.repository.GuardianRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuardianSection(val key: String, val label: String, val group: String = "")

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
    val articles: List<GuardianArticlePreview> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingArticle: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GuardianBrowseViewModel @Inject constructor(
    private val guardianRepository: GuardianRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuardianBrowseUiState())
    val uiState: StateFlow<GuardianBrowseUiState> = _uiState.asStateFlow()

    init {
        loadSection("international")
    }

    fun loadSection(section: String) {
        _uiState.update { it.copy(selectedSection = section, isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val articles = guardianRepository.getSectionArticles(section)
                _uiState.update { it.copy(articles = articles, isLoading = false) }
            } catch (e: Exception) {
                Log.e("GuardianBrowseVM", "Failed to load section: $section", e)
                _uiState.update { it.copy(isLoading = false, error = "加载失败：${e.message}") }
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
