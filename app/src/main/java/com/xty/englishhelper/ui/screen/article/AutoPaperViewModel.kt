package com.xty.englishhelper.ui.screen.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.ArticleAdvancedScoringTargets
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.ExamPaperProfile
import com.xty.englishhelper.domain.model.ExamPaperSlotSelection
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AutoPaperUiState(
    val profile: ExamPaperProfile = ExamPaperProfile.ENGLISH_ONE,
    val specialType: QuestionType = ArticleAdvancedScoringTargets.selectableSpecialTypes.first(),
    val paper: ExamPaper? = null,
    val slots: List<ExamPaperSlotSelection> = emptyList(),
    val isStarting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AutoPaperViewModel @Inject constructor(
    private val repository: QuestionBankRepository,
    private val taskManager: BackgroundTaskManager,
    private val settings: SettingsDataStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(AutoPaperUiState())
    val uiState: StateFlow<AutoPaperUiState> = _uiState.asStateFlow()
    private var slotJob: Job? = null
    private val dayKey: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    init { refreshLatest() }

    fun setProfile(profile: ExamPaperProfile) = _uiState.update { it.copy(profile = profile) }
    fun setSpecialType(type: QuestionType) = _uiState.update { it.copy(specialType = type) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun refreshLatest() {
        viewModelScope.launch {
            repository.getLatestAutoPaperByDay(dayKey)?.let(::observePaper)
        }
    }

    fun start() {
        if (_uiState.value.isStarting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStarting = true, error = null) }
            try {
                val state = _uiState.value
                val paper = repository.createAutoExamPaper(dayKey, state.profile, state.specialType)
                observePaper(paper)
                val thresholds = settings.getAdvancedScoringSettings()
                taskManager.enqueueAutoPaperSelection(
                    paper = paper,
                    minimumBasicScore = thresholds.minimumBasicScore,
                    minimumWordCount = thresholds.minimumWordCount,
                    maximumWordCount = thresholds.maximumWordCount
                )
            } catch (error: Exception) {
                _uiState.update { it.copy(error = error.message ?: "自动组卷启动失败") }
            } finally {
                _uiState.update { it.copy(isStarting = false) }
            }
        }
    }

    fun generateNow() {
        val paper = _uiState.value.paper ?: return
        viewModelScope.launch {
            runCatching {
                taskManager.enqueueExamPaperGeneration(
                    paper.id, paper.title, allowIncomplete = true, force = true
                )
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "出题任务启动失败") }
            }
        }
    }

    private fun observePaper(paper: ExamPaper) {
        _uiState.update {
            it.copy(paper = paper, profile = paper.profile, specialType = paper.specialQuestionType ?: it.specialType)
        }
        slotJob?.cancel()
        slotJob = viewModelScope.launch {
            repository.getExamPaperSlotSelections(paper.id).collect { slots ->
                _uiState.update { state -> state.copy(slots = slots) }
            }
        }
    }
}
