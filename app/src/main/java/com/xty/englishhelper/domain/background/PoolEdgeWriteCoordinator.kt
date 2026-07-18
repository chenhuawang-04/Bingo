package com.xty.englishhelper.domain.background

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PoolEdgeWriteCoordinator @Inject constructor() {
    private val mutexes = ConcurrentHashMap<Long, Mutex>()

    suspend fun <T> withLock(dictionaryId: Long, block: suspend () -> T): T {
        val mutex = mutexes.computeIfAbsent(dictionaryId) { Mutex() }
        return mutex.withLock { block() }
    }
}
