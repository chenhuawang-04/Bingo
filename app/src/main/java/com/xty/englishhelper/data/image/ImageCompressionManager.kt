package com.xty.englishhelper.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.xty.englishhelper.data.preferences.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Singleton
class ImageCompressionManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    suspend fun compressIfNeeded(bytes: ByteArray): ByteArray {
        val config = settingsDataStore.getImageCompressionConfig()
        return compressIfNeeded(bytes, config)
    }

    suspend fun compressAll(bytes: List<ByteArray>): List<ByteArray> {
        if (bytes.isEmpty()) return bytes
        val config = settingsDataStore.getImageCompressionConfig()
        return compressAll(bytes, config)
    }

    suspend fun compressIfNeeded(
        bytes: ByteArray,
        config: SettingsDataStore.ImageCompressionConfig
    ): ByteArray {
        if (!config.enabled) return bytes
        return withContext(Dispatchers.Default) {
            ImageCompressor.compress(bytes, config.targetBytes)
        }
    }

    suspend fun compressAll(
        bytes: List<ByteArray>,
        config: SettingsDataStore.ImageCompressionConfig
    ): List<ByteArray> {
        if (bytes.isEmpty()) return bytes
        if (!config.enabled) return bytes
        return withContext(Dispatchers.Default) {
            buildList(bytes.size) {
                bytes.forEach { add(ImageCompressor.compress(it, config.targetBytes)) }
            }
        }
    }

    suspend fun <T> readAndCompressAll(
        items: List<T>,
        config: SettingsDataStore.ImageCompressionConfig,
        reader: suspend (T) -> ByteArray?
    ): List<ByteArray> {
        if (items.isEmpty()) return emptyList()

        val results = ArrayList<ByteArray>(items.size)
        for (item in items) {
            val rawBytes = withContext(Dispatchers.IO) { reader(item) } ?: continue
            val finalBytes = if (config.enabled) {
                withContext(Dispatchers.Default) {
                    ImageCompressor.compress(rawBytes, config.targetBytes)
                }
            } else {
                rawBytes
            }
            results.add(finalBytes)
        }
        return results
    }
}

private object ImageCompressor {
    private const val MAX_LONG_EDGE = 3200
    private const val MIN_LONG_EDGE = 800
    private const val MAX_QUALITY = 90
    private const val MIN_QUALITY = 45
    private const val QUALITY_SEARCH_STEPS = 7
    private const val SCALE_ATTEMPTS = 4
    private const val SCALE_FACTOR = 0.85f

    fun compress(bytes: ByteArray, targetBytes: Int): ByteArray {
        if (bytes.size <= targetBytes) return bytes

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return bytes

        val longEdge = max(width, height)
        val desiredLongEdge = computeDesiredLongEdge(bytes.size, targetBytes, longEdge)
        val sampleSize = computeInSampleSize(width, height, desiredLongEdge)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return bytes
        bitmap = ensureOpaque(bitmap)

        var compressed = compressBitmap(bitmap, MAX_QUALITY)
        if (compressed.size <= targetBytes) {
            bitmap.recycle()
            return compressed
        }

        compressed = searchQuality(bitmap, targetBytes, compressed)
        if (compressed.size <= targetBytes) {
            bitmap.recycle()
            return compressed
        }

        var attempt = 0
        while (attempt < SCALE_ATTEMPTS && compressed.size > targetBytes) {
            val newWidth = (bitmap.width * SCALE_FACTOR).roundToInt().coerceAtLeast(1)
            val newHeight = (bitmap.height * SCALE_FACTOR).roundToInt().coerceAtLeast(1)
            if (newWidth >= bitmap.width || newHeight >= bitmap.height) break
            val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            if (scaled != bitmap) {
                bitmap.recycle()
            }
            bitmap = scaled
            compressed = compressBitmap(bitmap, MAX_QUALITY)
            compressed = searchQuality(bitmap, targetBytes, compressed)
            attempt++
        }

        bitmap.recycle()
        return compressed
    }

    private fun searchQuality(bitmap: Bitmap, targetBytes: Int, currentBest: ByteArray): ByteArray {
        var best = currentBest
        var bestSize = currentBest.size
        var minQ = MIN_QUALITY
        var maxQ = MAX_QUALITY
        repeat(QUALITY_SEARCH_STEPS) {
            val mid = (minQ + maxQ) / 2
            val out = compressBitmap(bitmap, mid)
            if (out.size < bestSize) {
                best = out
                bestSize = out.size
            }
            if (out.size <= targetBytes) {
                minQ = mid + 1
                best = out
                bestSize = out.size
            } else {
                maxQ = mid - 1
            }
        }
        return best
    }

    private fun compressBitmap(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)
        return stream.toByteArray()
    }

    private fun ensureOpaque(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(opaque)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmap.recycle()
        return opaque
    }

    private fun computeDesiredLongEdge(originalBytes: Int, targetBytes: Int, longEdge: Int): Int {
        val ratio = sqrt(targetBytes.toDouble() / originalBytes.toDouble()).coerceIn(0.2, 1.0)
        val scaled = (longEdge * ratio).roundToInt().coerceAtLeast(1)
        val capped = scaled.coerceAtMost(longEdge)
        val minEdge = if (longEdge < MIN_LONG_EDGE) 1 else MIN_LONG_EDGE
        return capped.coerceIn(minEdge, MAX_LONG_EDGE)
    }

    private fun computeInSampleSize(width: Int, height: Int, desiredLongEdge: Int): Int {
        var sample = 1
        val longEdge = max(width, height)
        while (longEdge / sample > desiredLongEdge && sample < 32) {
            sample *= 2
        }
        return sample
    }
}
