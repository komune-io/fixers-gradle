package io.komune.fixers.gradle.plugin.publish

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * How the uploads of one run behave against a server that rate limits or falters:
 * they pause together, on a clock the tests drive, and publish `maven-metadata.xml` last.
 */
class MavenRepositoryUploaderPacingTest {

    @TempDir
    lateinit var stagingDir: File

    /** Virtual time: [sleep] moves it forward instead of waiting, and records each pause. */
    class FakeClock(start: Long = 0) : UploadClock {
        private val now = AtomicLong(start)
        val pauses = ConcurrentLinkedQueue<Long>()
        val onSleep = ConcurrentLinkedQueue<() -> Unit>()

        override fun nowMillis(): Long = now.get()
        override suspend fun sleep(millis: Long) {
            pauses.add(millis)
            onSleep.forEach { it() }
            val target = now.get() + millis
            now.accumulateAndGet(target, ::maxOf)
            yield()
        }
    }

    /** A jitter source that never lengthens a pause. */
    object NoJitter : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    private data class Call(val path: String, val at: Long)

    /** Answers with [answer] and records, for each call, the path and the virtual time it started at. */
    private class RecordingClient(
        private val clock: UploadClock,
        private val answer: (path: String, call: Int) -> HttpPutResult,
    ) : HttpPutClient {
        val calls = ConcurrentLinkedQueue<Call>()
        private val count = AtomicInteger(0)

        override fun put(url: String, authHeader: String, file: File): HttpPutResult {
            val path = url.substringAfter("https://repo.example.com/")
            calls.add(Call(path, clock.nowMillis()))
            return answer(path, count.getAndIncrement())
        }
    }

    private fun uploader(client: HttpPutClient, clock: UploadClock, policy: PausePolicy = PausePolicy()) =
        MavenRepositoryUploader.toRepository("Test Repo")
            .from(stagingDir)
            .atUrl("https://repo.example.com")
            .withCredentials("user", "token")
            .withHttpClient(client)
            .withPausePolicy(policy)
            .withClock(clock, NoJitter)

    @Test
    fun `a 429 pauses every upload, not only the one that got it`() {
        val clock = FakeClock()
        val gateClosed = CountDownLatch(1)
        clock.onSleep.add { gateClosed.countDown() }
        val first429 = AtomicInteger(0)
        val client = RecordingClient(clock) { path, _ ->
            if (path == "a.jar" && first429.getAndIncrement() == 0) {
                HttpPutResult.Error(429, "error code: 1015")
            } else {
                // The sibling already in flight when the 429 lands finishes only once the pause began.
                if (path == "b.jar") gateClosed.await(2, TimeUnit.SECONDS)
                HttpPutResult.Success
            }
        }
        listOf("a.jar", "b.jar", "c.jar", "d.jar", "e.jar", "f.jar").forEach(::createFile)

        val summary = uploader(client, clock).withConcurrency(2).upload()

        assertThat(summary.uploaded).isEqualTo(6)
        assertThat(clock.pauses).containsExactly(30_000L)
        // Only the 429 and, when its worker had already started it, b.jar run before the pause ends.
        val beforeTheEnd = client.calls.filter { it.at < 30_000L }.map { it.path }
        assertThat(beforeTheEnd).contains("a.jar").isSubsetOf("a.jar", "b.jar")
    }

    @Test
    fun `failures of uploads already in flight pause once, not once each`() {
        val clock = FakeClock()
        val bothFailed = CountDownLatch(2)
        val client = RecordingClient(clock) { _, call ->
            if (call < 2) {
                bothFailed.countDown()
                bothFailed.await(2, TimeUnit.SECONDS)
                HttpPutResult.Error(429, "error code: 1015")
            } else {
                HttpPutResult.Success
            }
        }
        listOf("a.jar", "b.jar", "c.jar").forEach(::createFile)

        val summary = uploader(client, clock).withConcurrency(2).upload()

        assertThat(summary.uploaded).isEqualTo(3)
        assertThat(clock.pauses).containsExactly(30_000L)
    }

