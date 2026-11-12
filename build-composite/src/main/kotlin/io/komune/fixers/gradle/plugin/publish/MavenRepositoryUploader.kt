package io.komune.fixers.gradle.plugin.publish

import java.io.File
import java.util.Base64
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Fluent builder for uploading artifacts from a local staging directory
 * to any Maven repository (GitHub Packages, Maven Central Snapshots, etc.),
 * skipping files that already exist (HTTP 409 Conflict).
 *
 * Every `maven-metadata.xml` (and its checksums) goes last, and only once every other file
 * is in: the metadata is what points resolvers at a new snapshot, so a run that stops early
 * leaves the repository serving what it served before. Transient failures pause every
 * upload together (see [UploadGate]).
 */
object MavenRepositoryUploader {

    private const val MAX_CONCURRENT_UPLOADS = 10

    // Enough tries for a file to outlast the pauses the budget allows (six from 30s, doubling
    // to the 5 min cap, fit in 20 min); the budget, not this count, is what normally stops a run.
    private const val DEFAULT_MAX_ATTEMPTS = 10

    private val logger: Logger = Logging.getLogger(MavenRepositoryUploader::class.java)

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
        private var pausePolicy: PausePolicy = PausePolicy()
        private var clock: UploadClock = UploadClock.SYSTEM
        private var random: Random = Random.Default

        fun from(dir: File) = apply { stagingDir = dir }
        fun atUrl(url: String) = apply { baseUrl = url }
        fun withCredentials(user: String, tok: String) = apply { username = user; token = tok }
        fun withHttpClient(client: HttpPutClient) = apply { httpClient = client }
        fun withConcurrency(n: Int) = apply { concurrency = n }

        /** [attempts] per file; [backoffMillis], when given, is the first pause after any transient failure. */
        fun withRetry(attempts: Int, backoffMillis: Long? = null) = apply {
            maxAttempts = attempts
            if (backoffMillis != null) {
                pausePolicy = pausePolicy.copy(
                    rateLimitInitialPauseMillis = backoffMillis,
                    transientInitialPauseMillis = backoffMillis,
                )
            }
        }

        internal fun withPausePolicy(policy: PausePolicy) = apply { pausePolicy = policy }
        internal fun withClock(uploadClock: UploadClock, jitter: Random = Random.Default) =
            apply { clock = uploadClock; random = jitter }

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

            val files = stagingDir.walkTopDown().filter { it.isFile }
                .sortedBy { it.relativeTo(stagingDir).path }
                .toList()
            val (metadata, artifacts) = files.partition { it.name.startsWith(MAVEN_METADATA) }
            logger.lifecycle(
                "Uploading ${files.size} files to $repoName (concurrency: $concurrency, " +
                    "${metadata.size} maven-metadata file(s) last)"
            )

            val run = UploadRun(
                target = UploadRun.Target(stagingDir = stagingDir, baseUrl = baseUrl, authHeader = authHeader),
                httpClient = httpClient,
                gate = UploadGate(policy = pausePolicy, clock = clock, random = random, concurrency = concurrency),
                maxAttempts = maxAttempts,
                tally = UploadTally(files.size),
            )
            val isMetadataSent = runBlocking(dispatcher) {
                run.uploadAll(artifacts)
                run.tally.isClean.also { clean ->
                    if (clean) run.uploadAll(metadata) else run.holdBack(metadata)
                }
            }

            val summary = run.tally.summary()
            logger.lifecycle(
                "$repoName upload: ${summary.uploaded} uploaded, " +
                    "${summary.skipped} already existed, ${summary.failed} failed, " +
                    "${summary.notAttempted} not attempted"
            )
            if (summary.failed + summary.notAttempted > 0) {
                throw GradleException(
                    failureReport(
                        summary = summary,
                        totalFiles = files.size,
                        metadata = MetadataOutcome(
                            total = metadata.size,
                            isSent = isMetadataSent,
                            uploaded = run.tally.uploadedMetadataCount,
                        ),
                        exhaustion = run.exhaustion,
                    )
                )
            }
            return summary
        }

        private data class MetadataOutcome(val total: Int, val isSent: Boolean, val uploaded: Int)

        private fun failureReport(
            summary: UploadSummary,
            totalFiles: Int,
            metadata: MetadataOutcome,
            exhaustion: String?,
        ): String = buildString {
            append("$repoName upload failed for ${summary.failed + summary.notAttempted} file(s): ")
            append("${summary.uploaded} of $totalFiles file(s) uploaded, ${summary.skipped} already existed, ")
            append("${summary.failed} failed, ${summary.notAttempted} not attempted.")
            exhaustion?.let { append("\nStopped: $it.") }
            when {
                metadata.total == 0 -> Unit
                !metadata.isSent -> append(
                    "\nNo maven-metadata.xml was uploaded, so resolvers still get what this repository " +
                        "served before this run; the files uploaded are not referenced yet. Rerun the publication."
                )
                metadata.uploaded < metadata.total -> append(
                    "\nThe repository is partially published: ${metadata.uploaded} of ${metadata.total} " +
                        "maven-metadata file(s) uploaded, so some modules resolve to this run and others " +
                        "to the previous one. Rerun the publication."
                )
            }
            if (summary.errors.isNotEmpty()) {
                append("\n")
                append(summary.errors.joinToString("\n") { "  - $it" })
            }
        }
    }

    data class UploadSummary(
        val uploaded: Int,
        val skipped: Int,
        val failed: Int,
        val errors: List<String>,
        val notAttempted: Int = 0,
    )
}
