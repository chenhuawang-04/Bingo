package com.xty.englishhelper.ui.screen.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.usecase.study.GetDueWordsUseCase
import com.xty.englishhelper.domain.usecase.study.GetNewWordsUseCase
import com.xty.englishhelper.domain.usecase.study.GetStudyStateUseCase
import com.xty.englishhelper.domain.usecase.study.InitStudyStateUseCase
import com.xty.englishhelper.domain.usecase.study.MarkKnownUseCase
import com.xty.englishhelper.domain.usecase.study.MarkUnknownUseCase
import com.xty.englishhelper.domain.repository.UnitRepository
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
    private val getStudyState: GetStudyStateUseCase,
    private val initStudyState: InitStudyStateUseCase,
    private val markKnown: MarkKnownUseCase,
    private val markUnknown: MarkUnknownUseCase,
    private val unitRepository: UnitRepository
) : ViewModel() {

    private val unitIdsStr: String = savedStateHandle["unitIds"] ?: ""
    private val unitIds: List<Long> = unitIdsStr.split(",").mapNotNull { it.toLongOrNull() }

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private val queue = ArrayDeque<WordDetails>()
    private val wordRepeatCounts = mutableMapOf<Long, Int>() // unitId -> repeatCount
    private var processedWordIds = mutableSetOf<Long>()
    private var knownCount = 0
    private var unknownCount = 0
    private var masteredCount = 0
    private var totalUniqueWords = 0

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            try {
                // Load repeat counts for each unit
                for (unitId in unitIds) {
                    val unit = unitRepository.getUnitById(unitId)
                    if (unit != null) {
                        wordRepeatCounts[unitId] = unit.defaultRepeatCount
                    }
                }
                val defaultRepeatCount = wordRepeatCounts.values.maxOrNull() ?: 2

                // Get due words (most overdue first) and new words
                val dueWords = getDueWords(unitIds)
                val newWords = getNewWords(unitIds)

                // Initialize study state for new words
                for (word in newWords) {
                    initStudyState(word.id, defaultRepeatCount)
                }

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
                        knownCount = knownCount,
                        unknownCount = unknownCount,
                        masteredCount = masteredCount
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
                progress = processedWordIds.size,
                total = totalUniqueWords
            )
        }
    }

    fun onKnown() {
        val word = _uiState.value.currentWord ?: return
        viewModelScope.launch {
            val state = getStudyState(word.id) ?: return@launch
            val updated = markKnown(state)

            processedWordIds.add(word.id)
            knownCount++
            if (updated.remainingReviews <= 0) {
                masteredCount++
            }

            showNextWord()
        }
    }

    fun onUnknown() {
        _uiState.update { it.copy(showAnswer = true) }
    }

    fun onNext() {
        val word = _uiState.value.currentWord ?: return
        viewModelScope.launch {
            val state = getStudyState(word.id) ?: return@launch
            markUnknown(state)

            processedWordIds.add(word.id)
            unknownCount++

            // Re-queue: add to end of queue
            queue.addLast(word)

            showNextWord()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
