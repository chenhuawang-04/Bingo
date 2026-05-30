package com.xty.englishhelper.data.repository.pool

import kotlinx.coroutines.delay

/**
 * Simple token-bucket rate limiter.
 * @param maxPerMinute maximum requests per minute; 0 = unlimited
 *
 * N9 fix: uses System.nanoTime() (monotonic clock) instead of
 * System.currentTimeMillis() which can jump backward due to NTP adjustments.
 */
internal class EdgeRateLimiter(maxPerMinute: Int) {
    private val minIntervalNs = if (maxPerMinute > 0) 60_000_000_000L / maxPerMinute else 0L
    private var lastAcquireNs = 0L
    private val lock = Any()

    suspend fun acquire() {
        if (minIntervalNs <= 0) return
        val waitNs = synchronized(lock) {
            val now = System.nanoTime()
            if (lastAcquireNs == 0L) {
                // First call: record timestamp, no wait
                lastAcquireNs = now
                0L
            } else {
                val elapsed = now - lastAcquireNs
                if (elapsed >= minIntervalNs) {
                    lastAcquireNs = now
                    0L
                } else {
                    val delayNs = minIntervalNs - elapsed
                    lastAcquireNs = now + delayNs
                    delayNs
                }
            }
        }
        if (waitNs > 0) {
            delay(waitNs / 1_000_000) // convert ns to ms
        }
    }
}
