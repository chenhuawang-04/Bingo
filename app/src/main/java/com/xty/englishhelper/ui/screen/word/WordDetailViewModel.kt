package com.xty.englishhelper.ui.screen.word

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.usecase.dictionary.GetCloudWordExamplesUseCase
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordByIdUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordPresentationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWordById: GetWordByIdUseCase,
    private val deleteWord: DeleteWordUseCase,
    private val getCloudWordExamples: GetCloudWordExamplesUseCase,
    private val getWordPresentation: GetWordPresentationUseCase,
    private val ttsManager: TtsManager
) : ViewModel() {

    private var wordId: Long = savedStateHandle["wordId"] ?: 0L
    private var dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L
    private var cloudExamplesJob: Job? = null
    private var wordLoadJob: Job? = null
    private var detailsJob: Job? = null
    private var cloudExamplesRequestVersion: Long = 0L

    private val _uiState = MutableStateFlow(WordDetailUiState())
    val uiState: StateFlow<WordDetailUiState> = _uiState.asStateFlow()

    init {
        observeTtsState()
        if (wordId != 0L) {
            loadWord()
        }
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            ttsManager.state.collect { tts ->
                _uiState.update { it.copy(ttsState = tts) }
            }
        }
    }

    /**
     * Explicitly load a word by ID. Used by the wide-screen detail pane
     * where wordId/dictionaryId are not available from SavedStateHandle.
     */
    fun loadWord(wordId: Long, dictionaryId: Long) {
        this.wordId = wordId
        this.dictionaryId = dictionaryId
        loadWord()
    }

    fun loadWord() {
        val requestedWordId = wordId
        wordLoadJob?.cancel()
        detailsJob?.cancel()
        cloudExamplesJob?.cancel()
        wordLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    linkedWordIds = emptyMap(),
                    associatedWords = emptyList(),
                    examples = emptyList(),
                    pools = emptyList(),
                    clusters = emptyList(),
                    clusterReviews = emptyList(),
                    edgePreviews = emptyList(),
                    phrases = emptyList(),
                    detailsLoading = false,
                    detailsError = null,
                    cloudExamples = emptyList(),
                    cloudExamplesLoading = false,
                    cloudExamplesError = null
                )
            }
            try {
                val word = getWordById(requestedWordId)
                if (requestedWordId != wordId) return@launch
                _uiState.update { it.copy(word = word, isLoading = false) }

                if (word != null) {
                    loadPresentation(word)
                    loadCloudExamples(word.spelling, _uiState.value.cloudExampleSource)
                } else {
                    cloudExamplesJob?.cancel()
                    _uiState.update {
                        it.copy(
                            cloudExamplesLoading = false,
                            cloudExamples = emptyList(),
                            phrases = emptyList(),
                            detailsLoading = false,
                            detailsError = null,
                            cloudExamplesError = null
                        )
                    }
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun retryDetails() {
        _uiState.value.word?.let(::loadPresentation)
    }

    private fun loadPresentation(word: WordDetails) {
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            _uiState.update { it.copy(detailsLoading = true, detailsError = null) }
            val details = getWordPresentation(word)
            if (_uiState.value.word?.id != word.id) return@launch
            _uiState.update {
                it.copy(
                    linkedWordIds = details.linkedWordIds,
                    associatedWords = details.associatedWords,
                    examples = details.examples,
                    pools = details.pools,
                    clusters = details.clusterReviews.map { review -> review.cluster },
                    clusterReviews = details.clusterReviews,
                    edgePreviews = details.edgePreviews,
                    phrases = details.phrases,
                    detailsLoading = false,
                    detailsError = details.failedSections.takeIf { failed -> failed.isNotEmpty() }
                        ?.let { failed -> "${failed.size} 项本地信息暂时无法加载" }
                )
            }
        }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteWord(wordId, dictionaryId)
            onDeleted()
        }
    }

    fun toggleSpeakWord() {
        val word = _uiState.value.word ?: return
        val sessionId = ttsManager.wordSessionId(word.id)
        val isCurrent = _uiState.value.ttsState.sessionId == sessionId

        if (_uiState.value.ttsState.isSpeaking && isCurrent) {
            ttsManager.pause()
            return
        }

        viewModelScope.launch {
            ttsManager.speakWord(word.id, word.spelling)
        }
    }

    fun speakWordUs() {
        val word = _uiState.value.word ?: return
        viewModelScope.launch {
            ttsManager.speakWord(word.id, word.spelling, localeOverride = "en-US")
        }
    }

    fun speakWordUk() {
        val word = _uiState.value.word ?: return
        viewModelScope.launch {
            ttsManager.speakWord(word.id, word.spelling, localeOverride = "en-GB")
        }
    }

    fun clearTtsError() {
        ttsManager.clearError()
    }

    fun selectCloudExampleSource(source: CloudExampleSource) {
        val state = _uiState.value
        if (state.cloudExampleSource == source) return
        _uiState.update {
            it.copy(
                cloudExampleSource = source,
                cloudExamples = emptyList(),
                cloudExamplesError = null
            )
        }
        val spelling = state.word?.spelling ?: return
        loadCloudExamples(spelling, source)
    }

    private fun loadCloudExamples(word: String, source: CloudExampleSource) {
        cloudExamplesJob?.cancel()
        val requestVersion = ++cloudExamplesRequestVersion
        cloudExamplesJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cloudExamplesLoading = true,
                    cloudExamplesError = null
                )
            }
            runCatching {
                getCloudWordExamples(word = word, source = source)
            }.onSuccess { examples ->
                val currentState = _uiState.value
                if (requestVersion != cloudExamplesRequestVersion ||
                    currentState.word?.spelling != word ||
                    currentState.cloudExampleSource != source
                ) {
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        cloudExamples = examples,
                        cloudExamplesLoading = false,
                        cloudExamplesError = null
                    )
                }
            }.onFailure { error ->
                val currentState = _uiState.value
                if (requestVersion != cloudExamplesRequestVersion ||
                    currentState.word?.spelling != word ||
                    currentState.cloudExampleSource != source
                ) {
                    return@onFailure
                }
                _uiState.update {
                    it.copy(
                        cloudExamples = emptyList(),
                        cloudExamplesLoading = false,
                        cloudExamplesError = error.message
                    )
                }
            }
        }
    }
}
