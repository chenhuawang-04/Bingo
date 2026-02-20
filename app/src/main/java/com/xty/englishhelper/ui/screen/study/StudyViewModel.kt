package com.xty.englishhelper.ui.screen.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.study.Rating
import com.xty.englishhelper.domain.usecase.study.GetDueWordsUseCase
import com.xty.englishhelper.domain.usecase.study.GetNewWordsUseCase
import com.xty.englishhelper.domain.usecase.study.PreviewIntervalsUseCase
import com.xty.englishhelper.domain.usecase.study.ReviewWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDueWords: GetDueWordsUseCase,
    private val getNewWords: GetNewWordsUseCase,
    private val reviewWord: ReviewWordUseCase,
    private val previewIntervals: PreviewIntervalsUseCase
) : ViewModel() {

    private val unitIdsStr: String = savedStateHandle["unitIds"] ?: ""
    private val unitIds: List<Long> = unitIdsStr.split(",").mapNotNull { it.toLongOrNull() }

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private val queue = ArrayDeque<WordDetails>()
    private var processedWordIds = mutableSetOf<Long>()
    private var againCount = 0
    private var hardCount = 0
    private var goodCount = 0
    private var easyCount = 0
    private var totalUniqueWords = 0

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            try {
                // Get due words (most overdue first) and new words
                val dueWords = getDueWords(unitIds)
                val newWords = getNewWords(unitIds)

                // Build queue: due words first, then new words
                queue.clear()
                val addedIds = mutableSetOf<Long>()
                for (word in dueWords) {
                    if (word.id !in addedIds) {
                        queue.addLast(word)
                        addedIds.add(word.id)
                    }
                }
                for (word in newWords) {
                    if (word.id !in addedIds) {
                        queue.addLast(word)
                        addedIds.add(word.id)
                    }
                }

                totalUniqueWords = queue.size

                if (queue.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            phase = StudyPhase.Finished,
                            stats = StudyStats(totalWords = 0)
                        )
                    }
                } else {
                    showNextWord()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, phase = StudyPhase.Finished) }
            }
        }
    }

    private fun showNextWord() {
        if (queue.isEmpty()) {
            _uiState.update {
                it.copy(
                    phase = StudyPhase.Finished,
                    stats = StudyStats(
                        totalWords = totalUniqueWords,
                        againCount = againCount,
                        hardCount = hardCount,
                        goodCount = goodCount,
                        easyCount = easyCount
                    )
                )
            }
            return
        }

        val word = queue.removeFirst()
        _uiState.update {
            it.copy(
                phase = StudyPhase.Studying,
                currentWord = word,
                showAnswer = false,
                previewIntervals = emptyMap(),
                progress = processedWordIds.size,
                total = totalUniqueWords
            )
        }
    }

    fun onRevealAnswer() {
        val word = _uiState.value.currentWord ?: return
        viewModelScope.launch {
            val intervals = previewIntervals(word.id)
            _uiState.update {
                it.copy(
                    showAnswer = true,
                    previewIntervals = intervals
                )
            }
        }
    }

    fun onRate(rating: Rating) {
        val word = _uiState.value.currentWord ?: return
        viewModelScope.launch {
            reviewWord(word.id, rating)

            processedWordIds.add(word.id)
            when (rating) {
                Rating.Again -> againCount++
                Rating.Hard -> hardCount++
                Rating.Good -> goodCount++
                Rating.Easy -> easyCount++
            }

            // Re-queue on Again
            if (rating == Rating.Again) {
                queue.addLast(word)
            }

            showNextWord()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
