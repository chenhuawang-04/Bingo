package com.xty.englishhelper.ui.screen.article

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.image.ImageCompressionManager
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.article.SmartParagraphSplitter
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.domain.repository.ArticleRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ParagraphInput(
    val text: String = "",
    val imageUri: Uri? = null,
    val type: ParagraphType = ParagraphType.TEXT
)

data class ArticleEditorUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val summary: String = "",
    val author: String = "",
    val source: String = "",
    val domain: String = "",
    val coverImageUri: Uri? = null,
    val paragraphs: List<ParagraphInput> = listOf(ParagraphInput()),
    val imageUris: List<Uri> = emptyList(),
    val isCompressing: Boolean = false,
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
    private val settingsDataStore: SettingsDataStore,
    private val repository: ArticleRepository,
    private val imageCompressionManager: ImageCompressionManager
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
                // Load paragraphs from DB
                val paragraphs = repository.getParagraphs(articleId)
                val paragraphInputs = if (paragraphs.isNotEmpty()) {
                    paragraphs.map { p ->
                        ParagraphInput(
                            text = p.text,
                            imageUri = p.imageUri?.let { Uri.parse(it) },
                            type = p.paragraphType
                        )
                    }
                } else {
                    // Fallback: split content into paragraphs
                    val splits = SmartParagraphSplitter.split(article.content)
                    splits.map { ParagraphInput(text = it) }
                }

                _uiState.update {
                    it.copy(
                        isEditing = true,
                        title = article.title,
                        summary = article.summary,
                        author = article.author,
                        source = article.source,
                        domain = article.domain,
                        coverImageUri = article.coverImageUri?.let { uri -> Uri.parse(uri) },
                        paragraphs = paragraphInputs.ifEmpty { listOf(ParagraphInput()) }
                    )
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _uiState.update { it.copy(title = value) }
    }

    fun onSummaryChange(value: String) {
        _uiState.update { it.copy(summary = value) }
    }

    fun onAuthorChange(value: String) {
        _uiState.update { it.copy(author = value) }
    }

    fun onSourceChange(value: String) {
        _uiState.update { it.copy(source = value) }
    }

    fun onDomainChange(value: String) {
        _uiState.update { it.copy(domain = value) }
    }

    fun onCoverImageSelected(uri: Uri?) {
        _uiState.update { it.copy(coverImageUri = uri) }
    }

    fun onParagraphTextChange(index: Int, text: String) {
        _uiState.update {
            val newParagraphs = it.paragraphs.toMutableList()
            if (index < newParagraphs.size) {
                newParagraphs[index] = newParagraphs[index].copy(text = text)
            }
            it.copy(paragraphs = newParagraphs)
        }
    }

    fun onParagraphImageSelected(index: Int, uri: Uri?) {
        _uiState.update {
            val newParagraphs = it.paragraphs.toMutableList()
            if (index < newParagraphs.size) {
                newParagraphs[index] = newParagraphs[index].copy(imageUri = uri)
            }
            it.copy(paragraphs = newParagraphs)
        }
    }

    fun addParagraph() {
        _uiState.update {
            it.copy(paragraphs = it.paragraphs + ParagraphInput())
        }
    }

    fun removeParagraph(index: Int) {
        _uiState.update {
            if (it.paragraphs.size <= 1) return@update it
            val newParagraphs = it.paragraphs.toMutableList()
            newParagraphs.removeAt(index)
            it.copy(paragraphs = newParagraphs)
        }
    }

    fun pasteFullText(text: String) {
        val splits = SmartParagraphSplitter.split(text)
        if (splits.isEmpty()) return
        _uiState.update {
            it.copy(paragraphs = splits.map { s -> ParagraphInput(text = s) })
        }
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
            _uiState.update { it.copy(isOcrLoading = true, isCompressing = false, error = null) }
            try {
                val config = settingsDataStore.getAiConfig(AiSettingsScope.OCR)

                if (config.apiKey.isBlank()) {
                    _uiState.update { it.copy(isOcrLoading = false, error = "请先在设置中配置 API Key") }
                    return@launch
                }

                val compressionConfig = settingsDataStore.getImageCompressionConfig()
                val imageBytesList = withContext(Dispatchers.IO) {
                    uris.map { uri -> readImageBytes(uri) }
                }
                if (compressionConfig.enabled) {
                    _uiState.update { it.copy(isCompressing = true) }
                }
                val compressedBytes = try {
                    imageCompressionManager.compressAll(imageBytesList, compressionConfig)
                } finally {
                    if (compressionConfig.enabled) {
                        _uiState.update { it.copy(isCompressing = false) }
                    }
                }
                val result = extractFromImages(
                    compressedBytes,
                    _uiState.value.title.ifBlank { null },
                    config.apiKey,
                    config.model,
                    config.baseUrl,
                    config.provider
                )

                // Auto-split OCR result into paragraphs
                val paragraphs = if (result.content.isNotBlank()) {
                    SmartParagraphSplitter.split(result.content).map { ParagraphInput(text = it) }
                } else {
                    _uiState.value.paragraphs
                }

                _uiState.update {
                    it.copy(
                        isOcrLoading = false,
                        title = result.title.ifBlank { it.title },
                        domain = result.domain.ifBlank { it.domain },
                        paragraphs = paragraphs
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
        val nonEmptyParagraphs = state.paragraphs.filter { it.text.isNotBlank() }
        if (nonEmptyParagraphs.isEmpty()) {
            _uiState.update { it.copy(error = "请输入文章内容") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Build content from paragraphs
                val content = nonEmptyParagraphs.joinToString("\n\n") { it.text.trim() }
                val paragraphModels = nonEmptyParagraphs.mapIndexed { index, p ->
                    ArticleParagraph(
                        paragraphIndex = index,
                        text = p.text.trim(),
                        imageUri = p.imageUri?.toString(),
                        paragraphType = p.type
                    )
                }

                val savedId = if (state.isEditing) {
                    val article = getArticleDetail(articleId).first()!!
                    // Update paragraphs
                    repository.deleteParagraphsByArticle(articleId)
                    repository.insertParagraphs(paragraphModels.map {
                        it.copy(articleId = articleId)
                    })
                    updateArticle(
                        article.copy(
                            title = state.title.trim(),
                            content = content,
                            domain = state.domain.trim(),
                            summary = state.summary.trim(),
                            author = state.author.trim(),
                            source = state.source.trim(),
                            coverImageUri = state.coverImageUri?.toString(),
                            parseStatus = ArticleParseStatus.PENDING
                        )
                    )
                } else {
                    createArticle(
                        title = state.title.trim(),
                        content = content,
                        sourceType = if (state.imageUris.isNotEmpty()) ArticleSourceType.AI else ArticleSourceType.MANUAL,
                        domain = state.domain.trim(),
                        summary = state.summary.trim(),
                        author = state.author.trim(),
                        source = state.source.trim(),
                        paragraphs = paragraphModels,
                        coverImageUri = state.coverImageUri?.toString()
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
