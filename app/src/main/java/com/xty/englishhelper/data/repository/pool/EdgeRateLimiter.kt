package com.xty.englishhelper.data.repository.pool

import kotlinx.coroutines.delay

/**
 * Token-bucket rate limiter that tracks the NEXT allowed acquisition time.
 * @param maxPerMinute maximum requests per minute; 0 = unlimited
 *
 * Uses System.nanoTime() (monotonic clock) to avoid NTP adjustments.
 * Tracks nextAcquireNs instead of lastAcquireNs to avoid negative-elapsed
 * bugs when multiple coroutines call acquire() concurrently.
 *
 * 速率可在运行中通过 [updateRate] **热更新**（不重置已排定的节奏 nextAcquireNs），
 * 以便词池构建途中修改「每分钟请求数」能从后续请求起立即生效。
 */
internal class EdgeRateLimiter(maxPerMinute: Int) {
    // minIntervalNs 可热更新，故必须在 lock 内读写，避免与 acquire() 竞争。
    private var minIntervalNs = intervalFor(maxPerMinute)
    private var nextAcquireNs = 0L
    private val lock = Any()

    /**
     * 热更新速率：仅重算请求间隔，**保留** nextAcquireNs 的既有排程。
     * 切勿用「重建实例」来改速率——那会把 nextAcquireNs 清零，使每次更新后的首个请求立即放行，
     * 在频繁更新时突发击穿限流。
     *
     * 提速（间隔变小）时，把先前按旧慢速率排定的 nextAcquireNs 向前收敛到 now+newInterval，
     * 让提速尽快生效；收敛目标仍 ≥ 现在，因此不产生突发。减速则不动，自然按新间隔变慢。
     */
    fun updateRate(maxPerMinute: Int) {
        synchronized(lock) {
            val newInterval = intervalFor(maxPerMinute)
            minIntervalNs = newInterval
            if (newInterval > 0L && nextAcquireNs != 0L) {
                val earliest = System.nanoTime() + newInterval
                if (nextAcquireNs > earliest) nextAcquireNs = earliest
            }
        }
    }

    suspend fun acquire() {
        val waitNs = synchronized(lock) {
            val interval = minIntervalNs            // 在 lock 内读取最新间隔
            if (interval <= 0L) {
                0L                                  // 0 = 不限流
            } else {
                val now = System.nanoTime()
                when {
                    nextAcquireNs == 0L -> {
                        // First call: allow immediately, schedule next
                        nextAcquireNs = now + interval
                        0L
                    }
                    now >= nextAcquireNs -> {
                        // Enough time has passed: allow immediately
                        nextAcquireNs = now + interval
                        0L
                    }
                    else -> {
                        // Must wait until nextAcquireNs
                        val delayNs = nextAcquireNs - now
                        nextAcquireNs += interval   // Schedule next AFTER this one
                        delayNs
                    }
                }
            }
        }
        if (waitNs > 0) {
            delay((waitNs + 999_999) / 1_000_000) // Round up to nearest ms
        }
    }

    private companion object {
        fun intervalFor(maxPerMinute: Int): Long =
            if (maxPerMinute > 0) 60_000_000_000L / maxPerMinute else 0L
    }
}
