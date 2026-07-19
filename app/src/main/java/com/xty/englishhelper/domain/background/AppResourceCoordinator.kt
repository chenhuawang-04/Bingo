package com.xty.englishhelper.domain.background

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore

data class ForegroundResourceDemand(
    val memoryHeavy: Int = 0,
    val cpuHeavy: Int = 0,
    val network: Int = 0,
    val databaseWriter: Int = 0,
    val audio: Int = 0,
    val exclusive: Boolean = false
)

data class AppResourceUsage(
    val memoryHeavy: Int = 0,
    val cpuHeavy: Int = 0,
    val network: Int = 0,
    val databaseWriter: Int = 0,
    val audio: Int = 0,
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

    private val memoryGate = Semaphore(1)
    private val cpuGate = Semaphore(1)
    private val databaseWriterGate = Semaphore(1)
    private val leaseIds = AtomicLong(0)
    private val leases = MutableStateFlow<Map<Long, Lease>>(emptyMap())
    private val _usage = MutableStateFlow(AppResourceUsage())
    val usage: StateFlow<AppResourceUsage> = _usage.asStateFlow()

    private val _memoryHeavyOwner = MutableStateFlow<String?>(null)
    val memoryHeavyOwner: StateFlow<String?> = _memoryHeavyOwner.asStateFlow()

    suspend fun <T> withMemoryHeavyOperation(owner: String, block: suspend () -> T): T {
        return withResourceUsage(
            owner = owner,
            demand = ForegroundResourceDemand(memoryHeavy = 1, cpuHeavy = 1)
        ) {
            _memoryHeavyOwner.value = owner
            try {
                block()
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
        require(demand.network >= 0 && demand.databaseWriter >= 0 && demand.audio >= 0)
        require(demand.memoryHeavy <= 1 && demand.cpuHeavy <= 1 && demand.databaseWriter <= 1)
        val permits = acquirePermits(demand)
        val lease = try {
            acquireResourceUsage(owner, demand)
        } catch (error: Throwable) {
            permits.close()
            throw error
        }
        return try {
            block()
        } finally {
            lease.close()
            permits.close()
        }
    }

    suspend fun <T> withResourceObservation(
        owner: String,
        demand: ForegroundResourceDemand,
        block: suspend () -> T
    ): T {
        val lease = acquireResourceUsage(owner, demand)
        return try {
            block()
        } finally {
            lease.close()
        }
    }

    fun acquireResourceUsage(owner: String, demand: ForegroundResourceDemand): ResourceLease {
        require(demand.memoryHeavy >= 0 && demand.cpuHeavy >= 0)
        require(demand.network >= 0 && demand.databaseWriter >= 0 && demand.audio >= 0)
        val leaseId = leaseIds.incrementAndGet()
        leases.update { current -> current + (leaseId to Lease(owner, demand)) }
        refreshUsage()
        return ResourceLease {
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
            audio = active.sumOf { it.demand.audio },
            exclusiveOwners = active.filter { it.demand.exclusive }.mapTo(linkedSetOf()) { it.owner }
        )
    }

    private suspend fun acquirePermits(demand: ForegroundResourceDemand): ResourceLease {
        var memoryAcquired = false
        var cpuAcquired = false
        var databaseAcquired = false
        try {
            if (demand.memoryHeavy > 0) {
                memoryGate.acquire()
                memoryAcquired = true
            }
            if (demand.cpuHeavy > 0) {
                cpuGate.acquire()
                cpuAcquired = true
            }
            if (demand.databaseWriter > 0) {
                databaseWriterGate.acquire()
                databaseAcquired = true
            }
        } catch (error: Throwable) {
            if (databaseAcquired) databaseWriterGate.release()
            if (cpuAcquired) cpuGate.release()
            if (memoryAcquired) memoryGate.release()
            throw error
        }
        return ResourceLease {
            if (databaseAcquired) databaseWriterGate.release()
            if (cpuAcquired) cpuGate.release()
            if (memoryAcquired) memoryGate.release()
        }
    }
}

class ResourceLease internal constructor(
    private val release: () -> Unit
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) release()
    }
}
