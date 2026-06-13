package com.xty.englishhelper.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncNotifier @Inject constructor() {
    private val _events = MutableSharedFlow<SyncEvent>()
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    suspend fun notifyComplete(added: Int, updated: Int) {
        _events.emit(SyncEvent.Complete(added, updated))
    }

    suspend fun notifyError(message: String) {
        _events.emit(SyncEvent.Error(message))
    }
}

sealed class SyncEvent {
    data class Complete(val added: Int, val updated: Int) : SyncEvent()
    data class Error(val message: String) : SyncEvent()
}
