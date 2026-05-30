package com.xty.englishhelper.data.repository.pool

import kotlinx.coroutines.delay

/**
 * Simple token-bucket rate limiter.
 * @param maxPerMinute maximum requests per minute; 0 = unlimited
 */
internal class EdgeRateLimiter(maxPerMinute: Int) {
    private val minIntervalMs = if (maxPerMinute > 0) 60_000L / maxPerMinute else 0L
    private var lastAcquireMs = 0L
    private val lock = Any()

    suspend fun acquire() {
        if (minIntervalMs <= 0) return
        val waitMs = synchronized(lock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastAcquireMs
            if (elapsed >= minIntervalMs) {
                lastAcquireMs = now
                0L
            } else {
                val nextAvailable = lastAcquireMs + minIntervalMs
                val delayMs = nextAvailable - now
                lastAcquireMs = nextAvailable
                delayMs
            }
        }
        if (waitMs > 0) {
            delay(waitMs)
        }
    }
}
