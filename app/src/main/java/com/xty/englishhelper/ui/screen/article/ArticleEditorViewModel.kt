package com.xty.englishhelper.ui.screen.article

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.usecase.article.CreateArticleUseCase
import com.xty.englishhelper.domain.usecase.article.ExtractArticleFromImagesUseCase
import com.xty.englishhelper.domain.usecase.article.GetArticleDetailUseCase
import com.xty.englishhelper.domain.usecase.article.ParseArticleUseCase
import com.xty.englishhelper.domain.usecase.article.UpdateArticleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArticleEditorUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val content: String = "",
    val domain: String = "",
    val difficulty: Float = 0f,
    val imageUris: List<Uri> = emptyList(),
    val isOcrLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedSuccessfully: Boolean = false,
    val savedArticleId: Long = 0L
)

@HiltViewModel
class ArticleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val createArticle: CreateArticleUseCase,
    private val updateArticle: UpdateArticleUseCase,
    private val getArticleDetail: GetArticleDetailUseCase,
    private val parseArticle: ParseArticleUseCase,
    private val extractFromImages: ExtractArticleFromImagesUseCase,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val articleId: Long = savedStateHandle["articleId"] ?: 0L

    private val _uiState = MutableStateFlow(ArticleEditorUiState())
    val uiState: StateFlow<ArticleEditorUiState> = _uiState.asStateFlow()

    init {
        if (articleId != 0L) {
            loadArticle()
        }
    }

    private fun loadArticle() {
        viewModelScope.launch {
            getArticleDetail(articleId).first()?.let { article ->
                _uiState.update {
                    it.copy(
                        isEditing = true,
                        title = article.title,
                        content = article.content,
                        domain = article.domain
                    )
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value) }
    }

    fun onContentChange(value: String) {
        _uiState.update { it.copy(content = value) }
    }

    fun onDomainChange(value: String) {
        _uiState.update { it.copy(domain = value) }
    }

    fun addImages(uris: List<Uri>) {
        _uiState.update { it.copy(imageUris = it.imageUris + uris) }
    }

    fun removeImage(index: Int) {
        _uiState.update {
            it.copy(imageUris = it.imageUris.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun extractWithAi(readImageBytes: suspend (Uri) -> ByteArray) {
        val uris = _uiState.value.imageUris
        if (uris.isEmpty()) {
            _uiState.update { it.copy(error = "请先选择图片") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isOcrLoading = true, error = null) }
            try {
                val apiKey = settingsDataStore.apiKey.first()
                val model = settingsDataStore.model.first()
                val baseUrl = settingsDataStore.baseUrl.first()

                if (apiKey.isBlank()) {
                    _uiState.update { it.copy(isOcrLoading = false, error = "请先在设置中配置 API Key") }
                    return@launch
                }

                val imageBytesList = uris.map { readImageBytes(it) }
                val result = extractFromImages(imageBytesList, _uiState.value.title.ifBlank { null }, apiKey, model, baseUrl)

                _uiState.update {
                    it.copy(
                        isOcrLoading = false,
                        title = result.title.ifBlank { it.title },
                        content = result.content.ifBlank { it.content },
                        domain = result.domain.ifBlank { it.domain },
                        difficulty = if (result.difficulty > 0) result.difficulty else it.difficulty
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isOcrLoading = false, error = "AI 识别失败：${e.message}") }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "请输入文章标题") }
            return
        }
        if (state.content.isBlank()) {
            _uiState.update { it.copy(error = "请输入文章内容") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val savedId = if (state.isEditing) {
                    val article = getArticleDetail(articleId).first()!!
                    updateArticle(
                        article.copy(
                            title = state.title.trim(),
                            content = state.content.trim(),
                            domain = state.domain.trim(),
                            parseStatus = ArticleParseStatus.PENDING
                        )
                    )
                } else {
                    createArticle(
                        title = state.title.trim(),
                        content = state.content.trim(),
                        sourceType = if (state.imageUris.isNotEmpty()) ArticleSourceType.AI else ArticleSourceType.MANUAL,
                        domain = state.domain.trim(),
                        difficultyAi = state.difficulty
                    )
                }

                // Trigger background parse
                viewModelScope.launch {
                    try {
                        parseArticle(savedId)
                    } catch (e: Exception) {
                        Log.w("ArticleEditorVM", "Parse failed for articleId=$savedId", e)
                    }
                }

                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true, savedArticleId = savedId) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "保存失败：${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
