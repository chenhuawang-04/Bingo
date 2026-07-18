package com.xty.englishhelper.ui.screen.dictionary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.RebuildMode
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.organize.BackgroundOrganizeManager
import com.xty.englishhelper.domain.organize.OrganizeTaskStatus
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.model.WordPoolReviewPayload
import com.xty.englishhelper.domain.model.WordPhraseOrganizePayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.WordPhraseRepository
import com.xty.englishhelper.domain.usecase.dictionary.GetDictionaryByIdUseCase
import com.xty.englishhelper.domain.usecase.pool.GetPoolCountUseCase
import com.xty.englishhelper.domain.usecase.pool.GetPoolEdgeCountUseCase
import com.xty.englishhelper.domain.usecase.pool.GetPoolVersionInfoUseCase
import com.xty.englishhelper.domain.usecase.pool.AuditQualityFirstPoolsUseCase
import com.xty.englishhelper.domain.usecase.pool.RepairQualityFirstPoolsUseCase
import com.xty.englishhelper.domain.usecase.unit.CreateUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitsWithWordCountUseCase
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordsByDictionaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDictionaryById: GetDictionaryByIdUseCase,
    private val getWordsByDictionary: GetWordsByDictionaryUseCase,
    private val deleteWord: DeleteWordUseCase,
    private val getUnitsWithWordCount: GetUnitsWithWordCountUseCase,
    private val createUnit: CreateUnitUseCase,
    private val getPoolCount: GetPoolCountUseCase,
    private val getPoolEdgeCount: GetPoolEdgeCountUseCase,
    private val getPoolVersionInfo: GetPoolVersionInfoUseCase,
    private val auditQualityFirstPoolsUseCase: AuditQualityFirstPoolsUseCase,
    private val repairQualityFirstPoolsUseCase: RepairQualityFirstPoolsUseCase,
    private val backgroundOrganizeManager: BackgroundOrganizeManager,
    private val backgroundTaskManager: BackgroundTaskManager,
    private val taskRepository: BackgroundTaskRepository,
    private val wordPhraseRepository: WordPhraseRepository
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private var poolTaskId: Long? = null
    private var reviewTaskId: Long? = null
    private var phraseTaskId: Long? = null
    private var jumpToLastUnitPage = false

    init {
        loadDictionary()
        observeWords()
        observeUnits()
        observeOrganizeTasks()
        observePoolRebuildTasks()
        observePoolReviewTasks()
        observeWordPhraseOrganizeTasks()
    }

    private fun loadDictionary() {
        viewModelScope.launch {
            val dict = getDictionaryById(dictionaryId)
            _uiState.update { it.copy(dictionary = dict) }
        }
    }

    private fun observeWords() {
        viewModelScope.launch {
            getWordsByDictionary(dictionaryId)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { words ->
                    _uiState.update {
                        val cleanedSelection = it.selectedWordIds.filter { id -> words.any { w -> w.id == id } }.toSet()
                        recomputeFiltered(
                            it.copy(
                                words = words,
                                isLoading = false,
                                selectedWordIds = cleanedSelection
                            )
                        )
                    }
                    loadPhraseInfo()
                    loadPoolInfo()
                }
        }
    }

    private fun observeUnits() {
        viewModelScope.launch {
            getUnitsWithWordCount(dictionaryId)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
                .collect { units ->
                    _uiState.update {
                        val maxUnitPage = if (units.isEmpty()) 0 else (units.size + it.unitPageSize - 1) / it.unitPageSize - 1
                        it.copy(
                            units = units,
                            unitCurrentPage = if (jumpToLastUnitPage) {
                                maxUnitPage.coerceAtLeast(0)
                            } else {
                                it.unitCurrentPage.coerceAtMost(maxUnitPage.coerceAtLeast(0))
                            }
                        )
                    }
                    jumpToLastUnitPage = false
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { recomputeFiltered(it.copy(searchQuery = query, currentPage = 0)) }
    }

    fun showDeleteConfirm(word: WordDetails) {
        _uiState.update { it.copy(deleteTarget = word) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        viewModelScope.launch {
            deleteWord(target.id, dictionaryId)
            _uiState.update {
                it.copy(
                    deleteTarget = null,
                    selectedWordIds = it.selectedWordIds - target.id
                )
            }
        }
    }

    fun showFilterDialog() {
        _uiState.update { it.copy(showFilterDialog = true) }
    }

    fun dismissFilterDialog() {
        _uiState.update { it.copy(showFilterDialog = false) }
    }

    fun updateWordFilter(filter: DictionaryWordFilter) {
        _uiState.update { recomputeFiltered(it.copy(wordFilter = filter, currentPage = 0)) }
    }

    fun resetWordFilter() {
        _uiState.update { recomputeFiltered(it.copy(wordFilter = DictionaryWordFilter(), currentPage = 0)) }
    }

    fun toggleBatchMode() {
        _uiState.update {
            if (it.isBatchMode) {
                it.copy(isBatchMode = false, selectedWordIds = emptySet())
            } else {
                it.copy(isBatchMode = true)
            }
        }
    }

    fun clearBatchSelection() {
        _uiState.update { it.copy(selectedWordIds = emptySet()) }
    }

    fun toggleWordSelection(wordId: Long) {
        _uiState.update {
            val next = it.selectedWordIds.toMutableSet()
            if (wordId in next) next.remove(wordId) else next.add(wordId)
            it.copy(selectedWordIds = next)
        }
    }

    fun selectAllFilteredWords() {
        _uiState.update { it.copy(selectedWordIds = it.filteredWords.map { w -> w.id }.toSet()) }
    }

    fun deleteSelectedWords() {
        if (_uiState.value.selectedWordIds.isEmpty()) return
        _uiState.update { it.copy(showBatchDeleteConfirm = true) }
    }

    fun dismissBatchDeleteConfirm() {
        _uiState.update { it.copy(showBatchDeleteConfirm = false) }
    }

    fun confirmDeleteSelectedWords() {
        val selected = _uiState.value.selectedWordIds.toList()
        if (selected.isEmpty()) {
            _uiState.update { it.copy(showBatchDeleteConfirm = false) }
            return
        }
        viewModelScope.launch {
            var success = 0
            val failed = mutableSetOf<Long>()
            var lastError: String? = null
            selected.forEach { wordId ->
                runCatching { deleteWord(wordId, dictionaryId) }
                    .onSuccess { success++ }
                    .onFailure {
                        failed += wordId
                        lastError = it.message
                    }
            }
            _uiState.update {
                val next = it.copy(
                    showBatchDeleteConfirm = false,
                    isBatchMode = failed.isNotEmpty(),
                    selectedWordIds = failed
                )
                recomputeFiltered(
                    if (failed.isEmpty()) {
                        next
                    } else {
                        next.copy(
                            error = "已删除 $success 个，失败 ${failed.size} 个${lastError?.let { msg -> "：$msg" } ?: ""}"
                        )
                    }
                )
            }
        }
    }

    fun reorganizeSelectedWords() {
        val state = _uiState.value
        if (state.selectedWordIds.isEmpty()) return
        val selectedWords = state.words.filter { it.id in state.selectedWordIds }
        selectedWords.forEach { word ->
            backgroundOrganizeManager.enqueue(
                wordId = word.id,
                dictionaryId = dictionaryId,
                spelling = word.spelling,
                force = true
            )
        }
    }

    fun showCreateUnitDialog() {
        _uiState.update { it.copy(showCreateUnitDialog = true, newUnitName = "") }
    }

    fun dismissCreateUnitDialog() {
        _uiState.update { it.copy(showCreateUnitDialog = false, newUnitName = "") }
    }

    fun onNewUnitNameChange(name: String) {
        _uiState.update { it.copy(newUnitName = name) }
    }

    fun confirmCreateUnit() {
        val name = _uiState.value.newUnitName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            jumpToLastUnitPage = true
            createUnit(dictionaryId, name)
            _uiState.update { it.copy(showCreateUnitDialog = false, newUnitName = "") }
        }
    }

    fun goToPage(page: Int) {
        _uiState.update {
            val maxPage = (it.totalPages - 1).coerceAtLeast(0)
            it.copy(currentPage = page.coerceIn(0, maxPage))
        }
    }

    fun nextPage() = goToPage(_uiState.value.currentPage + 1)
    fun previousPage() = goToPage(_uiState.value.currentPage - 1)

    fun goToUnitPage(page: Int) {
        _uiState.update {
            val maxPage = (it.totalUnitPages - 1).coerceAtLeast(0)
            it.copy(unitCurrentPage = page.coerceIn(0, maxPage))
        }
    }

    fun nextUnitPage() = goToUnitPage(_uiState.value.unitCurrentPage + 1)
    fun previousUnitPage() = goToUnitPage(_uiState.value.unitCurrentPage - 1)

    // ── Background organize observation ──

    private fun observeOrganizeTasks() {
        viewModelScope.launch {
            backgroundOrganizeManager.tasks.map { allTasks ->
                allTasks.filterValues { it.dictionaryId == dictionaryId }
            }.collect { tasks ->
                _uiState.update { it.copy(organizeTasks = tasks) }
            }
        }
        viewModelScope.launch {
            backgroundOrganizeManager.tasks.map { allTasks ->
                allTasks.filterValues {
                    it.dictionaryId == dictionaryId && it.status == OrganizeTaskStatus.ORGANIZING
                }.keys
            }.collect { ids ->
                _uiState.update { it.copy(organizingWordIds = ids) }
            }
        }
    }

    fun showOrganizeDetailDialog() {
        _uiState.update { it.copy(showOrganizeDetailDialog = true) }
    }

    fun dismissOrganizeDetailDialog() {
        _uiState.update { it.copy(showOrganizeDetailDialog = false) }
    }

    fun dismissOrganizeTask(wordId: Long) {
        backgroundOrganizeManager.dismissTask(wordId)
    }

    fun dismissAllOrganizeTasks() {
        backgroundOrganizeManager.dismissAll()
    }

    fun retryAllFailedOrganizeTasks() {
        backgroundOrganizeManager.retryFailedForDictionary(dictionaryId)
    }

    fun resumeAllPausedOrganizeTasks() {
        backgroundOrganizeManager.resumePausedForDictionary(dictionaryId)
    }

    fun resumeOrganizeTask(wordId: Long) {
        backgroundOrganizeManager.resumeTask(wordId)
    }

    // ── Pool management ──

    private fun loadPoolInfo() {
        viewModelScope.launch {
            try {
                val count = getPoolCount(dictionaryId)
                val edges = getPoolEdgeCount(dictionaryId)
                _uiState.update { it.copy(poolCount = count, edgeCount = edges) }

                // Version check
                val versionInfo = getPoolVersionInfo(dictionaryId)
                val outdated = versionInfo.filter { (strategy, version) ->
                    when (strategy) {
                        "BALANCED" -> version != PoolStrategy.BALANCED.algorithmVersion
                        "QUALITY_FIRST" -> version != PoolStrategy.QUALITY_FIRST.algorithmVersion
                        else -> false
                    }
                }.map { it.first }.toSet()
                _uiState.update { it.copy(outdatedStrategies = outdated) }
            } catch (_: Exception) { }
        }
    }

    private data class PoolProgressMessage(
        val word: String,
        val committedChunks: Int,
        val totalChunks: Int,
        val edges: Int
    )

    private data class ReviewProgressMessage(
        val completedBatches: Int,
        val totalBatches: Int,
        val modifiedEdges: Int
    )

    private data class PhraseProgressMessage(
        val word: String,
        val success: Int,
        val empty: Int,
        val failed: Int
    )

    private fun parseBuildProgressMessage(message: String?): PoolProgressMessage? {
        if (message.isNullOrBlank()) return null
        val parts = message.split("|")
        if (parts.size < 3) return null
        val word = parts[0]
        val committed = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val total = parts.getOrNull(2)?.toIntOrNull() ?: return null
        val edges = parts.getOrNull(3)?.toIntOrNull() ?: 0
        if (word.isBlank() || total <= 0 || committed < 0 || committed > total) return null
        return PoolProgressMessage(word, committed, total, edges)
    }

    private fun parseReviewProgressMessage(message: String?): ReviewProgressMessage? {
        if (message.isNullOrBlank()) return null
        val parts = message.split("|")
        if (parts.size < 4 || parts[0] != "review") return null
        val completed = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val total = parts.getOrNull(2)?.toIntOrNull() ?: return null
        val modified = parts.getOrNull(3)?.toIntOrNull() ?: 0
        if (total < 0 || completed < 0 || completed > total) return null
        return ReviewProgressMessage(completed, total, modified)
    }

    private fun parsePhraseProgressMessage(message: String?): PhraseProgressMessage? {
        if (message.isNullOrBlank()) return null
        val parts = message.split("|")
        if (parts.size < 6 || parts[0] != "phrase") return null
        return PhraseProgressMessage(
            word = parts.getOrNull(2).orEmpty(),
            success = parts.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            empty = parts.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            failed = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        )
    }

    private fun formatReviewMessage(task: BackgroundTask): String? {
        val reviewProgress = parseReviewProgressMessage(task.progressMessage)
        return when {
            reviewProgress != null && reviewProgress.totalBatches > 0 -> {
                "已提纯 ${reviewProgress.completedBatches}/${reviewProgress.totalBatches} 批 · 已处理 ${task.progressCurrent}/${task.progressTotal} 条边 · 已降权 ${reviewProgress.modifiedEdges} 条"
            }
            task.progressTotal > 0 -> {
                "已处理 ${task.progressCurrent}/${task.progressTotal} 条边"
            }
            else -> task.progressMessage
        }
    }

    private fun formatPhraseMessage(task: BackgroundTask): String? {
        val phraseProgress = parsePhraseProgressMessage(task.progressMessage)
        return when {
            phraseProgress != null -> buildString {
                if (phraseProgress.word.isNotBlank()) {
                    append(phraseProgress.word)
                    append(" · ")
                }
                append("有短语 ${phraseProgress.success}")
                append(" · 空 ${phraseProgress.empty}")
                append(" · 失败 ${phraseProgress.failed}")
            }
            task.progressMessage.isNullOrBlank() -> null
            else -> task.progressMessage
        }
    }

    private fun loadPhraseInfo() {
        viewModelScope.launch {
            runCatching {
                wordPhraseRepository.getStats(dictionaryId, _uiState.value.words.size)
            }.onSuccess { stats ->
                _uiState.update {
                    it.copy(
                        phraseCount = stats.phraseCount,
                        phraseTagCount = stats.tagCount,
                        phraseOrganizedWordCount = stats.organizedWordCount,
                        phraseTotalWordCount = stats.totalWordCount
                    )
                }
            }
        }
    }

    private fun observePoolRebuildTasks() {
        viewModelScope.launch {
            var lastStatus: BackgroundTaskStatus? = null
            taskRepository.observeAllTasks().collect { tasks ->
                val poolTask = tasks
                    .filter { it.type == BackgroundTaskType.WORD_POOL_REBUILD }
                    .filter { (it.payload as? WordPoolRebuildPayload)?.dictionaryId == dictionaryId }
                    .maxByOrNull { it.updatedAt }

                if (poolTask == null) {
                    poolTaskId = null
                    _uiState.update {
                        it.copy(
                            isRebuildingPools = false,
                            rebuildProgress = null,
                            rebuildError = null,
                            currentBuildWord = null,
                            isBuildPaused = false
                        )
                    }
                    lastStatus = null
                    return@collect
                }

                poolTaskId = poolTask.id
                val inProgress = poolTask.status == BackgroundTaskStatus.PENDING ||
                    poolTask.status == BackgroundTaskStatus.RUNNING ||
                    poolTask.status == BackgroundTaskStatus.PAUSED
                val progress = if (poolTask.progressTotal > 0) {
                    poolTask.progressCurrent to poolTask.progressTotal
                } else {
                    null
                }
                val error = if (poolTask.status == BackgroundTaskStatus.FAILED) {
                    poolTask.errorMessage
                } else {
                    null
                }
                val currentWord = parseBuildProgressMessage(poolTask.progressMessage)?.word ?: poolTask.progressMessage
                _uiState.update {
                    it.copy(
                        isRebuildingPools = inProgress,
                        rebuildProgress = progress,
                        rebuildError = error,
                        currentBuildWord = currentWord,
                        isBuildPaused = poolTask.status == BackgroundTaskStatus.PAUSED
                    )
                }

                if (poolTask.status != lastStatus && poolTask.status == BackgroundTaskStatus.SUCCESS) {
                    loadPoolInfo()
                }
                lastStatus = poolTask.status
            }
        }
    }

    fun requestRebuildPools(strategy: PoolStrategy) {
        if (strategy == PoolStrategy.QUALITY_FIRST) {
            val wordCount = _uiState.value.words.size
            _uiState.update { it.copy(showQfConfirmDialog = true, qfWordCount = wordCount) }
        } else {
            startRebuild(strategy)
        }
    }

    fun confirmQfRebuild() {
        _uiState.update { it.copy(showQfConfirmDialog = false) }
        startRebuild(PoolStrategy.QUALITY_FIRST, RebuildMode.INCREMENTAL)
    }

    fun confirmQfFullRebuild() {
        _uiState.update { it.copy(showQfConfirmDialog = false) }
        startRebuild(PoolStrategy.QUALITY_FIRST, RebuildMode.FULL)
    }

    fun dismissQfConfirmDialog() {
        _uiState.update { it.copy(showQfConfirmDialog = false) }
    }

    private fun startRebuild(strategy: PoolStrategy, rebuildMode: RebuildMode = RebuildMode.INCREMENTAL) {
        _uiState.update { it.copy(isRebuildingPools = true, rebuildProgress = null, rebuildError = null) }
        backgroundTaskManager.enqueueWordPoolRebuild(dictionaryId, strategy, force = rebuildMode == RebuildMode.FULL, rebuildMode = rebuildMode)
    }

    fun pauseRebuild() {
        val taskId = poolTaskId ?: return
        backgroundTaskManager.pauseTask(taskId)
    }

    fun cancelRebuild() {
        val taskId = poolTaskId ?: return
        backgroundTaskManager.cancelTask(taskId)
        _uiState.update { it.copy(isRebuildingPools = false, rebuildProgress = null) }
    }

    fun resumeRebuild() {
        val taskId = poolTaskId ?: return
        backgroundTaskManager.resumeTask(taskId)
    }

    fun clearRebuildError() {
        _uiState.update { it.copy(rebuildError = null) }
    }

    // ── 词池提纯（独立任务，手动触发） ──

    private fun observePoolReviewTasks() {
        viewModelScope.launch {
            var lastStatus: BackgroundTaskStatus? = null
            taskRepository.observeAllTasks().collect { tasks ->
                val reviewTask = tasks
                    .filter { it.type == BackgroundTaskType.WORD_POOL_REVIEW }
                    .filter { (it.payload as? WordPoolReviewPayload)?.dictionaryId == dictionaryId }
                    .maxByOrNull { it.updatedAt }

                if (reviewTask == null) {
                    reviewTaskId = null
                    _uiState.update {
                        it.copy(
                            isReviewingPools = false,
                            reviewProgress = null,
                            reviewError = null,
                            currentReviewMessage = null,
                            isReviewPaused = false
                        )
                    }
                    lastStatus = null
                    return@collect
                }

                reviewTaskId = reviewTask.id
                val inProgress = reviewTask.status == BackgroundTaskStatus.PENDING ||
                    reviewTask.status == BackgroundTaskStatus.RUNNING ||
                    reviewTask.status == BackgroundTaskStatus.PAUSED
                val progress = if (reviewTask.progressTotal > 0) {
                    reviewTask.progressCurrent to reviewTask.progressTotal
                } else {
                    null
                }
                val error = if (reviewTask.status == BackgroundTaskStatus.FAILED) reviewTask.errorMessage else null
                _uiState.update {
                    it.copy(
                        isReviewingPools = inProgress,
                        reviewProgress = progress,
                        reviewError = error,
                        currentReviewMessage = formatReviewMessage(reviewTask),
                        isReviewPaused = reviewTask.status == BackgroundTaskStatus.PAUSED
                    )
                }

                // 提纯成功后刷新边数/词池数摘要；提纯不会重建词池。
                if (reviewTask.status != lastStatus && reviewTask.status == BackgroundTaskStatus.SUCCESS) {
                    loadPoolInfo()
                }
                lastStatus = reviewTask.status
            }
        }
    }

    /** 手动发起词池提纯（仅 QUALITY_FIRST：提纯针对边）。需已有边、且当前无整理 / 提纯进行中。 */
    fun requestReviewPools() {
        if (_uiState.value.edgeCount <= 0) return
        if (_uiState.value.isRebuildingPools || _uiState.value.isReviewingPools) return
        _uiState.update { it.copy(isReviewingPools = true, reviewProgress = null, reviewError = null) }
        backgroundTaskManager.enqueueWordPoolReview(dictionaryId, PoolStrategy.QUALITY_FIRST)
    }

    fun pauseReview() {
        val taskId = reviewTaskId ?: return
        backgroundTaskManager.pauseTask(taskId)
    }

    fun resumeReview() {
        val taskId = reviewTaskId ?: return
        backgroundTaskManager.resumeTask(taskId)
    }

    fun cancelReview() {
        val taskId = reviewTaskId ?: return
        backgroundTaskManager.cancelTask(taskId)
        _uiState.update { it.copy(isReviewingPools = false, reviewProgress = null, currentReviewMessage = null) }
    }

    fun clearReviewError() {
        _uiState.update { it.copy(reviewError = null) }
    }

    fun requestPoolHealthAudit() {
        if (_uiState.value.isAuditingPoolHealth || _uiState.value.isRepairingPools) return
        _uiState.update {
            it.copy(
                showPoolHealthDialog = true,
                isAuditingPoolHealth = true,
                poolHealthReport = null,
                poolHealthError = null
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { auditQualityFirstPoolsUseCase(dictionaryId) }
            }.onSuccess { report ->
                _uiState.update {
                    it.copy(isAuditingPoolHealth = false, poolHealthReport = report)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAuditingPoolHealth = false,
                        poolHealthError = error.message ?: "词池健康检查失败"
                    )
                }
            }
        }
    }

    fun repairQualityFirstPools() {
        val report = _uiState.value.poolHealthReport ?: return
        if (!report.canRepairFromExistingEdges || _uiState.value.isRepairingPools) return
        _uiState.update { it.copy(isRepairingPools = true, poolHealthError = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repairQualityFirstPoolsUseCase(dictionaryId) }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isRepairingPools = false,
                        poolHealthReport = result.after
                    )
                }
                loadPoolInfo()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRepairingPools = false,
                        poolHealthError = error.message ?: "词池修复失败"
                    )
                }
            }
        }
    }

    fun dismissPoolHealthDialog() {
        if (_uiState.value.isAuditingPoolHealth || _uiState.value.isRepairingPools) return
        _uiState.update {
            it.copy(
                showPoolHealthDialog = false,
                isAuditingPoolHealth = false,
                poolHealthError = null
            )
        }
    }

    // ── 词组/短语整理 ──

    private fun observeWordPhraseOrganizeTasks() {
        viewModelScope.launch {
            var lastStatus: BackgroundTaskStatus? = null
            taskRepository.observeAllTasks().collect { tasks ->
                val phraseTask = tasks
                    .filter { it.type == BackgroundTaskType.WORD_PHRASE_ORGANIZE }
                    .filter { (it.payload as? WordPhraseOrganizePayload)?.dictionaryId == dictionaryId }
                    .maxByOrNull { it.updatedAt }

                if (phraseTask == null) {
                    phraseTaskId = null
                    _uiState.update {
                        it.copy(
                            isOrganizingPhrases = false,
                            phraseOrganizeProgress = null,
                            phraseOrganizeError = null,
                            currentPhraseOrganizeMessage = null,
                            isPhraseOrganizePaused = false
                        )
                    }
                    lastStatus = null
                    return@collect
                }

                phraseTaskId = phraseTask.id
                val inProgress = phraseTask.status == BackgroundTaskStatus.PENDING ||
                    phraseTask.status == BackgroundTaskStatus.RUNNING ||
                    phraseTask.status == BackgroundTaskStatus.PAUSED
                val progress = if (phraseTask.progressTotal > 0) {
                    phraseTask.progressCurrent to phraseTask.progressTotal
                } else {
                    null
                }
                val error = if (phraseTask.status == BackgroundTaskStatus.FAILED) phraseTask.errorMessage else null
                _uiState.update {
                    it.copy(
                        isOrganizingPhrases = inProgress,
                        phraseOrganizeProgress = progress,
                        phraseOrganizeError = error,
                        currentPhraseOrganizeMessage = formatPhraseMessage(phraseTask),
                        isPhraseOrganizePaused = phraseTask.status == BackgroundTaskStatus.PAUSED
                    )
                }

                if (phraseTask.status != lastStatus && phraseTask.status == BackgroundTaskStatus.SUCCESS) {
                    loadPhraseInfo()
                }
                lastStatus = phraseTask.status
            }
        }
    }

    fun requestOrganizePhrases() {
        if (_uiState.value.isOrganizingPhrases) return
        _uiState.update { it.copy(showPhraseOrganizeConfirmDialog = true) }
    }

    fun confirmOrganizePhrases() {
        val dictName = _uiState.value.dictionary?.name.orEmpty()
        _uiState.update {
            it.copy(
                showPhraseOrganizeConfirmDialog = false,
                isOrganizingPhrases = true,
                phraseOrganizeProgress = null,
                phraseOrganizeError = null,
                currentPhraseOrganizeMessage = null
            )
        }
        backgroundTaskManager.enqueueWordPhraseOrganize(
            dictionaryId = dictionaryId,
            dictionaryName = dictName,
            force = true,
            mode = "FILL_MISSING"
        )
    }

    fun dismissPhraseOrganizeConfirmDialog() {
        _uiState.update { it.copy(showPhraseOrganizeConfirmDialog = false) }
    }

    fun pausePhraseOrganize() {
        val taskId = phraseTaskId ?: return
        backgroundTaskManager.pauseTask(taskId)
    }

    fun resumePhraseOrganize() {
        val taskId = phraseTaskId ?: return
        backgroundTaskManager.resumeTask(taskId)
    }

    fun cancelPhraseOrganize() {
        val taskId = phraseTaskId ?: return
        backgroundTaskManager.cancelTask(taskId)
        _uiState.update {
            it.copy(
                isOrganizingPhrases = false,
                phraseOrganizeProgress = null,
                currentPhraseOrganizeMessage = null
            )
        }
    }

    fun clearPhraseOrganizeError() {
        _uiState.update { it.copy(phraseOrganizeError = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun recomputeFiltered(state: DictionaryUiState): DictionaryUiState {
        val normalizedQuery = state.searchQuery.trim().lowercase()
        val startsWithLower = state.wordFilter.startsWith.trim().lowercase()
        val filtered = state.words.filter { word ->
            val spelling = word.spelling.trim()
            val spellingLower = spelling.lowercase()
            if (normalizedQuery.isNotBlank()) {
                val meaningHit = word.meanings.any { meaning ->
                    (meaning.pos + " " + meaning.definition).lowercase().contains(normalizedQuery)
                }
                if (!spellingLower.contains(normalizedQuery) && !meaningHit) return@filter false
            }
            if (!matchesPresence(state.wordFilter.phonetic, word.phonetic.isNotBlank())) return@filter false
            if (!matchesPresence(state.wordFilter.meanings, word.meanings.isNotEmpty())) return@filter false
            if (!matchesPresence(state.wordFilter.rootExplanation, word.rootExplanation.isNotBlank())) return@filter false
            if (!matchesPresence(state.wordFilter.decomposition, word.decomposition.isNotEmpty())) return@filter false
            if (!matchesPresence(state.wordFilter.synonyms, word.synonyms.isNotEmpty())) return@filter false
            if (!matchesPresence(state.wordFilter.similarWords, word.similarWords.isNotEmpty())) return@filter false
            if (!matchesPresence(state.wordFilter.cognates, word.cognates.isNotEmpty())) return@filter false
            if (!matchesPresence(state.wordFilter.inflections, word.inflections.isNotEmpty())) return@filter false
            val len = spelling.length
            if (state.wordFilter.minLength != null && len < state.wordFilter.minLength) return@filter false
            if (state.wordFilter.maxLength != null && len > state.wordFilter.maxLength) return@filter false
            if (startsWithLower.isNotBlank() && !spellingLower.startsWith(startsWithLower)) return@filter false
            true
        }
        val maxPage = if (filtered.isEmpty()) 0 else (filtered.size + state.pageSize - 1) / state.pageSize - 1
        return state.copy(
            filteredWords = filtered,
            currentPage = state.currentPage.coerceAtMost(maxPage.coerceAtLeast(0))
        )
    }

    private fun matchesPresence(filter: EntryPresenceFilter, hasValue: Boolean): Boolean {
        return when (filter) {
            EntryPresenceFilter.ANY -> true
            EntryPresenceFilter.PRESENT -> hasValue
            EntryPresenceFilter.MISSING -> !hasValue
        }
    }
}
