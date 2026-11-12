package io.komune.fixers.gradle.plugin.publish

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * The one pause every upload of a run shares.
 *
 * A server that rate limits counts requests, not files: an upload that backs off alone while
 * its siblings keep sending only spends its own retries. So the first transient failure of an
 * *epoch* closes the gate for everyone, and the failures of the uploads that were already in
 * flight wait on that same pause instead of starting their own. A 429 also halves how many
 * uploads run at once. The pauses add up against [PausePolicy.budgetMillis]; past it the
 * gate stays shut and the remaining files are reported as not attempted.
 */
internal class UploadGate(
    private val policy: PausePolicy,
    private val clock: UploadClock,
    private val random: Random,
    concurrency: Int,
) {
    private val mutex = Mutex()
    private var opened = CompletableDeferred(Unit)
    private var epoch = 0
    private var escalations = 0
    private var pausedMillis = 0L
    private val limit = AtomicInteger(concurrency)

    @Volatile
    var exhaustion: String? = null
        private set

    val concurrency: Int get() = limit.get()

    /** Waits while the gate is shut; returns the epoch the upload starts in, or `null` once given up. */
    suspend fun awaitOpen(): Int? {
        while (exhaustion == null) {
            val (current, gate) = mutex.withLock { epoch to opened }
            if (gate.isCompleted) return current
            gate.await()
        }
        return null
    }

    suspend fun onSuccess(startedIn: Int) {
        mutex.withLock { if (startedIn == epoch) escalations = 0 }
    }

    /**
     * An upload started in [startedIn] failed transiently. Pauses every upload unless a sibling's
     * failure already did; returns `false` when the budget forbids another try.
     */
    suspend fun onTransientFailure(error: HttpPutResult.Error, startedIn: Int, label: String): Boolean {
        val decision = mutex.withLock {
            when {
                exhaustion != null -> GiveUp
                startedIn != epoch -> AlreadyPaused
                else -> nextPause(error) ?: GiveUp
            }
        }
        if (decision is Pause) pauseEveryUpload(decision, error, label)
        return decision != GiveUp
    }

    private suspend fun pauseEveryUpload(pause: Pause, error: HttpPutResult.Error, label: String) {
        logger.lifecycle(
            "  ⏸ ${error.describe()} on $label: pausing every upload for ${formatDuration(pause.millis)} " +
                "(${pause.source}), $concurrency at once from now on; " +
                "${formatDuration(pausedMillis)} of the ${formatDuration(policy.budgetMillis)} pause budget used"
        )
        clock.sleep(pause.millis)
        mutex.withLock { opened.complete(Unit) }
    }

    private sealed interface Decision
    private data object GiveUp : Decision
    private data object AlreadyPaused : Decision
    private data class Pause(val millis: Long, val source: String) : Decision

    // Called under the lock. Shuts the gate and returns the pause to take, or records why not.
    private fun nextPause(error: HttpPutResult.Error): Pause? {
        val requested = retryAfterMillis(error.retryAfter, clock.nowMillis())
        val pause = requested?.let { Pause(it, "Retry-After") } ?: Pause(backoff(error), "backoff")
        val remaining = policy.budgetMillis - pausedMillis
        if (pause.millis > remaining) {
            exhaustion = if (requested != null) {
                "the server's Retry-After of ${formatDuration(requested)} exceeds the " +
                    "${formatDuration(remaining)} left of the pause budget"
            } else {
                "pause budget of ${formatDuration(policy.budgetMillis)} exhausted " +
                    "(${formatDuration(pausedMillis)} paused, last answer ${error.describe()})"
            }
            opened.complete(Unit)
            return null
        }
        epoch++
        escalations++
        pausedMillis += pause.millis
        if (error.code == HTTP_TOO_MANY_REQUESTS) limit.updateAndGet { maxOf(a = 1, b = it / 2) }
        opened = CompletableDeferred()
        return pause
    }

    private fun backoff(error: HttpPutResult.Error): Long {
        val initial = if (error.code == HTTP_TOO_MANY_REQUESTS) {
            policy.rateLimitInitialPauseMillis
        } else {
            policy.transientInitialPauseMillis
        }
        val doubled = initial.toDouble() * (1L shl minOf(a = escalations, b = MAX_DOUBLINGS))
        val jittered = doubled * (1 + random.nextDouble() * policy.jitterRatio)
        return minOf(a = jittered.toLong(), b = policy.maxPauseMillis)
    }

    private companion object {
        val logger: Logger = Logging.getLogger(UploadGate::class.java)
        const val MAX_DOUBLINGS = 30
    }
}

internal const val HTTP_TOO_MANY_REQUESTS = 429
private const val MILLIS_PER_SECOND = 1_000L

// Status reported for an upload that never got an HTTP response (connect timeout, reset, ...).
internal const val NO_HTTP_RESPONSE = 0

internal fun HttpPutResult.Error.describe(): String =
    if (code == NO_HTTP_RESPONSE) "I/O error" else "HTTP $code"

/**
 * The wait a `Retry-After` header asks for, in milliseconds from [nowMillis]: delay-seconds or
 * an HTTP-date (RFC 9110 §10.2.3). `null` when the header is absent or unreadable.
 */
internal fun retryAfterMillis(header: String?, nowMillis: Long): Long? {
    val value = header?.trim().orEmpty()
    val seconds = value.toLongOrNull()
    return when {
        value.isEmpty() -> null
        seconds != null -> seconds.coerceAtLeast(0) * MILLIS_PER_SECOND
        else -> parseHttpDate(value)?.let { (it - nowMillis).coerceAtLeast(0) }
    }
}

private fun parseHttpDate(value: String): Long? = try {
    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
} catch (_: DateTimeParseException) {
    null
}

internal fun formatDuration(millis: Long): String {
    val duration = Duration.ofMillis(millis)
    return when {
        duration.toHours() > 0 -> "${duration.toHours()}h${duration.toMinutesPart()}m${duration.toSecondsPart()}s"
        duration.toMinutes() > 0 -> "${duration.toMinutes()}m${duration.toSecondsPart()}s"
        else -> "${duration.toSeconds()}s"
    }
}