    @Test
    fun `Retry-After in seconds is the pause`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, call ->
            if (call == 0) HttpPutResult.Error(429, "slow down", retryAfter = "120") else HttpPutResult.Success
        }
        createFile("a.jar")

        uploader(client, clock).upload()

        assertThat(clock.pauses).containsExactly(120_000L)
        assertThat(client.calls.map { it.at }).containsExactly(0L, 120_000L)
    }

    @Test
    fun `Retry-After as an HTTP-date waits until that date`() {
        // Thu, 12 Nov 2026 01:27:45 GMT
        val clock = FakeClock(start = 1_794_446_865_000L)
        val client = RecordingClient(clock) { _, call ->
            if (call == 0) {
                HttpPutResult.Error(429, "slow down", retryAfter = "Thu, 12 Nov 2026 01:29:15 GMT")
            } else {
                HttpPutResult.Success
            }
        }
        createFile("a.jar")

        uploader(client, clock).upload()

        assertThat(clock.pauses).containsExactly(90_000L)
    }

    @Test
    fun `without Retry-After the pause doubles while the server keeps refusing`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, call ->
            if (call < 4) HttpPutResult.Error(429, "error code: 1015") else HttpPutResult.Success
        }
        createFile("a.jar")

        uploader(client, clock).upload()

        assertThat(clock.pauses).containsExactly(30_000L, 60_000L, 120_000L, 240_000L)
    }

    @Test
    fun `a 5xx or an I-O error pauses from the short initial pause, jittered upward`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, call ->
            if (call == 0) HttpPutResult.Error(503, "Service Unavailable") else HttpPutResult.Success
        }
        createFile("a.jar")

        MavenRepositoryUploader.toRepository("Test Repo")
            .from(stagingDir)
            .atUrl("https://repo.example.com")
            .withCredentials("user", "token")
            .withHttpClient(client)
            .withClock(clock, Random(seed = 42))
            .upload()

        assertThat(clock.pauses).hasSize(1)
        assertThat(clock.pauses.single()).isBetween(1_000L, 1_250L)
    }

    @Test
    fun `after a 429 fewer uploads run at once`() {
        val clock = FakeClock()
        // The four first calls are all in flight before the 429 is answered, and finish only once
        // the pause began: a worker past the lowered limit then retires instead of taking a file.
        val firstWave = CountDownLatch(4)
        val gateClosed = CountDownLatch(1)
        clock.onSleep.add { gateClosed.countDown() }
        val startedAfterPause = AtomicInteger(0)
        val peakAfterPause = AtomicInteger(0)
        val calls = AtomicInteger(0)
        val client = HttpPutClient { _, _, _ ->
            val call = calls.getAndIncrement()
            val afterPause = clock.nowMillis() >= 30_000L
            firstWave.countDown()
            if (call == 0) {
                firstWave.await(2, TimeUnit.SECONDS)
                return@HttpPutClient HttpPutResult.Error(429, "error code: 1015")
            }
            if (call < 4) gateClosed.await(2, TimeUnit.SECONDS)
            if (afterPause) peakAfterPause.updateAndGet { maxOf(it, startedAfterPause.incrementAndGet()) }
            try {
                Thread.sleep(20)
                HttpPutResult.Success
            } finally {
                if (afterPause) startedAfterPause.decrementAndGet()
            }
        }
        repeat(24) { createFile("artifact-$it.jar") }

        val summary = uploader(client, clock).withConcurrency(4).upload()

        assertThat(summary.uploaded).isEqualTo(24)
        assertThat(peakAfterPause.get()).isBetween(1, 2)
    }

    @Test
    fun `past the budget the run stops, says what was uploaded, and keeps the metadata back`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { path, _ ->
            if (path == "io/komune/test/1.0-SNAPSHOT/test-1.0-1.jar") {
                HttpPutResult.Success
            } else {
                HttpPutResult.Error(429, "error code: 1015")
            }
        }
        createFile("io/komune/test/1.0-SNAPSHOT/test-1.0-1.jar")
        createFile("io/komune/test/1.0-SNAPSHOT/test-1.0-1.pom")
        createFile("io/komune/test/1.0-SNAPSHOT/test-1.0-1.module")
        createFile("io/komune/test/1.0-SNAPSHOT/maven-metadata.xml")
        createFile("io/komune/test/1.0-SNAPSHOT/maven-metadata.xml.sha1")
        createFile("io/komune/test/maven-metadata.xml")
        val policy = PausePolicy(budgetMillis = 100_000L)

        assertThatThrownBy { uploader(client, clock, policy).withConcurrency(1).upload() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining(
                "Test Repo upload failed for 5 file(s): 1 of 6 file(s) uploaded, 0 already existed, " +
                    "1 failed, 4 not attempted"
            )
            .hasMessageContaining("pause budget of 1m40s exhausted")
            .hasMessageContaining("No maven-metadata.xml was uploaded")

        // 30s then 60s fit in 100s; the third pause (120s) would not, so it is never taken.
        assertThat(clock.pauses).containsExactly(30_000L, 60_000L)
        assertThat(client.calls.map { it.path }).noneMatch { it.contains("maven-metadata.xml") }
    }

    @Test
    fun `a Retry-After beyond the budget fails at once`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, _ -> HttpPutResult.Error(429, "slow down", retryAfter = "3600") }
        createFile("a.jar")
        createFile("b.jar")

        assertThatThrownBy { uploader(client, clock).withConcurrency(1).upload() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("0 of 2 file(s) uploaded")
            .hasMessageContaining("Retry-After of 1h0m0s")

        assertThat(clock.pauses).isEmpty()
        assertThat(client.calls).hasSize(1)
    }

    @Test
    fun `maven-metadata files and their checksums are uploaded after every other file`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, _ -> HttpPutResult.Success }
        listOf("a", "b", "c").forEach { module ->
            createFile("io/komune/$module/maven-metadata.xml")
            createFile("io/komune/$module/maven-metadata.xml.sha1")
            createFile("io/komune/$module/1.0-SNAPSHOT/maven-metadata.xml")
            createFile("io/komune/$module/1.0-SNAPSHOT/maven-metadata.xml.md5")
            createFile("io/komune/$module/1.0-SNAPSHOT/$module-1.0-20261112.012719-1.jar")
            createFile("io/komune/$module/1.0-SNAPSHOT/$module-1.0-20261112.012719-1.jar.sha1")
            createFile("io/komune/$module/1.0-SNAPSHOT/$module-1.0-20261112.012719-1.pom")
            createFile("io/komune/$module/1.0-SNAPSHOT/$module-1.0-20261112.012719-1.module")
        }

        val summary = uploader(client, clock).withConcurrency(3).upload()

        assertThat(summary.uploaded).isEqualTo(24)
        val order = client.calls.map { it.path }
        val lastArtifact = order.indexOfLast { !it.substringAfterLast('/').startsWith("maven-metadata.xml") }
        val firstMetadata = order.indexOfFirst { it.substringAfterLast('/').startsWith("maven-metadata.xml") }
        assertThat(firstMetadata).isGreaterThan(lastArtifact)
    }

    @Test
    fun `a failed artifact keeps every maven-metadata file back, so the previous snapshot stays whole`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { path, _ ->
            if (path.endsWith(".pom")) HttpPutResult.Error(400, "Bad Request") else HttpPutResult.Success
        }
        createFile("io/komune/test/1.0-SNAPSHOT/test-1.0-1.jar")
        createFile("io/komune/test/1.0-SNAPSHOT/test-1.0-1.pom")
        createFile("io/komune/test/1.0-SNAPSHOT/maven-metadata.xml")
        createFile("io/komune/test/maven-metadata.xml")

        assertThatThrownBy { uploader(client, clock).upload() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("1 of 4 file(s) uploaded, 0 already existed, 1 failed, 2 not attempted")
            .hasMessageContaining("No maven-metadata.xml was uploaded")
            .hasMessageContaining("test-1.0-1.pom → HTTP 400")

        assertThat(client.calls.map { it.path }).noneMatch { it.contains("maven-metadata.xml") }
    }

    @Test
    fun `a failure among the metadata says the publication is partial`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { path, _ ->
            if (path == "io/komune/b/1.0-SNAPSHOT/maven-metadata.xml") {
                HttpPutResult.Error(403, "Forbidden")
            } else {
                HttpPutResult.Success
            }
        }
        listOf("a", "b").forEach { module ->
            createFile("io/komune/$module/1.0-SNAPSHOT/$module-1.0-1.jar")
            createFile("io/komune/$module/1.0-SNAPSHOT/maven-metadata.xml")
        }

        assertThatThrownBy { uploader(client, clock).withConcurrency(1).upload() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("3 of 4 file(s) uploaded")
            .hasMessageContaining("partially published: 1 of 2 maven-metadata file(s) uploaded")
    }

    @Test
    fun `a hard answer is neither retried nor paused for`() {
        createFile("a.jar")
        listOf(400, 401, 403, 409).forEach { code ->
            val clock = FakeClock()
            val result = if (code == 409) HttpPutResult.Conflict else HttpPutResult.Error(code, "no")
            val client = RecordingClient(clock) { _, _ -> result }

            runCatching { uploader(client, clock).upload() }

            assertThat(client.calls).describedAs("calls on HTTP $code").hasSize(1)
            assertThat(clock.pauses).describedAs("pauses on HTTP $code").isEmpty()
        }
    }

    private fun createFile(relativePath: String): File {
        val file = File(stagingDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText("test-content")
        return file
    }
}
