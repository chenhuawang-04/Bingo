package com.xty.englishhelper.domain.background

import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskType

internal data class TaskResourceDemand(
    val memoryHeavy: Int = 0,
    val cpuHeavy: Int = 0,
    val network: Int = 0,
    val databaseWriter: Int = 0,
    val exclusive: Boolean = false
)

internal data class TaskResourceBudget(
    val memoryHeavy: Int = 1,
    val cpuHeavy: Int = 1,
    val network: Int = 2,
    val databaseWriter: Int = 1
)

internal fun BackgroundTask.resourceDemand(): TaskResourceDemand = when (type) {
    BackgroundTaskType.CLOUD_SYNC -> TaskResourceDemand(
        memoryHeavy = 1,
        cpuHeavy = 1,
        network = 1,
        databaseWriter = 1,
        exclusive = true
    )

    BackgroundTaskType.WORD_POOL_REBUILD,
    BackgroundTaskType.WORD_POOL_REVIEW -> TaskResourceDemand(
        memoryHeavy = 1,
        cpuHeavy = 1,
        network = 1,
        databaseWriter = 1
    )

    BackgroundTaskType.WORD_PHRASE_ORGANIZE,
    BackgroundTaskType.ONLINE_ARTICLE_SCAN_SCORE -> TaskResourceDemand(
        memoryHeavy = 1,
        network = 1,
        databaseWriter = 1
    )

    BackgroundTaskType.WORD_ORGANIZE,
    BackgroundTaskType.WORD_NOTE_ORGANIZE,
    BackgroundTaskType.QUESTION_GENERATE,
    BackgroundTaskType.QUESTION_ANSWER_GENERATE,
    BackgroundTaskType.QUESTION_SOURCE_VERIFY,
    BackgroundTaskType.QUESTION_WRITING_SAMPLE_SEARCH -> TaskResourceDemand(
        network = 1,
        databaseWriter = 1
    )

    BackgroundTaskType.UNKNOWN -> TaskResourceDemand(exclusive = true)
}

internal fun fitsResourceBudget(
    runningTasks: Collection<BackgroundTask>,
    selectedTasks: Collection<BackgroundTask>,
    candidate: BackgroundTask,
    budget: TaskResourceBudget = TaskResourceBudget()
): Boolean {
    val active = runningTasks + selectedTasks
    val candidateDemand = candidate.resourceDemand()
    if (candidateDemand.exclusive) return active.isEmpty()
    if (active.any { it.resourceDemand().exclusive }) return false

    val activeDemands = active.map { it.resourceDemand() }
    return activeDemands.sumOf { it.memoryHeavy } + candidateDemand.memoryHeavy <= budget.memoryHeavy &&
        activeDemands.sumOf { it.cpuHeavy } + candidateDemand.cpuHeavy <= budget.cpuHeavy &&
        activeDemands.sumOf { it.network } + candidateDemand.network <= budget.network &&
        activeDemands.sumOf { it.databaseWriter } + candidateDemand.databaseWriter <= budget.databaseWriter
}
