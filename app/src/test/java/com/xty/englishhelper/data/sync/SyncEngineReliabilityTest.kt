package com.xty.englishhelper.data.sync

import android.util.Log
import com.xty.englishhelper.data.repository.GitHubSyncRepositoryImpl
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineReliabilityTest {
    private val repository = mockk<GitHubSyncRepositoryImpl>()
    private val tracker = mockk<SyncProgressTracker>(relaxed = true)
    private val engine = SyncEngine(repository, tracker)

    @Test
    fun `repository failure is reported as unsuccessful result`() = runTest {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        coEvery { repository.sync(any()) } throws IllegalStateException("broken snapshot")

        val result = try {
            engine.sync(SyncMode.SMART)
        } finally {
            unmockkStatic(Log::class)
        }

        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("broken snapshot"))
    }

    @Test(expected = CancellationException::class)
    fun `cancellation remains cancellation instead of becoming sync failure`() = runTest {
        coEvery { repository.sync(any()) } throws CancellationException("stopped")

        engine.sync(SyncMode.SMART)
    }
}
