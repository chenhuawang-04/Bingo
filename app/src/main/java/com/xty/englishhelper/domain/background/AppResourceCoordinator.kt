package com.xty.englishhelper.domain.background

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ForegroundResourceDemand(
    val memoryHeavy: Int = 0,
    val cpuHeavy: Int = 0,
    val network: Int = 0,
    val databaseWriter: Int = 0,
    val exclusive: Boolean = false
)

data class AppResourceUsage(
    val memoryHeavy: Int = 0,
    val cpuHeavy: Int = 0,
    val network: Int = 0,
    val databaseWriter: Int = 0,
    val exclusiveOwners: Set<String> = emptySet()
)

/**
 * Process-wide resource registry shared by foreground work and the persistent task scheduler.
 * Existing callers keep the memory-heavy mutual exclusion guarantee, while all registered work
 * is also visible to background dispatch so it can yield before oversubscribing the process.
 */
object AppResourceCoordinator {
    private data class Lease(
        val owner: String,
        val demand: ForegroundResourceDemand
    )

    private val memoryHeavyMutex = Mutex()
    private val leaseIds = AtomicLong(0)
    private val leases = MutableStateFlow<Map<Long, Lease>>(emptyMap())
    private val _usage = MutableStateFlow(AppResourceUsage())
    val usage: StateFlow<AppResourceUsage> = _usage.asStateFlow()

    private val _memoryHeavyOwner = MutableStateFlow<String?>(null)
    val memoryHeavyOwner: StateFlow<String?> = _memoryHeavyOwner.asStateFlow()

    suspend fun <T> withMemoryHeavyOperation(owner: String, block: suspend () -> T): T {
        return memoryHeavyMutex.withLock {
            _memoryHeavyOwner.value = owner
            try {
                withResourceUsage(owner, ForegroundResourceDemand(memoryHeavy = 1), block)
            } finally {
                _memoryHeavyOwner.value = null
            }
        }
    }

    suspend fun <T> withResourceUsage(
        owner: String,
        demand: ForegroundResourceDemand,
        block: suspend () -> T
    ): T {
        require(demand.memoryHeavy >= 0 && demand.cpuHeavy >= 0)
        require(demand.network >= 0 && demand.databaseWriter >= 0)
        val leaseId = leaseIds.incrementAndGet()
        leases.update { current -> current + (leaseId to Lease(owner, demand)) }
        refreshUsage()
        return try {
            block()
        } finally {
            leases.update { current -> current - leaseId }
            refreshUsage()
        }
    }

    private fun refreshUsage() {
        val active = leases.value.values
        _usage.value = AppResourceUsage(
            memoryHeavy = active.sumOf { it.demand.memoryHeavy },
            cpuHeavy = active.sumOf { it.demand.cpuHeavy },
            network = active.sumOf { it.demand.network },
            databaseWriter = active.sumOf { it.demand.databaseWriter },
            exclusiveOwners = active.filter { it.demand.exclusive }.mapTo(linkedSetOf()) { it.owner }
        )
    }
}
