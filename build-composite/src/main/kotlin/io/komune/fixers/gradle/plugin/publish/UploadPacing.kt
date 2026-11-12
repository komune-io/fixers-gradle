package io.komune.fixers.gradle.plugin.publish

import kotlinx.coroutines.delay

/** Time as the uploader sees it, so that tests can pause for minutes without waiting. */
interface UploadClock {
    fun nowMillis(): Long
    suspend fun sleep(millis: Long)

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
 * @property rateLimitInitialPauseMillis The first pause after an HTTP 429 that carries no
 * `Retry-After`: Cloudflare, in front of Maven Central, answers its rate limiting (error 1015)
 * with a default `Retry-After` of 30 seconds.
 * @property transientInitialPauseMillis The first pause after a 5xx or an I/O error.
 * @property maxPauseMillis The longest computed pause; a server's `Retry-After` may ask for more.
 * @property budgetMillis The total pause a run accepts before it gives up on the remaining files.
 * @property jitterRatio A computed pause is lengthened by a random fraction up to this ratio.
 */
data class PausePolicy(
    val rateLimitInitialPauseMillis: Long = DEFAULT_RATE_LIMIT_INITIAL_PAUSE_MS,
    val transientInitialPauseMillis: Long = DEFAULT_TRANSIENT_INITIAL_PAUSE_MS,
    val maxPauseMillis: Long = DEFAULT_MAX_PAUSE_MS,
    val budgetMillis: Long = DEFAULT_BUDGET_MS,
    val jitterRatio: Double = DEFAULT_JITTER_RATIO,
) {
    private companion object {
        const val DEFAULT_RATE_LIMIT_INITIAL_PAUSE_MS = 30_000L
        const val DEFAULT_TRANSIENT_INITIAL_PAUSE_MS = 1_000L
        const val DEFAULT_MAX_PAUSE_MS = 300_000L
        const val DEFAULT_BUDGET_MS = 1_200_000L
        const val DEFAULT_JITTER_RATIO = 0.25
    }
}
