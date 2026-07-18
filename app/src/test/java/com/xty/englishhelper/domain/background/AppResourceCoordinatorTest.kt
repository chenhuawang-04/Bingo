package com.xty.englishhelper.domain.background

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
    }

    @Test
    fun `owner is cleared when operation fails`() = runTest {
        runCatching {
            AppResourceCoordinator.withMemoryHeavyOperation("failure") {
                error("expected")
            }
        }

        assertNull(AppResourceCoordinator.memoryHeavyOwner.value)
    }
}
