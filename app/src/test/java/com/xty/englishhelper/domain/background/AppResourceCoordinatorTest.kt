package com.xty.englishhelper.domain.background

import com.xty.englishhelper.domain.model.BackgroundTask
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.BackgroundTaskType
import com.xty.englishhelper.domain.model.WordPoolRebuildPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AppResourceCoordinatorTest {
    @Test
    fun `memory heavy operations never overlap`() = runTest {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)

        val first = async {
            AppResourceCoordinator.withMemoryHeavyOperation("first") {
                peak.updateAndGet { maxOf(it, active.incrementAndGet()) }
                delay(10)
                active.decrementAndGet()
            }
        }
        val second = async {
            AppResourceCoordinator.withMemoryHeavyOperation("second") {
                peak.updateAndGet { maxOf(it, active.incrementAndGet()) }
                active.decrementAndGet()
            }
        }
        first.await()
        second.await()

        assertEquals(1, peak.get())
        assertNull(AppResourceCoordinator.memoryHeavyOwner.value)
        assertEquals(AppResourceUsage(), AppResourceCoordinator.usage.value)
    }

    @Test
    fun `owner is cleared when operation fails`() = runTest {
        runCatching {
            AppResourceCoordinator.withMemoryHeavyOperation("failure") {
                error("expected")
            }
        }

        assertNull(AppResourceCoordinator.memoryHeavyOwner.value)
        assertEquals(AppResourceUsage(), AppResourceCoordinator.usage.value)
    }

    @Test
    fun `foreground usage is visible to background resource budgeting`() = runTest {
        AppResourceCoordinator.withResourceUsage(
            owner = "foreground-write",
            demand = ForegroundResourceDemand(databaseWriter = 1)
        ) {
            val candidate = BackgroundTask(
                id = 1,
                type = BackgroundTaskType.WORD_POOL_REBUILD,
                status = BackgroundTaskStatus.PENDING,
                payload = WordPoolRebuildPayload(dictionaryId = 1, strategy = "BALANCED"),
                progressCurrent = 0,
                progressTotal = 0,
                progressMessage = null,
                attempt = 0,
                errorMessage = null,
                createdAt = 1,
                updatedAt = 1,
                dedupeKey = "pool:1"
            )
            assertEquals(1, AppResourceCoordinator.usage.value.databaseWriter)
            assertEquals(
                emptyList<Long>(),
                selectLaunchablePendingTasks(
                    pendingTasks = listOf(candidate),
                    runningTasks = emptyList(),
                    slots = 1,
                    foregroundUsage = AppResourceCoordinator.usage.value
                ).map { it.id }
            )
        }
        assertEquals(AppResourceUsage(), AppResourceCoordinator.usage.value)
    }

    @Test
    fun `resource leases release exactly once`() {
        val lease = AppResourceCoordinator.acquireResourceUsage(
            owner = "network-test",
            demand = ForegroundResourceDemand(network = 1)
        )
        assertEquals(1, AppResourceCoordinator.usage.value.network)

        lease.close()
        lease.close()

        assertEquals(AppResourceUsage(), AppResourceCoordinator.usage.value)
    }

    @Test
    fun `cpu heavy foreground operations are admission controlled`() = runTest {
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)

        val first = async {
            AppResourceCoordinator.withResourceUsage(
                owner = "cpu-first",
                demand = ForegroundResourceDemand(cpuHeavy = 1)
            ) {
                peak.updateAndGet { maxOf(it, active.incrementAndGet()) }
                delay(10)
                active.decrementAndGet()
            }
        }
        val second = async {
            AppResourceCoordinator.withResourceUsage(
                owner = "cpu-second",
                demand = ForegroundResourceDemand(cpuHeavy = 1)
            ) {
                peak.updateAndGet { maxOf(it, active.incrementAndGet()) }
                active.decrementAndGet()
            }
        }
        first.await()
        second.await()

        assertEquals(1, peak.get())
        assertEquals(AppResourceUsage(), AppResourceCoordinator.usage.value)
    }
}
