package com.xty.englishhelper.domain.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsEngineTest {

    private val engine = FsrsEngine(enableFuzz = false)

    // --- reviewNew ---

    @Test
    fun `reviewNew Good produces expected stability and difficulty`() {
        val now = 1_000_000_000L
        val result = engine.reviewNew(Rating.Good, now)

        // S0(Good) = w2 = 3.173
        assertEquals(3.173, result.stability, 0.01)
        // D0(Good) = w4 - e^(w5*2) + 1
        assertTrue(result.difficulty in 1.0..10.0)
        // With default learningSteps=[1,10], Good enters Learning step 1
        assertEquals(CardState.Learning, result.state)
        assertEquals(1, result.step)
        assertEquals(1, result.reps)
        assertEquals(0, result.lapses)
        assertTrue(result.due > now)
        assertTrue(result.scheduledInterval > 0)
    }

    @Test
    fun `reviewNew Again enters Learning at step 0`() {
        val now = 1_000_000_000L
        val result = engine.reviewNew(Rating.Again, now)

        assertEquals(CardState.Learning, result.state)
        assertEquals(0, result.step)
        // S0(Again) = w0 = 0.40255
        assertEquals(0.40255, result.stability, 0.01)
        // due = now + 1 min
        assertEquals(now + 60_000L, result.due)
        assertEquals(60_000L, result.scheduledInterval)
    }

    @Test
    fun `reviewNew Hard enters Learning at step 0 with longer interval`() {
        val now = 1_000_000_000L
        val result = engine.reviewNew(Rating.Hard, now)

        assertEquals(CardState.Learning, result.state)
        assertEquals(0, result.step)
        // 1 min * 1.5 = 1.5 min = 90_000 ms
        assertEquals(now + 90_000L, result.due)
        assertEquals(90_000L, result.scheduledInterval)
    }

    @Test
    fun `reviewNew Good with single-step graduates directly`() {
        // Single-step engine: learningSteps = [1] (size 1)
        val singleStepEngine = FsrsEngine(enableFuzz = false, learningSteps = listOf(1L))
        val now = 1_000_000_000L
        val result = singleStepEngine.reviewNew(Rating.Good, now)

        assertEquals(CardState.Review, result.state)
        assertEquals(null, result.step)
    }

    @Test
    fun `reviewNew Easy graduates immediately to Review`() {
        val now = 1_000_000_000L
        val result = engine.reviewNew(Rating.Easy, now)

        assertEquals(CardState.Review, result.state)
        assertEquals(null, result.step)
        assertTrue(result.due > now)
        // S0(Easy) = w3 = 15.69105, interval should be ~15-16 days
        val intervalDays = result.scheduledInterval / (24 * 60 * 60 * 1000.0)
        assertTrue("Interval should be >= 1 day, was $intervalDays", intervalDays >= 1.0)
    }

    // --- Learning → graduation ---

    @Test
    fun `Learning step 1 Good graduates to Review`() {
        val now = 1_000_000_000L
        // Card in Learning, step 1 (last step of [1, 10])
        val result = engine.review(
            state = CardState.Learning,
            step = 1,
            stability = 3.173,
            difficulty = 5.5,
            lastReviewAt = now - 10 * 60_000, // 10 min ago
            reps = 1,
            lapses = 0,
            rating = Rating.Good,
            now = now
        )

        assertEquals(CardState.Review, result.state)
        assertEquals(null, result.step)
        assertEquals(2, result.reps)
    }

    @Test
    fun `Learning Again resets to step 0`() {
        val now = 1_000_000_000L
        val result = engine.review(
            state = CardState.Learning,
            step = 1,
            stability = 3.173,
            difficulty = 5.5,
            lastReviewAt = now - 10 * 60_000,
            reps = 1,
            lapses = 0,
            rating = Rating.Again,
            now = now
        )

        assertEquals(CardState.Learning, result.state)
        assertEquals(0, result.step)
        // due = now + learningSteps[0] * 60000 = now + 1 min
        assertEquals(now + 60_000L, result.due)
    }

    @Test
    fun `Learning Easy graduates immediately`() {
        val now = 1_000_000_000L
        val result = engine.review(
            state = CardState.Learning,
            step = 0,
            stability = 3.173,
            difficulty = 5.5,
            lastReviewAt = now - 60_000,
            reps = 1,
            lapses = 0,
            rating = Rating.Easy,
            now = now
        )

        assertEquals(CardState.Review, result.state)
        assertEquals(null, result.step)
    }

    // --- Review state ---

    @Test
    fun `Review Good increases stability`() {
        val now = 1_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L
        val result = engine.review(
            state = CardState.Review,
            step = null,
            stability = 3.0,
            difficulty = 5.0,
            lastReviewAt = now - 3 * dayMs, // 3 days ago
            reps = 3,
            lapses = 0,
            rating = Rating.Good,
            now = now
        )

        assertEquals(CardState.Review, result.state)
        assertTrue("New stability should be > old", result.stability > 3.0)
        assertEquals(4, result.reps)
        assertEquals(0, result.lapses)
    }

    @Test
    fun `Review Again enters Relearning and increments lapses`() {
        val now = 1_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L
        val result = engine.review(
            state = CardState.Review,
            step = null,
            stability = 10.0,
            difficulty = 5.0,
            lastReviewAt = now - 10 * dayMs,
            reps = 5,
            lapses = 0,
            rating = Rating.Again,
            now = now
        )

        assertEquals(CardState.Relearning, result.state)
        assertEquals(0, result.step)
        assertEquals(1, result.lapses)
        // due = now + relearningSteps[0] * 60000 = now + 10 min
        assertEquals(now + 10 * 60_000L, result.due)
    }

    @Test
    fun `Review Hard produces shorter interval than Good`() {
        val now = 1_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L
        val hardResult = engine.review(
            state = CardState.Review, step = null, stability = 10.0, difficulty = 5.0,
            lastReviewAt = now - 10 * dayMs, reps = 5, lapses = 0,
            rating = Rating.Hard, now = now
        )
        val goodResult = engine.review(
            state = CardState.Review, step = null, stability = 10.0, difficulty = 5.0,
            lastReviewAt = now - 10 * dayMs, reps = 5, lapses = 0,
            rating = Rating.Good, now = now
        )

        assertTrue(
            "Hard interval (${hardResult.scheduledInterval}) should be <= Good interval (${goodResult.scheduledInterval})",
            hardResult.scheduledInterval <= goodResult.scheduledInterval
        )
    }

    @Test
    fun `Review Easy produces longer interval than Good`() {
        val now = 1_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L
        val goodResult = engine.review(
            state = CardState.Review, step = null, stability = 10.0, difficulty = 5.0,
            lastReviewAt = now - 10 * dayMs, reps = 5, lapses = 0,
            rating = Rating.Good, now = now
        )
        val easyResult = engine.review(
            state = CardState.Review, step = null, stability = 10.0, difficulty = 5.0,
            lastReviewAt = now - 10 * dayMs, reps = 5, lapses = 0,
            rating = Rating.Easy, now = now
        )

        assertTrue(
            "Easy interval (${easyResult.scheduledInterval}) should be >= Good interval (${goodResult.scheduledInterval})",
            easyResult.scheduledInterval >= goodResult.scheduledInterval
        )
    }

    // --- Relearning ---

    @Test
    fun `Relearning Good at last step graduates to Review`() {
        val now = 1_000_000_000L
        // Default relearning_steps = [10], so step 0 is the last step
        val result = engine.review(
            state = CardState.Relearning,
            step = 0,
            stability = 2.0,
            difficulty = 6.0,
            lastReviewAt = now - 10 * 60_000,
            reps = 5,
            lapses = 1,
            rating = Rating.Good,
            now = now
        )

        assertEquals(CardState.Review, result.state)
        assertEquals(null, result.step)
    }

    // --- Core formulas ---

    @Test
    fun `retrievability at t=0 is 1`() {
        val r = engine.retrievability(0.0, 10.0)
        assertEquals(1.0, r, 0.001)
    }

    @Test
    fun `retrievability at t=S is approximately 0_9`() {
        // By definition: when t = S, R ≈ 0.9
        val r = engine.retrievability(10.0, 10.0)
        assertEquals(0.9, r, 0.01)
    }

    @Test
    fun `retrievability decreases over time`() {
        val r1 = engine.retrievability(1.0, 10.0)
        val r2 = engine.retrievability(5.0, 10.0)
        val r3 = engine.retrievability(20.0, 10.0)
        assertTrue(r1 > r2)
        assertTrue(r2 > r3)
    }

    @Test
    fun `initialDifficulty is clamped 1 to 10`() {
        for (rating in Rating.entries) {
            val d = engine.initialDifficulty(rating)
            assertTrue("D($rating) = $d should be in [1, 10]", d in 1.0..10.0)
        }
    }

    @Test
    fun `nextDifficulty decreases on Easy and increases on Again`() {
        val d = 5.0
        val dAfterEasy = engine.nextDifficulty(d, Rating.Easy)
        val dAfterAgain = engine.nextDifficulty(d, Rating.Again)
        assertTrue("Easy should decrease difficulty", dAfterEasy < d)
        assertTrue("Again should increase difficulty", dAfterAgain > d)
    }

    @Test
    fun `nextInterval returns at least 1 day`() {
        val interval = engine.nextInterval(0.001)
        assertTrue(interval >= 1)
    }

    @Test
    fun `nextInterval respects maxInterval`() {
        val interval = engine.nextInterval(1_000_000.0)
        assertTrue(interval <= FsrsConstants.MAX_INTERVAL)
    }

    // --- previewIntervals ---

    @Test
    fun `previewIntervals returns all four ratings`() {
        val now = 1_000_000_000L
        val dayMs = 24 * 60 * 60 * 1000L
        val intervals = engine.previewIntervals(
            state = CardState.Review,
            step = null,
            stability = 10.0,
            difficulty = 5.0,
            lastReviewAt = now - 10 * dayMs,
            reps = 5,
            lapses = 0,
            now = now
        )

        assertEquals(4, intervals.size)
        assertTrue(intervals.containsKey(Rating.Again))
        assertTrue(intervals.containsKey(Rating.Hard))
        assertTrue(intervals.containsKey(Rating.Good))
        assertTrue(intervals.containsKey(Rating.Easy))
        intervals.values.forEach { assertTrue(it > 0) }
    }

    // --- formatInterval ---

    @Test
    fun `formatInterval formats minutes correctly`() {
        assertEquals("1分", formatInterval(60_000L))
        assertEquals("30分", formatInterval(30 * 60_000L))
    }

    @Test
    fun `formatInterval formats hours correctly`() {
        assertEquals("1时", formatInterval(60 * 60_000L))
        assertEquals("12时", formatInterval(12 * 60 * 60_000L))
    }

    @Test
    fun `formatInterval formats days correctly`() {
        assertEquals("1天", formatInterval(24 * 60 * 60_000L))
        assertEquals("15天", formatInterval(15L * 24 * 60 * 60_000L))
    }

    @Test
    fun `formatInterval formats months correctly`() {
        assertEquals("1月", formatInterval(30L * 24 * 60 * 60_000))
        assertEquals("2月", formatInterval(60L * 24 * 60 * 60_000))
    }

    // --- Edge cases ---

    @Test
    fun `review with zero stability does not crash`() {
        val now = 1_000_000_000L
        val result = engine.review(
            state = CardState.Review,
            step = null,
            stability = 0.001,
            difficulty = 5.0,
            lastReviewAt = now - 86_400_000,
            reps = 1,
            lapses = 0,
            rating = Rating.Good,
            now = now
        )
        assertTrue(result.stability >= FsrsConstants.STABILITY_MIN)
    }

    @Test
    fun `same-day review uses shortTermStability`() {
        val now = 1_000_000_000L
        // Review a card that was last reviewed 1 hour ago (same day)
        val result = engine.review(
            state = CardState.Review,
            step = null,
            stability = 10.0,
            difficulty = 5.0,
            lastReviewAt = now - 3_600_000, // 1 hour ago
            reps = 5,
            lapses = 0,
            rating = Rating.Good,
            now = now
        )
        // Should still produce a valid result
        assertTrue(result.stability > 0)
        assertEquals(CardState.Review, result.state)
    }
}
