package io.komune.fixers.gradle.plugin.publish

import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.gradle.api.GradleException

/**
 * Fluent builder for uploading artifacts from a local staging directory
 * to any Maven repository (GitHub Packages, Maven Central Snapshots, etc.),
 * skipping files that already exist (HTTP 409 Conflict).
 */
object MavenRepositoryUploader {

    private const val MAX_CONCURRENT_UPLOADS = 10

    fun to(repoName: String): Builder = Builder(repoName)

    class Builder(private val repoName: String) {
        private lateinit var stagingDir: File
        private lateinit var baseUrl: String
        private lateinit var username: String
        private lateinit var token: String
        private var httpClient: HttpPutClient = DefaultHttpPutClient()
        private var concurrency: Int = MAX_CONCURRENT_UPLOADS

        fun from(dir: File) = apply { stagingDir = dir }
        fun at(url: String) = apply { baseUrl = url }
        fun withCredentials(user: String, tok: String) = apply { username = user; token = tok }
        fun withHttpClient(client: HttpPutClient) = apply { httpClient = client }
        fun withConcurrency(n: Int) = apply { concurrency = n }

        fun upload(): UploadSummary {
            if (!stagingDir.exists() || stagingDir.listFiles()?.isEmpty() != false) {
                println("No artifacts found in staging directory: $stagingDir")
                return UploadSummary(uploaded = 0, skipped = 0, failed = 0, errors = emptyList())
            }

            val authHeader = "Basic " + Base64.getEncoder()
                .encodeToString("$username:$token".toByteArray())

            val files = stagingDir.walkTopDown().filter { it.isFile }.toList()
            val totalFiles = files.size
            println("Uploading $totalFiles files to $repoName (concurrency: $concurrency)")

            val uploaded = AtomicInteger(0)
            val skipped = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val completed = AtomicInteger(0)
            val errors = java.util.concurrent.ConcurrentLinkedQueue<String>()

            val semaphore = Semaphore(concurrency)

            runBlocking(Dispatchers.IO) {
                files.map { file ->
                    async {
                        semaphore.withPermit {
                            val relativePath = file.relativeTo(stagingDir).path
                            val url = "${baseUrl.trimEnd('/')}/$relativePath"
                            val result = httpClient.put(url, authHeader, file)
                            val progress = "${completed.incrementAndGet()}/$totalFiles"
                            when (result) {
                                HttpPutResult.Success -> {
                                    uploaded.incrementAndGet()
                                    println("  ✔ [$progress] Uploaded: $relativePath")
                                }
                                HttpPutResult.Conflict -> {
                                    skipped.incrementAndGet()
                                    println("  ⚠ [$progress] Already exists, skipping: $relativePath")
                                }
                                is HttpPutResult.Error -> {
                                    failed.incrementAndGet()
                                    println("  ✘ [$progress] Failed: $relativePath → HTTP ${result.code}")
                                    errors.add("$relativePath → HTTP ${result.code}: ${result.message}")
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

            println("$repoName upload: ${summary.uploaded} uploaded, " +
                "${summary.skipped} already existed, ${summary.failed} failed")

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
