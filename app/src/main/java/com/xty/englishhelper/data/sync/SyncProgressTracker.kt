package com.xty.englishhelper.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncPhase { DOWNLOAD, MERGE, UPLOAD }

data class SyncProgress(
    val phase: SyncPhase = SyncPhase.DOWNLOAD,
    val step: String = "",
    val current: Int = 0,
    val total: Int = 0
)

class SyncProgressTracker {
    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    fun updatePhase(phase: SyncPhase, total: Int = 0) {
        _progress.value = SyncProgress(phase = phase, total = total)
    }

    fun updateStep(step: String, current: Int = _progress.value.current) {
        _progress.value = _progress.value.copy(step = step, current = current)
    }

    fun increment() {
        val p = _progress.value
        _progress.value = p.copy(current = p.current + 1)
    }

    fun reset() {
        _progress.value = SyncProgress()
    }
}
