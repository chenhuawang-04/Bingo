package com.xty.englishhelper.ui.screen.article

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import com.xty.englishhelper.domain.repository.GuardianRepository
import com.xty.englishhelper.domain.usecase.article.DeleteArticleUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleListUseCase
import com.xty.englishhelper.domain.repository.ArticleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArticleListUiState(
    val articles: List<Article> = emptyList(),
    val categories: List<ArticleCategory> = emptyList(),
    val selectedCategoryId: Long? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ArticleListViewModel @Inject constructor(
    private val getArticleList: GetArticleListUseCase,
    private val deleteArticleUseCase: DeleteArticleUseCase,
    private val guardianRepository: GuardianRepository,
    private val articleRepository: ArticleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArticleListUiState())
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private var hasInitializedCategory = false

    init {
        // Cleanup old unsaved Guardian articles on entry
        viewModelScope.launch {
            try {
                guardianRepository.cleanupUnsavedArticles()
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
                    _uiState.update { it.copy(articles = articles) }
                }
        }
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
