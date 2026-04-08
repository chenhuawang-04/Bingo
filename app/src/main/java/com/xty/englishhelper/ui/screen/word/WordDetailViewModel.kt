package com.xty.englishhelper.ui.screen.word

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.tts.TtsManager
import com.xty.englishhelper.domain.model.CloudExampleSource
import com.xty.englishhelper.domain.usecase.article.GetWordExamplesUseCase
import com.xty.englishhelper.domain.usecase.dictionary.GetCloudWordExamplesUseCase
import com.xty.englishhelper.domain.usecase.pool.GetWordPoolsUseCase
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase
import com.xty.englishhelper.domain.usecase.word.GetAssociatedWordsUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordByIdUseCase
import com.xty.englishhelper.domain.usecase.word.ResolveLinkedWordsUseCase
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
    private val resolveLinkedWords: ResolveLinkedWordsUseCase,
    private val getAssociatedWords: GetAssociatedWordsUseCase,
    private val getWordExamples: GetWordExamplesUseCase,
    private val getCloudWordExamples: GetCloudWordExamplesUseCase,
    private val getWordPools: GetWordPoolsUseCase,
    private val ttsManager: TtsManager
) : ViewModel() {

    private var wordId: Long = savedStateHandle["wordId"] ?: 0L
    private var dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L
    private var cloudExamplesJob: Job? = null
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val word = getWordById(wordId)
                _uiState.update { it.copy(word = word, isLoading = false) }

                if (word != null) {
                    // Resolve linked words (synonyms, similar words, cognates)
                    val allSpellings = word.synonyms.map { it.word } +
                            word.similarWords.map { it.word } +
                            word.cognates.map { it.word }
                    if (allSpellings.isNotEmpty()) {
                        val linkedIds = resolveLinkedWords(dictionaryId, allSpellings)
                        _uiState.update { it.copy(linkedWordIds = linkedIds) }
                    }

                    // Load associated words
                    val associated = getAssociatedWords(wordId)
                    _uiState.update { it.copy(associatedWords = associated) }

                    // Load word examples from articles
                    try {
                        val examples = getWordExamples(wordId)
                        _uiState.update { it.copy(examples = examples) }
                    } catch (e: Exception) {
                        Log.w("WordDetailVM", "Examples loading failed for wordId=$wordId", e)
                    }

                    // Load word pools
                    try {
                        val pools = getWordPools(wordId)
                        _uiState.update { it.copy(pools = pools) }
                    } catch (e: Exception) {
                        Log.w("WordDetailVM", "Pools loading failed for wordId=$wordId", e)
                    }

                    loadCloudExamples(word.spelling, _uiState.value.cloudExampleSource)
                } else {
                    cloudExamplesJob?.cancel()
                    _uiState.update {
                        it.copy(
                            cloudExamplesLoading = false,
                            cloudExamples = emptyList(),
                            cloudExamplesError = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
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
