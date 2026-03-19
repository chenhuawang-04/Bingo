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
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.repository.QuestionBankAiRepository
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val questionType: String = QuestionType.READING_COMPREHENSION.name,
    val rawQuestionType: String = QuestionType.READING_COMPREHENSION.name,
    val directions: String? = null,
    val sectionLabel: String = "",
    val sourceInfo: String = "",
    val sourceUrl: String = "",
    val passageParagraphs: List<String> = emptyList(),
    val sentenceOptions: List<String> = emptyList(),
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
                if (compressionConfig.enabled) {
                    _uiState.update { it.copy(isCompressing = true) }
                }
                val compressed = try {
                    PdfPageRenderer.renderPages(appContext, uri) { pageBytes ->
                        imageCompressionManager.compressIfNeeded(pageBytes, compressionConfig)
                    }
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
                if (compressionConfig.enabled) {
                    _uiState.update { it.copy(isCompressing = true) }
                }
                val compressed = try {
                    imageCompressionManager.readAndCompressAll(uris, compressionConfig) { uri ->
                        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
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
                val resolvedQuestionType = resolveQuestionTypeName(group.questionType)
                EditableQuestionGroup(
                    questionType = resolvedQuestionType.orEmpty(),
                    rawQuestionType = group.questionType,
                    directions = group.directions,
                    sectionLabel = group.sectionLabel ?: "",
                    sourceInfo = group.sourceInfo ?: "",
                    sourceUrl = group.sourceUrl ?: "",
                    passageParagraphs = group.passageParagraphs,
                    sentenceOptions = group.sentenceOptions,
                    questions = group.questions.map { q ->
                        val normalizedQuestionText = if (q.questionText.isBlank()) {
                            when (group.questionType) {
                                "PARAGRAPH_ORDER", "SENTENCE_INSERTION" -> "Blank ${q.questionNumber}"
                                "COMMENT_OPINION_MATCH" -> "Comment ${q.questionNumber}"
                                "SUBHEADING_MATCH" -> "Paragraph ${q.questionNumber}"
                                "INFORMATION_MATCH" -> "Item ${q.questionNumber}"
                                else -> q.questionText
                            }
                        } else {
                            q.questionText
                        }
                        EditableQuestion(
                            questionNumber = q.questionNumber,
                            questionText = normalizedQuestionText,
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
                    confidence = result.confidence,
                    error = if (editableGroups.any { group -> group.questionType.isBlank() }) {
                        "检测到未识别题型，请在预览中手动确认后再保存"
                    } else {
                        null
                    }
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

    fun updateGroupQuestionType(groupIndex: Int, questionType: String) {
        _uiState.update { state ->
            val groups = state.editableGroups.toMutableList()
            if (groupIndex in groups.indices) {
                groups[groupIndex] = groups[groupIndex].copy(
                    questionType = questionType,
                    rawQuestionType = questionType
                )
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
        val unresolvedIndex = state.editableGroups.indexOfFirst { resolveQuestionTypeName(it.questionType) == null }
        if (unresolvedIndex >= 0) {
            _uiState.update {
                it.copy(error = "请先为第 ${unresolvedIndex + 1} 个题组选择正确题型后再保存")
            }
            return
        }

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
                    val sentenceOptionsJson = if (
                        (eg.questionType == "SENTENCE_INSERTION" ||
                            eg.questionType == "COMMENT_OPINION_MATCH" ||
                            eg.questionType == "SUBHEADING_MATCH" ||
                            eg.questionType == "INFORMATION_MATCH") &&
                        eg.sentenceOptions.isNotEmpty()
                    ) {
                        buildSentenceInsertionExtraData(eg.sentenceOptions)
                    } else {
                        null
                    }
                    val passageParagraphs = if (
                        eg.questionType == "INFORMATION_MATCH" &&
                        eg.passageParagraphs.isEmpty() &&
                        eg.sentenceOptions.isNotEmpty()
                    ) {
                        eg.sentenceOptions
                    } else {
                        eg.passageParagraphs
                    }
                    QuestionGroup(
                        uid = eg.uid,
                        examPaperId = 0,
                        questionType = requireNotNull(
                            resolveQuestionTypeName(eg.questionType)
                                ?.let { resolved -> QuestionType.entries.find { it.name == resolved } }
                        ) { "题组 ${i + 1} 的题型无法识别" },
                        sectionLabel = eg.sectionLabel.ifBlank { null },
                        orderInPaper = i,
                        directions = eg.directions,
                        passageText = passageParagraphs.joinToString("\n"),
                        sourceInfo = eg.sourceInfo.ifBlank { null },
                        sourceUrl = eg.sourceUrl.ifBlank { null },
                        wordCount = eg.wordCount,
                        difficultyLevel = eg.difficultyLevel?.let {
                            com.xty.englishhelper.domain.model.DifficultyLevel.entries.find { d -> d.name == it }
                        },
                        difficultyScore = eg.difficultyScore,
                        createdAt = now, updatedAt = now,
                        paragraphs = passageParagraphs.mapIndexed { pi, text ->
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
                                difficultyScore = q.difficultyScore,
                                extraData = sentenceOptionsJson
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
                    if (group.questionType == QuestionType.WRITING) {
                        val snippet = group.items.firstOrNull()?.questionText?.take(300).orEmpty()
                        backgroundTaskManager.enqueueQuestionWritingSampleSearch(
                            groupId = group.id,
                            paperTitle = paperTitle,
                            questionSnippet = snippet
                        )
                    } else {
                        backgroundTaskManager.enqueueQuestionAnswerGeneration(
                            groupId = group.id,
                            paperTitle = paperTitle,
                            sectionLabel = group.sectionLabel.orEmpty()
                        )
                        if (group.questionType != QuestionType.TRANSLATION) {
                            backgroundTaskManager.enqueueQuestionSourceVerify(
                                groupId = group.id,
                                paperTitle = paperTitle,
                                sectionLabel = group.sectionLabel.orEmpty(),
                                sourceUrlOverride = group.sourceUrl
                            )
                        }
                    }
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

    private fun buildSentenceInsertionExtraData(options: List<String>): String {
        val arr = org.json.JSONArray()
        options.filter { it.isNotBlank() }.forEach { arr.put(it) }
        val obj = org.json.JSONObject()
        obj.put("options", arr)
        return obj.toString()
    }

    private fun resolveQuestionTypeName(raw: String): String? {
        if (raw.isBlank()) return null
        val normalized = normalizeQuestionTypeKey(raw)
        val direct = QuestionType.entries.firstOrNull { normalizeQuestionTypeKey(it.name) == normalized }
        if (direct != null) return direct.name

        val aliasMap = mapOf(
            "READING" to QuestionType.READING_COMPREHENSION.name,
            "READINGCOMPREHENSION" to QuestionType.READING_COMPREHENSION.name,
            "READINGUNDERSTANDING" to QuestionType.READING_COMPREHENSION.name,
            "CLOZETEST" to QuestionType.CLOZE.name,
            "TRANSLATE" to QuestionType.TRANSLATION.name,
            "PARAGRAPHSORTING" to QuestionType.PARAGRAPH_ORDER.name,
            "PARAGRAPHREORDER" to QuestionType.PARAGRAPH_ORDER.name,
            "PARAGRAPHORDERING" to QuestionType.PARAGRAPH_ORDER.name,
            "SENTENCEINSERT" to QuestionType.SENTENCE_INSERTION.name,
            "COMMENTMATCH" to QuestionType.COMMENT_OPINION_MATCH.name,
            "OPINIONMATCH" to QuestionType.COMMENT_OPINION_MATCH.name,
            "COMMENTOPINION" to QuestionType.COMMENT_OPINION_MATCH.name,
            "HEADINGMATCH" to QuestionType.SUBHEADING_MATCH.name,
            "SUBHEADING" to QuestionType.SUBHEADING_MATCH.name,
            "INFORMATIONALMATCH" to QuestionType.INFORMATION_MATCH.name,
            "INFORMATION" to QuestionType.INFORMATION_MATCH.name,
            "WRITINGTASK" to QuestionType.WRITING.name,
            "ESSAY" to QuestionType.WRITING.name
        )
        aliasMap[normalized]?.let { return it }

        return QuestionType.entries.firstOrNull { normalizeQuestionTypeKey(it.displayName) == normalized }?.name
    }

    private fun normalizeQuestionTypeKey(raw: String): String {
        return raw.uppercase()
            .replace(Regex("[^A-Z0-9\\u4E00-\\u9FFF]"), "")
    }
}
