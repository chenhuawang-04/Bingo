package com.xty.englishhelper.ui.screen.article

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import com.xty.englishhelper.domain.usecase.article.DeleteArticleUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleListUseCase
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ArticleListUiState(
    val allArticles: List<Article> = emptyList(),
    val articles: List<Article> = emptyList(),
    val categories: List<ArticleCategory> = emptyList(),
    val selectedCategoryId: Long? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val evaluatingIds: Set<Long> = emptySet(),
    val lengthFilter: ArticleLengthFilter = ArticleLengthFilter.ALL,
    val scoreFilter: ArticleScoreFilter = ArticleScoreFilter.ALL,
    val sortOption: ArticleSortOption = ArticleSortOption.DEFAULT
)

@HiltViewModel
class ArticleListViewModel @Inject constructor(
    private val getArticleList: GetArticleListUseCase,
    private val deleteArticleUseCase: DeleteArticleUseCase,
    private val articleRepository: ArticleRepository,
    private val articleAiRepository: ArticleAiRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArticleListUiState())
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private var hasInitializedCategory = false

    init {
        // Cleanup old unsaved online articles on entry
        viewModelScope.launch {
            try {
                val cutoff = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(7)
                articleRepository.deleteUnsavedArticlesBefore(cutoff)
            } catch (e: Exception) {
                Log.w("ArticleListVM", "Cleanup unsaved articles failed", e)
            }
        }

        viewModelScope.launch {
            articleRepository.ensureDefaultCategories()
        }

        viewModelScope.launch {
            articleRepository.getArticleCategories().collectLatest { categories ->
                val sorted = categories.sortedBy { it.id }
                if (sorted.isNotEmpty()) {
                    val defaultId = sorted.firstOrNull { it.id == ArticleCategoryDefaults.DEFAULT_ID }?.id
                        ?: sorted.first().id
                    val currentId = selectedCategoryId.value
                    val resolvedId = when {
                        !hasInitializedCategory && currentId == null -> defaultId
                        currentId == null -> null
                        sorted.none { it.id == currentId } -> defaultId
                        else -> currentId
                    }
                    if (resolvedId != currentId) {
                        selectedCategoryId.value = resolvedId
                    }
                    if (!hasInitializedCategory) {
                        hasInitializedCategory = true
                    }
                } else {
                    selectedCategoryId.value = null
                }
                _uiState.update { it.copy(categories = sorted, selectedCategoryId = selectedCategoryId.value) }
            }
        }

        viewModelScope.launch {
            selectedCategoryId
                .flatMapLatest { categoryId ->
                    if (categoryId == null) {
                        getArticleList()
                    } else {
                        articleRepository.getArticlesByCategory(categoryId)
                    }
                }
                .collectLatest { articles ->
                    _uiState.update { state ->
                        state.withPresentedArticles(articles)
                    }
                }
        }
    }

    fun setLengthFilter(filter: ArticleLengthFilter) {
        _uiState.update { it.copy(lengthFilter = filter).applyPresentation() }
    }

    fun setScoreFilter(filter: ArticleScoreFilter) {
        _uiState.update { it.copy(scoreFilter = filter).applyPresentation() }
    }

    fun setSortOption(option: ArticleSortOption) {
        _uiState.update { it.copy(sortOption = option).applyPresentation() }
    }

    fun selectCategory(categoryId: Long?) {
        selectedCategoryId.value = categoryId
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun createCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                val id = articleRepository.createCategory(trimmed)
                if (id > 0) {
                    selectCategory(id)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "unknown"
                _uiState.update { it.copy(error = "创建分类失败：$msg") }
            }
        }
    }

    fun moveArticleToCategory(articleId: Long, categoryId: Long) {
        viewModelScope.launch {
            try {
                articleRepository.updateArticleCategory(articleId, categoryId)
            } catch (e: Exception) {
                val msg = e.message ?: "unknown"
                _uiState.update { it.copy(error = "更新分类失败：$msg") }
            }
        }
    }

    fun deleteArticle(articleId: Long) {
        viewModelScope.launch {
            try {
                deleteArticleUseCase(articleId)
            } catch (e: Exception) {
                Log.e("ArticleListVM", "Delete article failed: $articleId", e)
                val msg = e.message ?: "unknown"
                _uiState.update { it.copy(error = "Delete failed: $msg") }
            }
        }
    }

    fun reEvaluateArticle(articleId: Long) {
        viewModelScope.launch {
            if (_uiState.value.evaluatingIds.contains(articleId)) return@launch
            _uiState.update { it.copy(evaluatingIds = it.evaluatingIds + articleId) }
            try {
                val article = articleRepository.getArticleByIdOnce(articleId) ?: return@launch
                val config = settingsDataStore.getFastAiConfig()
                if (config.apiKey.isBlank() || config.model.isBlank()) {
                    _uiState.update { it.copy(error = "快速模型未配置，无法评估") }
                    return@launch
                }
                val excerpt = buildExcerpt(article.content)
                val result = withContext(Dispatchers.IO) {
                    articleAiRepository.evaluateArticleSuitability(
                        title = article.title,
                        excerpt = excerpt,
                        trailText = article.summary.takeIf { it.isNotBlank() },
                        source = article.source.takeIf { it.isNotBlank() },
                        section = null,
                        wordCount = article.wordCount.takeIf { it > 0 },
                        url = article.domain.takeIf { it.isNotBlank() },
                        apiKey = config.apiKey,
                        model = config.model,
                        baseUrl = config.baseUrl,
                        provider = config.provider
                    )
                }
                val now = System.currentTimeMillis()
                val modelKey = "${config.providerName}|${config.baseUrl.trimEnd('/')}|${config.model}"
                articleRepository.updateSuitabilityById(
                    articleId = articleId,
                    score = result.score,
                    reason = result.reason,
                    evaluatedAt = now,
                    modelKey = modelKey
                )
            } catch (e: Exception) {
                val msg = e.message ?: "unknown"
                _uiState.update { it.copy(error = "评估失败：$msg") }
            } finally {
                _uiState.update { it.copy(evaluatingIds = it.evaluatingIds - articleId) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun buildExcerpt(content: String): String {
        if (content.isBlank()) return ""
        val paragraphs = content.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val selected = if (paragraphs.size <= 3) paragraphs else paragraphs.take(3)
        val joined = selected.joinToString("\n\n")
        return if (joined.length > 2200) joined.take(2200) else joined
    }

    private fun ArticleListUiState.withPresentedArticles(items: List<Article>): ArticleListUiState {
        return copy(allArticles = items).applyPresentation()
    }

    private fun ArticleListUiState.applyPresentation(): ArticleListUiState {
        val presented = applyArticlePresentation(
            items = allArticles,
            lengthFilter = lengthFilter,
            scoreFilter = scoreFilter,
            sortOption = sortOption,
            wordCountOf = { it.wordCount.takeIf { count -> count > 0 } },
            scoreOf = { it.suitabilityScore },
            titleOf = { it.title }
        )
        return copy(articles = presented)
    }
}
