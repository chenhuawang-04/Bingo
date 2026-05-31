package com.xty.englishhelper.data.repository.pool

import kotlinx.coroutines.delay

/**
 * Token-bucket rate limiter that tracks the NEXT allowed acquisition time.
 * @param maxPerMinute maximum requests per minute; 0 = unlimited
 *
 * Uses System.nanoTime() (monotonic clock) to avoid NTP adjustments.
 * Tracks nextAcquireNs instead of lastAcquireNs to avoid negative-elapsed
 * bugs when multiple coroutines call acquire() concurrently.
 */
internal class EdgeRateLimiter(maxPerMinute: Int) {
    private val minIntervalNs = if (maxPerMinute > 0) 60_000_000_000L / maxPerMinute else 0L
    private var nextAcquireNs = 0L
    private val lock = Any()

    suspend fun acquire() {
        if (minIntervalNs <= 0) return
        val waitNs = synchronized(lock) {
            val now = System.nanoTime()
            if (nextAcquireNs == 0L) {
                // First call: allow immediately, schedule next
                nextAcquireNs = now + minIntervalNs
                0L
            } else if (now >= nextAcquireNs) {
                // Enough time has passed: allow immediately
                nextAcquireNs = now + minIntervalNs
                0L
            } else {
                // Must wait until nextAcquireNs
                val delayNs = nextAcquireNs - now
                nextAcquireNs += minIntervalNs  // Schedule next AFTER this one
                delayNs
            }
        }
        if (waitNs > 0) {
            delay((waitNs + 999_999) / 1_000_000) // Round up to nearest ms
        }
    }
}
