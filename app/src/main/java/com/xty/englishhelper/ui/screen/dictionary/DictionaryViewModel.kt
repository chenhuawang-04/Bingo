package com.xty.englishhelper.ui.screen.dictionary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.organize.BackgroundOrganizeManager
import com.xty.englishhelper.domain.organize.OrganizeTaskStatus
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.usecase.dictionary.GetDictionaryByIdUseCase
import com.xty.englishhelper.domain.usecase.pool.GetPoolCountUseCase
import com.xty.englishhelper.domain.usecase.pool.GetPoolVersionInfoUseCase
import com.xty.englishhelper.domain.usecase.unit.CreateUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitsWithWordCountUseCase
import com.xty.englishhelper.domain.usecase.word.DeleteWordUseCase
import com.xty.englishhelper.domain.usecase.word.GetWordsByDictionaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val getPoolVersionInfo: GetPoolVersionInfoUseCase,
    private val backgroundOrganizeManager: BackgroundOrganizeManager,
    private val backgroundTaskManager: BackgroundTaskManager,
    private val taskRepository: BackgroundTaskRepository
) : ViewModel() {

    private val dictionaryId: Long = savedStateHandle["dictionaryId"] ?: 0L

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private var poolTaskId: Long? = null
    private var jumpToLastUnitPage = false

    init {
        loadDictionary()
        observeWords()
        observeUnits()
        loadPoolInfo()
        observeOrganizeTasks()
        observePoolRebuildTasks()
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
                _uiState.update { it.copy(poolCount = count) }

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
                        it.copy(isRebuildingPools = false, rebuildProgress = null, rebuildError = null)
                    }
                    lastStatus = null
                    return@collect
                }

                poolTaskId = poolTask.id
                val inProgress = poolTask.status == BackgroundTaskStatus.PENDING ||
                    poolTask.status == BackgroundTaskStatus.RUNNING
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
                _uiState.update {
                    it.copy(
                        isRebuildingPools = inProgress,
                        rebuildProgress = progress,
                        rebuildError = error
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
        startRebuild(PoolStrategy.QUALITY_FIRST)
    }

    fun dismissQfConfirmDialog() {
        _uiState.update { it.copy(showQfConfirmDialog = false) }
    }

    private fun startRebuild(strategy: PoolStrategy) {
        _uiState.update { it.copy(isRebuildingPools = true, rebuildProgress = null, rebuildError = null) }
        backgroundTaskManager.enqueueWordPoolRebuild(dictionaryId, strategy, force = true)
    }

    fun cancelRebuild() {
        val taskId = poolTaskId ?: return
        backgroundTaskManager.cancelTask(taskId)
        _uiState.update { it.copy(isRebuildingPools = false, rebuildProgress = null) }
    }

    fun clearRebuildError() {
        _uiState.update { it.copy(rebuildError = null) }
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
