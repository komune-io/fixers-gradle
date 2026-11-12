package io.komune.fixers.gradle.plugin.publish

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * The one pause, and the one pace, every upload of a run shares.
 *
 * A server that rate limits counts requests, not files: an upload that backs off alone while
 * its siblings keep sending only spends its own retries. So upload starts are spaced by
 * [minRequestIntervalMillis] from the first one, and the first transient failure of an *epoch*
 * closes the gate for everyone; the failures of the uploads that were already in flight wait on
 * that same pause instead of starting their own. A failure that arrives after its epoch's pause
 * is over is retried at once (one attempt spent) and pauses again only if it repeats.
 *
 * A 429 also halves how many uploads run at once and doubles the spacing. A success in the
 * current epoch resets the pause length to its initial value, not the concurrency nor the
 * spacing: against a server that refuses some uploads and accepts others, pauses stay short while
 * the pace keeps slowing. The pauses add up against [PausePolicy.rateLimitBudgetMillis] for 429s
 * and [PausePolicy.transientBudgetMillis] for 5xx and I/O errors; past either the gate stays shut
 * and the remaining files are reported as not attempted.
 */
internal class UploadGate(
    private val policy: PausePolicy,
    private val clock: UploadClock,
    private val random: Random,
    concurrency: Int,
    private var minRequestIntervalMillis: Long = 0,
) {
    private val mutex = Mutex()
    private var opened = CompletableDeferred(Unit)
    private var epoch = 0
    private var escalations = 0
    private var rateLimitPausedMillis = 0L
    private var transientPausedMillis = 0L
    private var nextStartAt = Long.MIN_VALUE
    @Volatile
    private var limit = concurrency

    @Volatile
    var exhaustion: String? = null
        private set

    val concurrency: Int get() = limit

    /** What an upload may do once it asks the gate. */
    sealed interface Opening {
        /** Send now; the upload belongs to [epoch]. */
        data class Open(val epoch: Int) : Opening

        /** The run gave up: do not send. */
        data object GivenUp : Opening

        /** The concurrency dropped below this worker's slot: hand the file back and stop. */
        data object Retire : Opening
    }

    private sealed interface Step {
        data class Ready(val opening: Opening) : Step
        data class Shut(val gate: CompletableDeferred<Unit>) : Step
        data class Spaced(val until: Long, val epoch: Int) : Step
    }

    /** Waits while the gate is shut and until this upload's spaced start. */
    suspend fun awaitOpen(slot: Int): Opening {
        var opening: Opening? = null
        while (opening == null) {
            opening = when (val step = mutex.withLock { nextStep(slot) }) {
                is Step.Ready -> {
                    step.opening
                }
                is Step.Shut -> {
                    step.gate.await()
                    null
                }
                is Step.Spaced -> {
                    clock.sleepUntil(step.until)
                    mutex.withLock { stillOpen(step.epoch) }
                }
            }
        }
        return opening
    }

    // Called under the lock.
    private fun nextStep(slot: Int): Step = when {
        exhaustion != null -> {
            Step.Ready(Opening.GivenUp)
        }
        !opened.isCompleted -> {
            Step.Shut(opened)
        }
        slot >= limit -> {
            Step.Ready(Opening.Retire)
        }
        else -> {
            val now = clock.nowMillis()
            val start = maxOf(a = now, b = nextStartAt)
            nextStartAt = start + minRequestIntervalMillis
            if (start > now) Step.Spaced(until = start, epoch = epoch) else Step.Ready(Opening.Open(epoch))
        }
    }

    // Called under the lock, after a spaced wait: the gate may have shut meanwhile.
    private fun stillOpen(startedIn: Int): Opening? = when {
        exhaustion != null -> Opening.GivenUp
        opened.isCompleted && startedIn == epoch -> Opening.Open(startedIn)
        else -> null
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
                "(${pause.source}); from now on $concurrency at once, one start every " +
                "${minRequestIntervalMillis}ms; paused so far: ${formatDuration(rateLimitPausedMillis)} " +
                "for 429s, ${formatDuration(transientPausedMillis)} for 5xx and I/O errors"
        )
        clock.pause(pause.millis)
        mutex.withLock { opened.complete(Unit) }
    }

    private sealed interface Decision
    private data object GiveUp : Decision
    private data object AlreadyPaused : Decision
    private data class Pause(val millis: Long, val source: String) : Decision

    // Called under the lock. Shuts the gate and returns the pause to take, or records why not.
    // A Retry-After is a floor: 0 or a date already past still waits for the computed backoff.
    private fun nextPause(error: HttpPutResult.Error): Pause? {
        val isRateLimit = error.code == HTTP_TOO_MANY_REQUESTS
        val requested = retryAfterMillis(error.retryAfter, clock.nowMillis())
        val computed = backoff(isRateLimit)
        val pause = if (requested != null && requested >= computed) {
            Pause(requested, "Retry-After")
        } else {
            Pause(computed, "backoff")
        }
        val budget = if (isRateLimit) policy.rateLimitBudgetMillis else policy.transientBudgetMillis
        val paused = if (isRateLimit) rateLimitPausedMillis else transientPausedMillis
        val kind = if (isRateLimit) "rate-limit" else "5xx and I/O"
        if (pause.millis > budget - paused) {
            exhaustion = if (pause.source == "Retry-After") {
                "the server's Retry-After of ${formatDuration(pause.millis)} exceeds the " +
                    "${formatDuration(budget - paused)} left of the $kind pause budget"
            } else {
                "$kind pause budget of ${formatDuration(budget)} exhausted " +
                    "(${formatDuration(paused)} paused, last answer ${error.describe()})"
            }
            opened.complete(Unit)
            return null
        }
        epoch++
        escalations++
        if (isRateLimit) {
            rateLimitPausedMillis += pause.millis
            limit = maxOf(a = 1, b = limit / 2)
            minRequestIntervalMillis = minOf(
                a = policy.maxRequestIntervalMillis,
                b = maxOf(a = policy.rateLimitedMinIntervalMillis, b = minRequestIntervalMillis * 2),
            )
        } else {
            transientPausedMillis += pause.millis
        }
        opened = CompletableDeferred()
        return pause
    }

    private fun backoff(isRateLimit: Boolean): Long {
        val initial = if (isRateLimit) policy.rateLimitInitialPauseMillis else policy.transientInitialPauseMillis
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
 * an HTTP-date (RFC 9110 §10.2.3). `null` when the header is absent or unreadable; a value too
 * large to count in milliseconds saturates.
 */
internal fun retryAfterMillis(header: String?, nowMillis: Long): Long? {
    val value = header?.trim().orEmpty()
    val seconds = value.toLongOrNull()
    return when {
        value.isEmpty() -> null
        seconds != null && seconds > Long.MAX_VALUE / MILLIS_PER_SECOND -> Long.MAX_VALUE
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
