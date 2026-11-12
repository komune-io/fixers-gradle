package io.komune.fixers.gradle.plugin.publish

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

internal const val MAVEN_METADATA = "maven-metadata.xml"

private const val HTTP_SERVER_ERROR = 500
private const val HTTP_LAST_SERVER_ERROR = 599

// A repository that is briefly overloaded, rate-limiting or unreachable should not abort
// a release that is otherwise fine. Everything else (auth, bad request, 409) is a hard answer
// and is returned as-is.
private fun HttpPutResult.Error.isTransient(): Boolean =
    code == NO_HTTP_RESPONSE ||
        code == HTTP_TOO_MANY_REQUESTS ||
        code in HTTP_SERVER_ERROR..HTTP_LAST_SERVER_ERROR

/** One upload of a staging directory: the files go through [gate], their outcomes into [tally]. */
internal class UploadRun(
    private val target: Target,
    private val httpClient: HttpPutClient,
    private val gate: UploadGate,
    private val maxAttempts: Int,
    val tally: UploadTally,
) {
    data class Target(val stagingDir: File, val baseUrl: String, val authHeader: String)

    val exhaustion: String? get() = gate.exhaustion

    fun holdBack(files: List<File>) = files.forEach { tally.notAttempted(it.relativeTo(target.stagingDir).path) }

    // A worker whose slot is past the gate's lowered concurrency hands its file back and stops.
    // A file handed back after the last worker of a round ended goes out in the next round.
    suspend fun uploadAll(files: List<File>) {
        val queue = ConcurrentLinkedQueue(files)
        while (queue.isNotEmpty()) {
            coroutineScope {
                repeat(gate.concurrency) { slot ->
                    launch { work(slot, queue) }
                }
            }
        }
    }

    private suspend fun work(slot: Int, queue: ConcurrentLinkedQueue<File>) {
        var file = queue.poll()
        while (file != null) {
            if (upload(file, slot)) {
                file = queue.poll()
            } else {
                queue.add(file)
                file = null
            }
        }
    }

    // Returns `false` when this worker retired before the file was settled.
    private suspend fun upload(file: File, slot: Int): Boolean {
        val relativePath = file.relativeTo(target.stagingDir).path
        val url = "${target.baseUrl.trimEnd('/')}/$relativePath"
        val outcome = putWithRetry(item = Item(url = url, file = file, label = relativePath), slot = slot)
        when (outcome) {
            is Outcome.Sent -> tally.record(outcome.result, relativePath)
            Outcome.NotSent -> tally.notAttempted(relativePath)
            Outcome.Retired -> Unit
        }
        return outcome != Outcome.Retired
    }

    private data class Item(val url: String, val file: File, val label: String)

    private sealed interface Outcome {
        data class Sent(val result: HttpPutResult) : Outcome
        data object NotSent : Outcome
        data object Retired : Outcome
    }

    // Uploads a single file, retrying transient failures behind the shared gate. An I/O failure
    // (connect timeout, connection reset, ...) counts as transient too. A transient failure on the
    // last attempt still pauses the other uploads. Returns the last result once the attempts are
    // exhausted, so a persistent failure lands in the summary instead of cancelling the siblings.
    private tailrec suspend fun putWithRetry(
        item: Item,
        slot: Int,
        attempt: Int = 1,
        last: HttpPutResult? = null,
    ): Outcome {
        val opening = gate.awaitOpen(slot)
        if (opening !is UploadGate.Opening.Open) return closedOutcome(opening, last)
        val result = put(item.url, item.file)
        val isRetried = when {
            result !is HttpPutResult.Error -> {
                gate.onSuccess(opening.epoch)
                false
            }
            !result.isTransient() -> {
                false
            }
            else -> {
                gate.onTransientFailure(error = result, startedIn = opening.epoch, label = item.label) &&
                    attempt < maxAttempts
            }
        }
        if (isRetried) logger.info("  ↻ [${item.label}] retrying, attempt ${attempt + 1}/$maxAttempts")
        return if (isRetried) {
            putWithRetry(item = item, slot = slot, attempt = attempt + 1, last = result)
        } else {
            Outcome.Sent(result)
        }
    }

    private fun closedOutcome(opening: UploadGate.Opening, last: HttpPutResult?): Outcome = when {
        opening == UploadGate.Opening.Retire -> Outcome.Retired
        last != null -> Outcome.Sent(last)
        else -> Outcome.NotSent
    }

    private fun put(url: String, file: File): HttpPutResult = try {
        httpClient.put(url, target.authHeader, file)
    } catch (e: IOException) {
        HttpPutResult.Error(NO_HTTP_RESPONSE, e.toString())
    }

    private companion object {
        val logger: Logger = Logging.getLogger(UploadRun::class.java)
    }
}

/** What the concurrent uploads of one run gave, logged as each one ends. */
internal class UploadTally(private val totalFiles: Int) {
    private val completed = AtomicInteger(0)
    private val uploaded = AtomicInteger(0)
    private val uploadedMetadata = AtomicInteger(0)
    private val skipped = AtomicInteger(0)
    private val failed = AtomicInteger(0)
    private val notAttempted = AtomicInteger(0)
    private val errors = ConcurrentLinkedQueue<String>()

    val uploadedMetadataCount: Int get() = uploadedMetadata.get()
    val isClean: Boolean get() = failed.get() == 0 && notAttempted.get() == 0

    fun record(result: HttpPutResult, relativePath: String) {
        val progress = "${completed.incrementAndGet()}/$totalFiles"
        when (result) {
            HttpPutResult.Success -> {
                uploaded.incrementAndGet()
                if (File(relativePath).name.startsWith(MAVEN_METADATA)) uploadedMetadata.incrementAndGet()
                logger.lifecycle("  ✔ [$progress] Uploaded: $relativePath")
            }
            HttpPutResult.Conflict -> {
                skipped.incrementAndGet()
                logger.lifecycle("  ⚠ [$progress] Already exists, skipping: $relativePath")
            }
            is HttpPutResult.Error -> {
                failed.incrementAndGet()
                logger.lifecycle("  ✘ [$progress] Failed: $relativePath → ${result.describe()}")
                errors.add("$relativePath → ${result.describe()}: ${result.message}")
            }
        }
    }

    fun notAttempted(relativePath: String) {
        completed.incrementAndGet()
        notAttempted.incrementAndGet()
        logger.info("  ⏭ Not attempted: $relativePath")
    }

    fun summary() = MavenRepositoryUploader.UploadSummary(
        uploaded = uploaded.get(),
        skipped = skipped.get(),
        failed = failed.get(),
        errors = errors.toList(),
        notAttempted = notAttempted.get(),
    )

    private companion object {
        val logger: Logger = Logging.getLogger(UploadTally::class.java)
    }
}
