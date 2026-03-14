package com.xty.englishhelper.ui.screen.questionbank

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.image.ImageCompressionManager
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.SELECT,
    val selectedImageUris: List<Uri> = emptyList(),
    val isScanning: Boolean = false,
    val isCompressing: Boolean = false,
    val scanResult: ScanResult? = null,
    // Editable preview
    val paperTitle: String = "",
    val editableGroups: List<EditableQuestionGroup> = emptyList(),
    val confidence: Float = 0f,
    // Save
    val isSaving: Boolean = false,
    val error: String? = null
)

enum class ScanPhase { SELECT, SCANNING, PREVIEW }

data class EditableQuestionGroup(
    val uid: String = UUID.randomUUID().toString(),
    val sectionLabel: String = "",
    val sourceInfo: String = "",
    val sourceUrl: String = "",
    val passageParagraphs: List<String> = emptyList(),
    val questions: List<EditableQuestion> = emptyList(),
    val wordCount: Int = 0,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
)

data class EditableQuestion(
    val questionNumber: Int = 0,
    val questionText: String = "",
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val wordCount: Int = 0,
    val difficultyLevel: String? = null,
    val difficultyScore: Float? = null
)

@HiltViewModel
class QuestionBankScanViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val aiRepository: QuestionBankAiRepository,
    private val repository: QuestionBankRepository,
    private val settingsDataStore: SettingsDataStore,
    private val imageCompressionManager: ImageCompressionManager,
    private val backgroundTaskManager: BackgroundTaskManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _savedPaperId = MutableSharedFlow<Long>(replay = 0)
    val savedPaperId: SharedFlow<Long> = _savedPaperId.asSharedFlow()

    fun onImagesSelected(uris: List<Uri>) {
        _uiState.update { it.copy(selectedImageUris = uris) }
        if (uris.isNotEmpty()) startScan(uris)
    }

    fun onPdfSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = ScanPhase.SCANNING, isScanning = true, isCompressing = false, error = null) }
            try {
                val compressionConfig = settingsDataStore.getImageCompressionConfig()
                val imageBytes = PdfPageRenderer.renderPages(appContext, uri)
                if (compressionConfig.enabled) {
                    _uiState.update { it.copy(isCompressing = true) }
                }
                val compressed = try {
                    imageCompressionManager.compressAll(imageBytes, compressionConfig)
                } finally {
                    if (compressionConfig.enabled) {
                        _uiState.update { it.copy(isCompressing = false) }
                    }
                }
                doScan(compressed)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(phase = ScanPhase.SELECT, isScanning = false, error = "PDF处理失败：${e.message}")
                }
            }
        }
    }

    private fun startScan(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = ScanPhase.SCANNING, isScanning = true, isCompressing = false, error = null) }
            try {
                val compressionConfig = settingsDataStore.getImageCompressionConfig()
                val imageBytes = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                }
                if (compressionConfig.enabled) {
                    _uiState.update { it.copy(isCompressing = true) }
                }
                val compressed = try {
                    imageCompressionManager.compressAll(imageBytes, compressionConfig)
                } finally {
                    if (compressionConfig.enabled) {
                        _uiState.update { it.copy(isCompressing = false) }
                    }
                }
                doScan(compressed)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(phase = ScanPhase.SELECT, isScanning = false, error = "读取图片失败：${e.message}")
                }
            }
        }
    }

    private suspend fun doScan(imageBytes: List<ByteArray>) {
        try {
            val config = settingsDataStore.getAiConfig(AiSettingsScope.OCR)
            if (config.apiKey.isBlank()) {
                _uiState.update {
                    it.copy(phase = ScanPhase.SELECT, isScanning = false, error = "请先在设置中配置 OCR 模型")
                }
                return
            }

            val result = aiRepository.scanQuestions(
                imageBytes, config.apiKey, config.model, config.baseUrl, config.provider
            )

            val editableGroups = result.questionGroups.map { group ->
                EditableQuestionGroup(
                    sectionLabel = group.sectionLabel ?: "",
                    sourceInfo = group.sourceInfo ?: "",
                    sourceUrl = group.sourceUrl ?: "",
                    passageParagraphs = group.passageParagraphs,
                    questions = group.questions.map { q ->
                        EditableQuestion(
                            questionNumber = q.questionNumber,
                            questionText = q.questionText,
                            optionA = q.optionA,
                            optionB = q.optionB,
                            optionC = q.optionC,
                            optionD = q.optionD,
                            wordCount = q.wordCount,
                            difficultyLevel = q.difficultyLevel,
                            difficultyScore = q.difficultyScore
                        )
                    },
                    wordCount = group.wordCount,
                    difficultyLevel = group.difficultyLevel,
                    difficultyScore = group.difficultyScore
                )
            }

            _uiState.update {
                it.copy(
                    phase = ScanPhase.PREVIEW,
                    isScanning = false,
                    scanResult = result,
                    paperTitle = result.examPaperTitle.ifBlank { "未命名试卷" },
                    editableGroups = editableGroups,
                    confidence = result.confidence
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(phase = ScanPhase.SELECT, isScanning = false, error = "扫描失败：${e.message}")
            }
        }
    }

    fun updatePaperTitle(title: String) {
        _uiState.update { it.copy(paperTitle = title) }
    }

    fun updateGroupSectionLabel(groupIndex: Int, label: String) {
        _uiState.update { state ->
            val groups = state.editableGroups.toMutableList()
            if (groupIndex in groups.indices) {
                groups[groupIndex] = groups[groupIndex].copy(sectionLabel = label)
            }
            state.copy(editableGroups = groups)
        }
    }

    fun updateGroupSourceUrl(groupIndex: Int, url: String) {
        _uiState.update { state ->
            val groups = state.editableGroups.toMutableList()
            if (groupIndex in groups.indices) {
                groups[groupIndex] = groups[groupIndex].copy(sourceUrl = url)
            }
            state.copy(editableGroups = groups)
        }
    }

    fun updateQuestionText(groupIndex: Int, questionIndex: Int, text: String) {
        _uiState.update { state ->
            val groups = state.editableGroups.toMutableList()
            if (groupIndex in groups.indices) {
                val questions = groups[groupIndex].questions.toMutableList()
                if (questionIndex in questions.indices) {
                    questions[questionIndex] = questions[questionIndex].copy(questionText = text)
                    groups[groupIndex] = groups[groupIndex].copy(questions = questions)
                }
            }
            state.copy(editableGroups = groups)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val now = System.currentTimeMillis()
                val paper = ExamPaper(
                    uid = UUID.randomUUID().toString(),
                    title = state.paperTitle.ifBlank { "未命名试卷" },
                    createdAt = now, updatedAt = now
                )

                val groups = state.editableGroups.mapIndexed { i, eg ->
                    QuestionGroup(
                        uid = eg.uid,
                        examPaperId = 0,
                        questionType = com.xty.englishhelper.domain.model.QuestionType.READING_COMPREHENSION,
                        sectionLabel = eg.sectionLabel.ifBlank { null },
                        orderInPaper = i,
                        passageText = eg.passageParagraphs.joinToString("\n"),
                        sourceInfo = eg.sourceInfo.ifBlank { null },
                        sourceUrl = eg.sourceUrl.ifBlank { null },
                        wordCount = eg.wordCount,
                        difficultyLevel = eg.difficultyLevel?.let {
                            com.xty.englishhelper.domain.model.DifficultyLevel.entries.find { d -> d.name == it }
                        },
                        difficultyScore = eg.difficultyScore,
                        createdAt = now, updatedAt = now,
                        paragraphs = eg.passageParagraphs.mapIndexed { pi, text ->
                            ArticleParagraph(paragraphIndex = pi, text = text)
                        },
                        items = eg.questions.mapIndexed { qi, q ->
                            QuestionItem(
                                questionGroupId = 0,
                                questionNumber = q.questionNumber,
                                questionText = q.questionText,
                                optionA = q.optionA.ifBlank { null },
                                optionB = q.optionB.ifBlank { null },
                                optionC = q.optionC.ifBlank { null },
                                optionD = q.optionD.ifBlank { null },
                                orderInGroup = qi,
                                wordCount = q.wordCount,
                                difficultyLevel = q.difficultyLevel?.let {
                                    com.xty.englishhelper.domain.model.DifficultyLevel.entries.find { d -> d.name == it }
                                },
                                difficultyScore = q.difficultyScore
                            )
                        }
                    )
                }

                val paperId = repository.saveScannedPaper(paper, groups)

                // Trigger background tasks for each group
                launchBackgroundTasks(paperId, paper.title)

                _uiState.update { it.copy(isSaving = false) }
                _savedPaperId.emit(paperId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "保存失败：${e.message}") }
            }
        }
    }

    private fun launchBackgroundTasks(paperId: Long, paperTitle: String) {
        viewModelScope.launch {
            try {
                val groupList = repository.getGroupsByPaper(paperId).first()
                for (group in groupList) {
                    backgroundTaskManager.enqueueQuestionAnswerGeneration(
                        groupId = group.id,
                        paperTitle = paperTitle,
                        sectionLabel = group.sectionLabel.orEmpty()
                    )
                    backgroundTaskManager.enqueueQuestionSourceVerify(
                        groupId = group.id,
                        paperTitle = paperTitle,
                        sectionLabel = group.sectionLabel.orEmpty(),
                        sourceUrlOverride = group.sourceUrl
                    )
                }
            } catch (_: Exception) { }
        }
    }

    fun resetToSelect() {
        _uiState.update { ScanUiState() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
