package com.xty.englishhelper.domain.background

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppResourceCoordinator {
    private val memoryHeavyMutex = Mutex()
    private val _memoryHeavyOwner = MutableStateFlow<String?>(null)
    val memoryHeavyOwner: StateFlow<String?> = _memoryHeavyOwner.asStateFlow()

    suspend fun <T> withMemoryHeavyOperation(owner: String, block: suspend () -> T): T {
        return memoryHeavyMutex.withLock {
            _memoryHeavyOwner.value = owner
            try {
                block()
            } finally {
                _memoryHeavyOwner.value = null
            }
        }
    }
}
