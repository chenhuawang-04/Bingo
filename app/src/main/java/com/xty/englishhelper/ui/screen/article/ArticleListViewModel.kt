package com.xty.englishhelper.ui.screen.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.usecase.article.DeleteArticleUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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
    private val deleteArticle: DeleteArticleUseCase
) : ViewModel() {

    fun getArticles(): Flow<List<Article>> {
        return getArticleList()
    }

    fun deleteArticle(articleId: Long) {
        viewModelScope.launch {
            try {
                deleteArticle(articleId)
            } catch (e: Exception) {
                // Error handled by UI state
            }
        }
    }
}
