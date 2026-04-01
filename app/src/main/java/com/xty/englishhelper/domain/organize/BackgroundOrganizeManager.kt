package com.xty.englishhelper.domain.organize

import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.WordOrganizePayload
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class OrganizeTaskStatus { ORGANIZING, SUCCESS, FAILED, PAUSED }

data class OrganizeTask(
    val wordId: Long,
    val dictionaryId: Long,
    val spelling: String,
    val status: OrganizeTaskStatus,
    val error: String? = null
)

@Singleton
class BackgroundOrganizeManager @Inject constructor(
    private val taskRepository: BackgroundTaskRepository,
    private val backgroundTaskManager: BackgroundTaskManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow<Map<Long, OrganizeTask>>(emptyMap())
    val tasks: StateFlow<Map<Long, OrganizeTask>> = _tasks.asStateFlow()

    private val _organizingWordIds = MutableStateFlow<Set<Long>>(emptySet())
    val organizingWordIds: StateFlow<Set<Long>> = _organizingWordIds.asStateFlow()

    private val taskIdByWordId = mutableMapOf<Long, Long>()

    init {
        scope.launch {
            taskRepository.observeAllTasks().collect { allTasks ->
                val map = mutableMapOf<Long, OrganizeTask>()
                val idMap = mutableMapOf<Long, Long>()
                allTasks.asSequence()
                    .filter { it.type == BackgroundTaskType.WORD_ORGANIZE }
                    .forEach { task ->
                        val payload = task.payload as? WordOrganizePayload ?: return@forEach
                        val status = when (task.status) {
                            BackgroundTaskStatus.PENDING, BackgroundTaskStatus.RUNNING -> OrganizeTaskStatus.ORGANIZING
                            BackgroundTaskStatus.SUCCESS -> OrganizeTaskStatus.SUCCESS
                            BackgroundTaskStatus.FAILED -> OrganizeTaskStatus.FAILED
                            BackgroundTaskStatus.PAUSED -> OrganizeTaskStatus.PAUSED
                            BackgroundTaskStatus.CANCELED -> null
                        }
                        if (status != null) {
                            map[payload.wordId] = OrganizeTask(
                                wordId = payload.wordId,
                                dictionaryId = payload.dictionaryId,
                                spelling = payload.spelling,
                                status = status,
                                error = task.errorMessage
                            )
                            idMap[payload.wordId] = task.id
                        }
                    }

                _tasks.value = map
                taskIdByWordId.clear()
                taskIdByWordId.putAll(idMap)
                _organizingWordIds.value = map.filterValues { it.status == OrganizeTaskStatus.ORGANIZING }.keys
            }
        }
    }

    fun enqueue(
        wordId: Long,
        dictionaryId: Long,
        spelling: String,
        force: Boolean = false,
        referenceHints: List<String> = emptyList()
    ) {
        backgroundTaskManager.enqueueWordOrganize(
            wordId = wordId,
            dictionaryId = dictionaryId,
            spelling = spelling,
            force = force,
            referenceHints = referenceHints
        )
    }

    fun dismissTask(wordId: Long) {
        val taskId = taskIdByWordId[wordId] ?: return
        backgroundTaskManager.deleteTask(taskId)
    }

    fun dismissAll() {
        val ids = _tasks.value.values
            .filter { it.status != OrganizeTaskStatus.ORGANIZING }
            .mapNotNull { taskIdByWordId[it.wordId] }
        ids.forEach { backgroundTaskManager.deleteTask(it) }
    }

    fun retryAllFailed() {
        val ids = _tasks.value.values
            .filter { it.status == OrganizeTaskStatus.FAILED }
            .mapNotNull { taskIdByWordId[it.wordId] }
        ids.forEach { backgroundTaskManager.restartTask(it) }
    }

    fun resumePausedForDictionary(dictionaryId: Long) {
        val ids = _tasks.value.values
            .filter { it.status == OrganizeTaskStatus.PAUSED && it.dictionaryId == dictionaryId }
            .mapNotNull { taskIdByWordId[it.wordId] }
        ids.forEach { backgroundTaskManager.resumeTask(it) }
    }

    fun resumeTask(wordId: Long) {
        val taskId = taskIdByWordId[wordId] ?: return
        backgroundTaskManager.resumeTask(taskId)
    }

    fun retryFailedForDictionary(dictionaryId: Long) {
        val ids = _tasks.value.values
            .filter { it.status == OrganizeTaskStatus.FAILED && it.dictionaryId == dictionaryId }
            .mapNotNull { taskIdByWordId[it.wordId] }
        ids.forEach { backgroundTaskManager.restartTask(it) }
    }
}
