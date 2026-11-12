package io.komune.fixers.gradle.plugin.publish

import kotlinx.coroutines.delay

/** Time as the uploader sees it, so that tests can pause for minutes without waiting. */
internal interface UploadClock {
    fun nowMillis(): Long
    suspend fun sleep(millis: Long)

    /** Waits until [epochMillis]; how uploads are spaced from one another. */
    suspend fun sleepUntil(epochMillis: Long) {
        val millis = epochMillis - nowMillis()
        if (millis > 0) sleep(millis)
    }

    /** The pause every upload waits on after a transient failure. */
    suspend fun pause(millis: Long) = sleep(millis)

    companion object {
        val SYSTEM: UploadClock = object : UploadClock {
            override fun nowMillis(): Long = System.currentTimeMillis()
            override suspend fun sleep(millis: Long) = delay(millis)
        }
    }
}

/**
 * How long every upload of a run waits, together, after a transient failure.
 *
 * @property rateLimitInitialPauseMillis The first pause after an HTTP 429, and the least one:
 * Cloudflare, in front of Maven Central, answers its rate limiting (error 1015) with a default
 * `Retry-After` of 30 seconds. A longer `Retry-After` is honoured.
 * @property transientInitialPauseMillis The first pause after a 5xx or an I/O error.
 * @property maxPauseMillis The longest computed pause; a server's `Retry-After` may ask for more.
 * @property rateLimitBudgetMillis The total pause after 429s a run accepts before it gives up.
 * @property transientBudgetMillis The same for 5xx and I/O errors: a server that keeps failing is
 * not going to recover within a publication.
 * @property jitterRatio A computed pause is lengthened by a random fraction up to this ratio.
 * @property rateLimitedMinIntervalMillis The spacing between upload starts after a 429, when the
 * repository had none; an existing spacing doubles instead.
 * @property maxRequestIntervalMillis The widest spacing a 429 can push the uploads to: one start a
 * second. Assumed, not documented: if Maven Central counts over a window of ten minutes or more,
 * even this pace can keep tripping it and the run ends on [rateLimitBudgetMillis]; the knob is
 * `fixers.publish.maven.snapshots.minRequestIntervalMillis`, which sets the pace from the start.
 */
internal data class PausePolicy(
    val rateLimitInitialPauseMillis: Long = DEFAULT_RATE_LIMIT_INITIAL_PAUSE_MS,
    val transientInitialPauseMillis: Long = DEFAULT_TRANSIENT_INITIAL_PAUSE_MS,
    val maxPauseMillis: Long = DEFAULT_MAX_PAUSE_MS,
    val rateLimitBudgetMillis: Long = DEFAULT_RATE_LIMIT_BUDGET_MS,
    val transientBudgetMillis: Long = DEFAULT_TRANSIENT_BUDGET_MS,
    val jitterRatio: Double = DEFAULT_JITTER_RATIO,
    val rateLimitedMinIntervalMillis: Long = DEFAULT_RATE_LIMITED_MIN_INTERVAL_MS,
    val maxRequestIntervalMillis: Long = DEFAULT_MAX_REQUEST_INTERVAL_MS,
) {
    private companion object {
        const val DEFAULT_RATE_LIMIT_INITIAL_PAUSE_MS = 30_000L
        const val DEFAULT_TRANSIENT_INITIAL_PAUSE_MS = 1_000L
        const val DEFAULT_MAX_PAUSE_MS = 300_000L
        const val DEFAULT_RATE_LIMIT_BUDGET_MS = 1_200_000L
        const val DEFAULT_TRANSIENT_BUDGET_MS = 120_000L
        const val DEFAULT_JITTER_RATIO = 0.25
        const val DEFAULT_RATE_LIMITED_MIN_INTERVAL_MS = 250L
        const val DEFAULT_MAX_REQUEST_INTERVAL_MS = 1_000L
    }
}
