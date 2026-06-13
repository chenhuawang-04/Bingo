package com.xty.englishhelper.data.sync

interface ConflictResolver<T> {
    fun resolve(local: T, cloud: T): MergeAction
}

sealed class MergeAction {
    object KeepLocal : MergeAction()
    object KeepCloud : MergeAction()
    data class Merge(val summary: String) : MergeAction()
    data class Conflict(val reason: String) : MergeAction()
}

data class MergeResult(
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val conflicts: List<String> = emptyList()
)
