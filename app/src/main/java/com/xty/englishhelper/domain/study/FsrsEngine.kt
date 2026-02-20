package com.xty.englishhelper.domain.study

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

object FsrsConstants {
    const val DECAY = -0.5
    const val FACTOR = 19.0 / 81.0
    const val STABILITY_MIN = 0.001
    val DEFAULT_PARAMS = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105,       // w0-w3: initial stability S0(Again/Hard/Good/Easy)
        7.1949, 0.5345,                            // w4-w5: initial difficulty
        1.4604, 0.0046,                            // w6-w7: difficulty update
        1.54575, 0.1192, 1.01925,                  // w8-w10: recall stability
        1.9395, 0.11, 0.29605, 2.2698,             // w11-w14: forget stability
        0.2315, 2.9898,                            // w15-w16: hard penalty / easy bonus
        0.51655, 0.6621                            // w17-w18: short-term (same-day) review
    )
    val DEFAULT_LEARNING_STEPS = listOf(1L, 10L)         // minutes
    val DEFAULT_RELEARNING_STEPS = listOf(10L)            // minutes
    const val DEFAULT_DESIRED_RETENTION = 0.9
    const val MAX_INTERVAL = 36500                        // days
}

enum class Rating(val value: Int) {
    Again(1), Hard(2), Good(3), Easy(4);

    companion object {
        fun fromValue(value: Int): Rating = entries.first { it.value == value }
    }
}

enum class CardState(val value: Int) {
    Learning(1), Review(2), Relearning(3);

    companion object {
        fun fromValue(value: Int): CardState = entries.first { it.value == value }
    }
}

data class SchedulingResult(
    val state: CardState,
    val step: Int?,
    val stability: Double,
    val difficulty: Double,
    val due: Long,             // timestamp (millis)
    val lastReviewAt: Long,
    val reps: Int,
    val lapses: Int,
    val scheduledInterval: Long // preview: next interval (millis)
)

