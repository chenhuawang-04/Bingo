package com.xty.englishhelper.ui.screen.article

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.repository.GuardianRepository
import com.xty.englishhelper.domain.usecase.article.DeleteArticleUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArticleListUiState(
    val articles: List<Article> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ArticleListViewModel @Inject constructor(
    private val getArticleList: GetArticleListUseCase,
    private val deleteArticleUseCase: DeleteArticleUseCase,
    private val guardianRepository: GuardianRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArticleListUiState())
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()

    init {
        // Cleanup old unsaved Guardian articles on entry
        viewModelScope.launch {
            try {
                guardianRepository.cleanupUnsavedArticles()
            } catch (e: Exception) {
                Log.w("ArticleListVM", "Cleanup unsaved articles failed", e)
            }
        }
    }

    fun getArticles(): Flow<List<Article>> {
        return getArticleList()
    }

    fun deleteArticle(articleId: Long) {
        viewModelScope.launch {
            try {
                deleteArticleUseCase(articleId)
            } catch (e: Exception) {
                Log.e("ArticleListVM", "Delete article failed: $articleId", e)
                _uiState.update { it.copy(error = "Delete failed: ${e.message ?: \"unknown\"}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
