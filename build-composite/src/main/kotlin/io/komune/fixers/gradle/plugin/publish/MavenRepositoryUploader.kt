package io.komune.fixers.gradle.plugin.publish

import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging

/**
 * Fluent builder for uploading artifacts from a local staging directory
 * to any Maven repository (GitHub Packages, Maven Central Snapshots, etc.),
 * skipping files that already exist (HTTP 409 Conflict).
 */
object MavenRepositoryUploader {

    private const val MAX_CONCURRENT_UPLOADS = 10

    private val logger = Logging.getLogger(MavenRepositoryUploader::class.java)

    private const val DEFAULT_MAX_ATTEMPTS = 3
    private const val DEFAULT_INITIAL_BACKOFF_MS = 1_000L
    private const val BACKOFF_FACTOR = 2
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
    private const val HTTP_LAST_SERVER_ERROR = 599

    /** Status reported for an upload that never got an HTTP response (connect timeout, reset, ...). */
    private const val NO_HTTP_RESPONSE = 0

    /**
     * A repository that is briefly overloaded, rate-limiting or unreachable should not abort
     * a release that is otherwise fine. Everything else (auth, bad request, 409) is a hard answer
     * and is returned as-is.
     */
    private fun HttpPutResult.Error.isTransient(): Boolean =
        code == NO_HTTP_RESPONSE ||
            code == HTTP_TOO_MANY_REQUESTS ||
            code in HTTP_SERVER_ERROR..HTTP_LAST_SERVER_ERROR

    private fun HttpPutResult.Error.describe(): String =
        if (code == NO_HTTP_RESPONSE) "I/O error" else "HTTP $code"

    fun toRepository(repoName: String): Builder = Builder(repoName)

    class Builder(
        private val repoName: String,
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        private var stagingDir: File? = null
        private var baseUrl: String? = null
        private var username: String? = null
        private var token: String? = null
        private var httpClient: HttpPutClient = DefaultHttpPutClient()
        private var concurrency: Int = MAX_CONCURRENT_UPLOADS
        private var maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
        private var initialBackoffMillis: Long = DEFAULT_INITIAL_BACKOFF_MS

        fun from(dir: File) = apply { stagingDir = dir }
        fun atUrl(url: String) = apply { baseUrl = url }
        fun withCredentials(user: String, tok: String) = apply { username = user; token = tok }
        fun withHttpClient(client: HttpPutClient) = apply { httpClient = client }
        fun withConcurrency(n: Int) = apply { concurrency = n }
        fun withRetry(attempts: Int, backoffMillis: Long = DEFAULT_INITIAL_BACKOFF_MS) =
            apply { maxAttempts = attempts; initialBackoffMillis = backoffMillis }

        /**
         * Uploads a single file, retrying transient failures with exponential backoff.
         * An I/O failure (connect timeout, connection reset, ...) counts as transient too.
         * Returns the last result once the attempts are exhausted, so a persistent failure
         * lands in the summary instead of cancelling the sibling uploads.
         */
        private suspend fun putWithRetry(url: String, authHeader: String, file: File, label: String): HttpPutResult {
            var backoff = initialBackoffMillis
            var attempt = 1
            while (true) {
                val result = try {
                    httpClient.put(url, authHeader, file)
                } catch (e: IOException) {
                    HttpPutResult.Error(NO_HTTP_RESPONSE, e.toString())
                }
                if (result !is HttpPutResult.Error || !result.isTransient() || attempt >= maxAttempts) {
                    return result
                }
                logger.lifecycle(
                    "  ↻ [$label] ${result.describe()}, retrying in ${backoff}ms " +
                        "(attempt ${attempt + 1}/$maxAttempts)"
                )
                delay(backoff)
                backoff *= BACKOFF_FACTOR
                attempt++
            }
        }

        fun upload(): UploadSummary {
            val stagingDir = checkNotNull(stagingDir) { "from() was not called" }
            val baseUrl = checkNotNull(baseUrl) { "atUrl() was not called" }
            val username = checkNotNull(username) { "withCredentials() was not called" }
            val token = checkNotNull(token) { "withCredentials() was not called" }
            if (!stagingDir.exists() || stagingDir.listFiles()?.isEmpty() != false) {
                logger.lifecycle("No artifacts found in staging directory: $stagingDir")
                return UploadSummary(uploaded = 0, skipped = 0, failed = 0, errors = emptyList())
            }

            val authHeader = "Basic " + Base64.getEncoder()
                .encodeToString("$username:$token".toByteArray())

            val files = stagingDir.walkTopDown().filter { it.isFile }.toList()
            val totalFiles = files.size
            logger.lifecycle("Uploading $totalFiles files to $repoName (concurrency: $concurrency)")

            val uploaded = AtomicInteger(0)
            val skipped = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val completed = AtomicInteger(0)
            val errors = ConcurrentLinkedQueue<String>()

            val semaphore = Semaphore(concurrency)

            runBlocking(dispatcher) {
                files.map { file ->
                    async {
                        semaphore.withPermit {
                            val relativePath = file.relativeTo(stagingDir).path
                            val url = "${baseUrl.trimEnd('/')}/$relativePath"
                            val result = putWithRetry(url, authHeader, file, relativePath)
                            val progress = "${completed.incrementAndGet()}/$totalFiles"
                            when (result) {
                                HttpPutResult.Success -> {
                                    uploaded.incrementAndGet()
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
                    }
                }.awaitAll()
            }

            val summary = UploadSummary(
                uploaded = uploaded.get(),
                skipped = skipped.get(),
                failed = failed.get(),
                errors = errors.toList(),
            )

            logger.lifecycle(
                "$repoName upload: ${summary.uploaded} uploaded, " +
                    "${summary.skipped} already existed, ${summary.failed} failed"
            )

            if (summary.errors.isNotEmpty()) {
                throw GradleException(
                    "$repoName upload failed for ${summary.errors.size} file(s):\n" +
                        summary.errors.joinToString("\n") { "  - $it" }
                )
            }

            return summary
        }
    }

    data class UploadSummary(
        val uploaded: Int,
        val skipped: Int,
        val failed: Int,
        val errors: List<String>,
    )
}