class FsrsEngine(
    private val params: DoubleArray = FsrsConstants.DEFAULT_PARAMS,
    private val desiredRetention: Double = FsrsConstants.DEFAULT_DESIRED_RETENTION,
    private val learningSteps: List<Long> = FsrsConstants.DEFAULT_LEARNING_STEPS,
    private val relearningSteps: List<Long> = FsrsConstants.DEFAULT_RELEARNING_STEPS,
    private val maxInterval: Int = FsrsConstants.MAX_INTERVAL,
    private val enableFuzz: Boolean = true
) {
    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MINUTE_MS = 60 * 1000L
    }

    /**
     * First review of a brand-new card (no prior study state).
     */
    fun reviewNew(rating: Rating, now: Long): SchedulingResult {
        val s0 = initialStability(rating)
        val d0 = initialDifficulty(rating)

        return when (rating) {
            Rating.Again -> {
                // Enter Learning, step 0
                val stepMinutes = learningSteps.getOrElse(0) { 1L }
                val interval = stepMinutes * MINUTE_MS
                SchedulingResult(
                    state = CardState.Learning,
                    step = 0,
                    stability = s0,
                    difficulty = d0,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = 1,
                    lapses = 0,
                    scheduledInterval = interval
                )
            }
            Rating.Hard -> {
                // Enter Learning, step 0 with slightly longer interval
                val stepMinutes = learningSteps.getOrElse(0) { 1L }
                val interval = (stepMinutes * 1.5 * MINUTE_MS).toLong()
                SchedulingResult(
                    state = CardState.Learning,
                    step = 0,
                    stability = s0,
                    difficulty = d0,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = 1,
                    lapses = 0,
                    scheduledInterval = interval
                )
            }
            Rating.Good -> {
                if (learningSteps.size <= 1) {
                    // Graduate immediately to Review
                    val intervalDays = nextInterval(s0)
                    val fuzzedDays = applyFuzz(intervalDays)
                    val interval = fuzzedDays * DAY_MS
                    SchedulingResult(
                        state = CardState.Review,
                        step = null,
                        stability = s0,
                        difficulty = d0,
                        due = now + interval,
                        lastReviewAt = now,
                        reps = 1,
                        lapses = 0,
                        scheduledInterval = interval
                    )
                } else {
                    // Move to step 1
                    val stepMinutes = learningSteps[1]
                    val interval = stepMinutes * MINUTE_MS
                    SchedulingResult(
                        state = CardState.Learning,
                        step = 1,
                        stability = s0,
                        difficulty = d0,
                        due = now + interval,
                        lastReviewAt = now,
                        reps = 1,
                        lapses = 0,
                        scheduledInterval = interval
                    )
                }
            }
            Rating.Easy -> {
                // Graduate immediately to Review with easy bonus
                val intervalDays = nextInterval(s0)
                val fuzzedDays = applyFuzz(intervalDays)
                val interval = max(fuzzedDays * DAY_MS, DAY_MS) // at least 1 day
                SchedulingResult(
                    state = CardState.Review,
                    step = null,
                    stability = s0,
                    difficulty = d0,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = 1,
                    lapses = 0,
                    scheduledInterval = interval
                )
            }
        }
    }

    /**
     * Subsequent review of a card with existing study state.
     */
    fun review(
        state: CardState,
        step: Int?,
        stability: Double,
        difficulty: Double,
        lastReviewAt: Long,
        reps: Int,
        lapses: Int,
        rating: Rating,
        now: Long
    ): SchedulingResult {
        val elapsedMs = now - lastReviewAt
        val elapsedDays = elapsedMs.toDouble() / DAY_MS

        return when (state) {
            CardState.Learning -> handleLearning(
                steps = learningSteps,
                graduateState = CardState.Review,
                isLearning = true,
                step = step ?: 0,
                stability = stability,
                difficulty = difficulty,
                reps = reps,
                lapses = lapses,
                rating = rating,
                now = now,
                elapsedDays = elapsedDays
            )
            CardState.Relearning -> handleLearning(
                steps = relearningSteps,
                graduateState = CardState.Review,
                isLearning = false,
                step = step ?: 0,
                stability = stability,
                difficulty = difficulty,
                reps = reps,
                lapses = lapses,
                rating = rating,
                now = now,
                elapsedDays = elapsedDays
            )
            CardState.Review -> handleReview(
                stability = stability,
                difficulty = difficulty,
                reps = reps,
                lapses = lapses,
                rating = rating,
                now = now,
                elapsedDays = elapsedDays
            )
        }
    }

    /**
     * Preview the scheduled intervals for all four ratings.
     * Returns Rating -> interval in millis.
     */
    fun previewIntervals(
        state: CardState,
        step: Int?,
        stability: Double,
        difficulty: Double,
        lastReviewAt: Long,
        reps: Int,
        lapses: Int,
        now: Long
    ): Map<Rating, Long> {
        return Rating.entries.associateWith { rating ->
            review(state, step, stability, difficulty, lastReviewAt, reps, lapses, rating, now)
                .scheduledInterval
        }
    }

    // --- Internal: Learning / Relearning ---

    private fun handleLearning(
        steps: List<Long>,
        graduateState: CardState,
        isLearning: Boolean,
        step: Int,
        stability: Double,
        difficulty: Double,
        reps: Int,
        lapses: Int,
        rating: Rating,
        now: Long,
        elapsedDays: Double
    ): SchedulingResult {
        val newD = nextDifficulty(difficulty, rating)

        when (rating) {
            Rating.Again -> {
                // Reset to step 0
                val stepMinutes = steps.getOrElse(0) { 1L }
                val interval = stepMinutes * MINUTE_MS
                return SchedulingResult(
                    state = if (isLearning) CardState.Learning else CardState.Relearning,
                    step = 0,
                    stability = stability,
                    difficulty = newD,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = reps + 1,
                    lapses = lapses,
                    scheduledInterval = interval
                )
            }
            Rating.Hard -> {
                // Stay at current step, slightly longer interval
                val currentStepMinutes = steps.getOrElse(step) { steps.lastOrNull() ?: 1L }
                val nextStepMinutes = steps.getOrElse(step + 1) { currentStepMinutes }
                val avgMinutes = (currentStepMinutes + nextStepMinutes) / 2
                val interval = max(avgMinutes * MINUTE_MS, currentStepMinutes * MINUTE_MS)
                return SchedulingResult(
                    state = if (isLearning) CardState.Learning else CardState.Relearning,
                    step = step,
                    stability = stability,
                    difficulty = newD,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = reps + 1,
                    lapses = lapses,
                    scheduledInterval = interval
                )
            }
            Rating.Good -> {
                if (step >= steps.size - 1) {
                    // Graduate: compute FSRS stability and schedule
                    val newS = if (elapsedDays < 1) {
                        shortTermStability(stability, rating)
                    } else {
                        initialStability(rating)
                            .let { max(it, stability) }
                    }
                    val intervalDays = nextInterval(newS)
                    val fuzzedDays = applyFuzz(intervalDays)
                    val interval = fuzzedDays * DAY_MS
                    return SchedulingResult(
                        state = graduateState,
                        step = null,
                        stability = newS,
                        difficulty = newD,
                        due = now + interval,
                        lastReviewAt = now,
                        reps = reps + 1,
                        lapses = lapses,
                        scheduledInterval = interval
                    )
                } else {
                    // Advance to next step
                    val nextStep = step + 1
                    val stepMinutes = steps[nextStep]
                    val interval = stepMinutes * MINUTE_MS
                    return SchedulingResult(
                        state = if (isLearning) CardState.Learning else CardState.Relearning,
                        step = nextStep,
                        stability = stability,
                        difficulty = newD,
                        due = now + interval,
                        lastReviewAt = now,
                        reps = reps + 1,
                        lapses = lapses,
                        scheduledInterval = interval
                    )
                }
            }
            Rating.Easy -> {
                // Graduate immediately with easy bonus
                val newS = if (elapsedDays < 1) {
                    shortTermStability(stability, Rating.Easy)
                } else {
                    initialStability(Rating.Easy)
                        .let { max(it, stability) }
                }
                val intervalDays = nextInterval(newS)
                val fuzzedDays = applyFuzz(intervalDays)
                val interval = max(fuzzedDays * DAY_MS, DAY_MS)
                return SchedulingResult(
                    state = graduateState,
                    step = null,
                    stability = newS,
                    difficulty = newD,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = reps + 1,
                    lapses = lapses,
                    scheduledInterval = interval
                )
            }
        }
    }

    // --- Internal: Review state ---

    private fun handleReview(
        stability: Double,
        difficulty: Double,
        reps: Int,
        lapses: Int,
        rating: Rating,
        now: Long,
        elapsedDays: Double
    ): SchedulingResult {
        val r = retrievability(elapsedDays, stability)
        val newD = nextDifficulty(difficulty, rating)

        val isSameDay = elapsedDays < 1

        when (rating) {
            Rating.Again -> {
                val newS = if (isSameDay) {
                    shortTermStability(stability, Rating.Again)
                } else {
                    nextStabilityAfterForget(difficulty, stability, r)
                }
                val stepMinutes = relearningSteps.getOrElse(0) { 10L }
                val interval = stepMinutes * MINUTE_MS
                return SchedulingResult(
                    state = CardState.Relearning,
                    step = 0,
                    stability = newS,
                    difficulty = newD,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = reps + 1,
                    lapses = lapses + 1,
                    scheduledInterval = interval
                )
            }
            Rating.Hard -> {
                val newS = if (isSameDay) {
                    shortTermStability(stability, Rating.Hard)
                } else {
                    nextStabilityAfterRecall(difficulty, stability, r, Rating.Hard)
                }
                val intervalDays = nextInterval(newS)
                val fuzzedDays = applyFuzz(intervalDays)
                val interval = fuzzedDays * DAY_MS
                return SchedulingResult(
                    state = CardState.Review,
                    step = null,
                    stability = newS,
                    difficulty = newD,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = reps + 1,
                    lapses = lapses,
                    scheduledInterval = interval
                )
            }
            Rating.Good -> {
                val newS = if (isSameDay) {
                    shortTermStability(stability, Rating.Good)
                } else {
                    nextStabilityAfterRecall(difficulty, stability, r, Rating.Good)
                }
                val intervalDays = nextInterval(newS)
                val fuzzedDays = applyFuzz(intervalDays)
                val interval = fuzzedDays * DAY_MS
                return SchedulingResult(
                    state = CardState.Review,
                    step = null,
                    stability = newS,
                    difficulty = newD,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = reps + 1,
                    lapses = lapses,
                    scheduledInterval = interval
                )
            }
            Rating.Easy -> {
                val newS = if (isSameDay) {
                    shortTermStability(stability, Rating.Easy)
                } else {
                    nextStabilityAfterRecall(difficulty, stability, r, Rating.Easy)
                }
                val intervalDays = nextInterval(newS)
                val fuzzedDays = applyFuzz(intervalDays)
                val interval = max(fuzzedDays * DAY_MS, DAY_MS)
                return SchedulingResult(
                    state = CardState.Review,
                    step = null,
                    stability = newS,
                    difficulty = newD,
                    due = now + interval,
                    lastReviewAt = now,
                    reps = reps + 1,
                    lapses = lapses,
                    scheduledInterval = interval
                )
            }
        }
    }

    // --- Core FSRS formulas ---

    /**
     * Power-law forgetting curve: probability of recall after [elapsedDays] with given [stability].
     */
    internal fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability < FsrsConstants.STABILITY_MIN) return 0.0
        return (1.0 + FsrsConstants.FACTOR * elapsedDays / stability).pow(FsrsConstants.DECAY)
    }

    /** S0(G) = w[G-1] */
    internal fun initialStability(rating: Rating): Double {
        return max(params[rating.value - 1], FsrsConstants.STABILITY_MIN)
    }

    /** D0(G) = w4 - e^(w5*(G-1)) + 1, clamped to [1, 10] */
    internal fun initialDifficulty(rating: Rating): Double {
        val d = params[4] - exp(params[5] * (rating.value - 1)) + 1
        return d.coerceIn(1.0, 10.0)
    }

    /**
     * Stability after successful recall (Hard/Good/Easy).
     * S'r = S * (1 + e^w8 * (11-D) * S^(-w9) * (e^(w10*(1-R)) - 1) * hard_penalty * easy_bonus)
     */
    internal fun nextStabilityAfterRecall(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.Hard) params[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) params[16] else 1.0

        val newS = s * (1 + exp(params[8]) *
                (11 - d) *
                s.pow(-params[9]) *
                (exp(params[10] * (1 - r)) - 1) *
                hardPenalty *
                easyBonus)

        return max(newS, FsrsConstants.STABILITY_MIN)
    }

    /**
     * Stability after forgetting (Again).
     * S'f = w11 * D^(-w12) * ((S+1)^w13 - 1) * e^(w14*(1-R))
     */
    internal fun nextStabilityAfterForget(d: Double, s: Double, r: Double): Double {
        val newS = params[11] *
                d.pow(-params[12]) *
                ((s + 1).pow(params[13]) - 1) *
                exp(params[14] * (1 - r))

        return max(newS, FsrsConstants.STABILITY_MIN)
    }

    /**
     * Update difficulty:
     * delta_D = -w6 * (G - 3)
     * D' = D + delta_D * (10 - D) / 9     // linear damping
     * D'' = w7 * D0(4) + (1 - w7) * D'    // mean reversion
     * clamped to [1, 10]
     */
    internal fun nextDifficulty(d: Double, rating: Rating): Double {
        val deltaD = -params[6] * (rating.value - 3)
        val dPrime = d + deltaD * (10 - d) / 9
        val d0Easy = params[4] - exp(params[5] * 3) + 1 // D0(Easy=4)
        val dDoublePrime = params[7] * d0Easy + (1 - params[7]) * dPrime
        return dDoublePrime.coerceIn(1.0, 10.0)
    }

    /**
     * Compute optimal interval in days from stability and desired retention.
     * I(S, r) = (S / FACTOR) * (r^(1/DECAY) - 1)
     */
    internal fun nextInterval(stability: Double): Long {
        val interval = (stability / FsrsConstants.FACTOR) *
                (desiredRetention.pow(1.0 / FsrsConstants.DECAY) - 1)
        return interval.roundToLong().coerceIn(1, maxInterval.toLong())
    }

    /**
     * Apply fuzz to Review intervals >= 2.5 days.
     * Fuzz factor: 2.5-7d → 15%, 7-20d → 10%, 20+d → 5%
     */
    internal fun applyFuzz(intervalDays: Long): Long {
        if (!enableFuzz || intervalDays < 3) return intervalDays

        val fuzzFactor = when {
            intervalDays < 7 -> 0.15
            intervalDays < 20 -> 0.10
            else -> 0.05
        }

        val delta = (intervalDays * fuzzFactor).toLong().coerceAtLeast(1)
        val minIvl = intervalDays - delta
        val maxIvl = intervalDays + delta

        return (minIvl..maxIvl).random().coerceAtLeast(1)
    }

    /**
     * Short-term (same-day) review stability update.
     * SInc = e^(w17 * (G - 3 + w18))
     * S' = S * SInc
     */
    internal fun shortTermStability(s: Double, rating: Rating): Double {
        val sInc = exp(params[17] * (rating.value - 3 + params[18]))
        return max(s * sInc, FsrsConstants.STABILITY_MIN)
    }
}

/**
 * Format an interval in milliseconds to a human-readable Chinese string.
 */
fun formatInterval(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 60 -> "${minutes}分"
        minutes < 1440 -> "${minutes / 60}时"
        minutes < 43200 -> "${minutes / 1440}天"
        else -> {
            val months = minutes / 43200.0
            if (months == months.toLong().toDouble()) {
                "${months.toLong()}月"
            } else {
                "${"%.1f".format(months)}月"
            }
        }
    }
}
