package com.xty.englishhelper.data.debug

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiDebugManager @Inject constructor() {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _events = MutableSharedFlow<AiDebugEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AiDebugEvent> = _events.asSharedFlow()

    fun setEnabled(value: Boolean) {
        _enabled.value = value
    }

    fun isEnabled(): Boolean = _enabled.value

    fun emit(event: AiDebugEvent) {
        if (!_enabled.value) return
        _events.tryEmit(event)
    }
}
