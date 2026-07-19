package com.xty.englishhelper.data.image

import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.background.AppResourceCoordinator
import com.xty.englishhelper.domain.background.AppResourceUsage
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCompressionManagerTest {

    private val manager = ImageCompressionManager(mockk(relaxed = true))
    private val disabledConfig = SettingsDataStore.ImageCompressionConfig(
        enabled = false,
        targetBytes = 1_000_000
    )

    @Test
    fun `batch reads preserve order and release resource lease`() = runTest {
        val result = manager.readAndCompressAll(listOf(1, 2), disabledConfig) { value ->
            byteArrayOf(value.toByte())
        }

        assertEquals(listOf(1, 2), result.map { it.single().toInt() })
        assertEquals(AppResourceUsage(), AppResourceCoordinator.usage.value)
    }

    @Test
    fun `oversized image batches fail instead of silently truncating`() = runTest {
        val failure = runCatching {
            manager.readAndCompressAll((1..51).toList(), disabledConfig) { byteArrayOf(1) }
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(AppResourceUsage(), AppResourceCoordinator.usage.value)
    }
}
