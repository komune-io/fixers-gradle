package io.komune.fixers.gradle.plugin.publish

import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * How the uploads of one run behave against a server that rate limits or falters:
 * they pause together, on a clock the tests drive, and publish `maven-metadata.xml` last.
 */
class MavenRepositoryUploaderPacingTest {

    @TempDir
    lateinit var stagingDir: File

    /**
     * Virtual time: waiting moves it forward instead of waiting. Records each shared [pauses] entry
     * and each time an upload start was spaced to ([spacedUntil]).
     */
    class FakeClock(start: Long = 0) : UploadClock {
        private val now = AtomicLong(start)
        val pauses = ConcurrentLinkedQueue<Long>()
        val spacedUntil = ConcurrentLinkedQueue<Long>()
        val onPause = ConcurrentLinkedQueue<() -> Unit>()

        override fun nowMillis(): Long = now.get()
        override suspend fun sleep(millis: Long) {
            now.accumulateAndGet(now.get() + millis, ::maxOf)
            yield()
        }
        override suspend fun sleepUntil(epochMillis: Long) {
            spacedUntil.add(epochMillis)
            now.accumulateAndGet(epochMillis, ::maxOf)
            yield()
        }
        override suspend fun pause(millis: Long) {
            pauses.add(millis)
            onPause.forEach { it() }
            sleep(millis)
        }
    }

    /** Counts the calls running at once, holding each until one more than [limit] join or 200 ms pass. */
    private class ConcurrencyProbe(private val limit: Int) {
        private val live = AtomicInteger(0)
        val peak = AtomicInteger(0)

        fun <T> measure(block: () -> T): T {
            val current = live.incrementAndGet()
            peak.updateAndGet { maxOf(it, current) }
            val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(200)
            while (live.get() <= limit && System.nanoTime() < until) Thread.sleep(2)
            val joined = live.get()
            peak.updateAndGet { maxOf(it, joined) }
            try {
                return block()
            } finally {
                live.decrementAndGet()
            }
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
        clock.onPause.add { gateClosed.countDown() }
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
        val probe = ConcurrencyProbe(limit = 2)
        val firstWave = CountDownLatch(4)
        val calls = AtomicInteger(0)
        val client = HttpPutClient { _, _, _ ->
            if (calls.getAndIncrement() < 4) {
                // Four uploads in flight, all refused: the limit halves, the workers past it retire.
                firstWave.countDown()
                firstWave.await(2, TimeUnit.SECONDS)
                HttpPutResult.Error(429, "error code: 1015")
            } else {
                probe.measure { HttpPutResult.Success }
            }
        }
        repeat(8) { createFile("artifact-$it.jar") }

        val summary = uploader(client, clock).withConcurrency(4).upload()

        assertThat(summary.uploaded).isEqualTo(8)
        assertThat(clock.pauses).containsExactly(30_000L)
        assertThat(probe.peak.get()).isBetween(1, 2)
    }

    @Test
    fun `the metadata uploads run at the concurrency the artifacts left`() {
        val clock = FakeClock()
        val probe = ConcurrencyProbe(limit = 2)
        val firstWave = CountDownLatch(4)
        val calls = AtomicInteger(0)
        val client = RecordingClient(clock) { path, _ ->
            when {
                path.substringAfterLast('/').startsWith("maven-metadata.xml") -> probe.measure { HttpPutResult.Success }
                calls.getAndIncrement() < 4 -> {
                    firstWave.countDown()
                    firstWave.await(2, TimeUnit.SECONDS)
                    HttpPutResult.Error(429, "error code: 1015")
                }
                else -> HttpPutResult.Success
            }
        }
        repeat(4) { createFile("io/komune/m$it/1.0-SNAPSHOT/m$it-1.0-1.jar") }
        repeat(6) { createFile("io/komune/m$it/1.0-SNAPSHOT/maven-metadata.xml") }

        val summary = uploader(client, clock).withConcurrency(4).upload()

        assertThat(summary.uploaded).isEqualTo(10)
        assertThat(probe.peak.get()).isBetween(1, 2)
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
        val policy = PausePolicy(rateLimitBudgetMillis = 100_000L)

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

    @Test
    fun `a Retry-After of 0 still pauses for the backoff`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, call ->
            if (call == 0) HttpPutResult.Error(429, "slow down", retryAfter = "0") else HttpPutResult.Success
        }
        createFile("a.jar")

        uploader(client, clock).upload()

        assertThat(clock.pauses).containsExactly(30_000L)
    }

    @Test
    fun `a Retry-After date already past still pauses for the backoff`() {
        // Thu, 12 Nov 2026 01:27:45 GMT, a clock ahead of the server's
        val clock = FakeClock(start = 1_794_446_865_000L)
        val client = RecordingClient(clock) { _, call ->
            if (call == 0) {
                HttpPutResult.Error(429, "slow down", retryAfter = "Thu, 12 Nov 2026 01:00:00 GMT")
            } else {
                HttpPutResult.Success
            }
        }
        createFile("a.jar")

        uploader(client, clock).upload()

        assertThat(clock.pauses).containsExactly(30_000L)
    }

    @Test
    fun `a Retry-After too large to count in milliseconds fails at once`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, _ ->
            HttpPutResult.Error(429, "slow down", retryAfter = "9223372036854776")
        }
        createFile("a.jar")

        assertThatThrownBy { uploader(client, clock).upload() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("Retry-After")

        assertThat(clock.pauses).isEmpty()
    }

    @Test
    fun `a 429 on a file's last attempt still pauses the others`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { path, _ ->
            if (path == "a.jar") HttpPutResult.Error(429, "error code: 1015") else HttpPutResult.Success
        }
        createFile("a.jar")
        createFile("b.jar")

        assertThatThrownBy { uploader(client, clock).withConcurrency(1).withRetry(attempts = 1).upload() }
            .isInstanceOf(GradleException::class.java)

        assertThat(clock.pauses).containsExactly(30_000L)
        assertThat(client.calls.single { it.path == "b.jar" }.at).isEqualTo(30_000L)
    }

    @Test
    fun `a server that keeps failing with 5xx stops the run within the 5xx budget`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, _ -> HttpPutResult.Error(503, "Service Unavailable") }
        createFile("a.jar")
        createFile("b.jar")

        assertThatThrownBy { uploader(client, clock).withConcurrency(1).upload() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("5xx and I/O pause budget of 2m0s exhausted")
            .hasMessageContaining("1 failed, 1 not attempted")

        // 1+2+4+8+16+32 s = 63 s fit in 2 min; the seventh pause (64 s) would not.
        assertThat(clock.pauses).containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L)
        assertThat(clock.nowMillis()).isEqualTo(63_000L)
    }

    @Test
    fun `uploads to a spaced repository never start closer than the interval`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, _ -> HttpPutResult.Success }
        repeat(10) { createFile("artifact-$it.jar") }

        uploader(client, clock).withConcurrency(10).withMinRequestInterval(250).upload()

        assertThat(client.calls).hasSize(10)
        assertThat(clock.spacedUntil.sorted()).containsExactlyElementsOf((1..9).map { it * 250L })
    }

    @Test
    fun `a 429 widens the spacing`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, call ->
            if (call == 0) HttpPutResult.Error(429, "error code: 1015") else HttpPutResult.Success
        }
        listOf("a.jar", "b.jar", "c.jar").forEach(::createFile)

        uploader(client, clock).withConcurrency(1).withMinRequestInterval(250).upload()

        assertThat(clock.pauses).containsExactly(30_000L)
        assertThat(clock.spacedUntil).containsExactly(30_500L, 31_000L)
    }

    @Test
    fun `a 429 spaces an unspaced repository too`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, call ->
            if (call == 0) HttpPutResult.Error(429, "error code: 1015") else HttpPutResult.Success
        }
        listOf("a.jar", "b.jar").forEach(::createFile)

        uploader(client, clock).withConcurrency(1).upload()

        assertThat(clock.spacedUntil).containsExactly(30_250L)
    }

    @Test
    fun `a small upload is slowed by the spacing and nothing else`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, _ -> HttpPutResult.Success }
        listOf("a.jar", "b.jar", "c.jar").forEach(::createFile)

        uploader(client, clock).withConcurrency(10).withMinRequestInterval(250).upload()

        assertThat(clock.spacedUntil.sorted()).containsExactly(250L, 500L)
        assertThat(clock.pauses).isEmpty()
        assertThat(clock.nowMillis()).isEqualTo(500L)
    }

    @Test
    fun `an unspaced repository sends without waiting`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { _, _ -> HttpPutResult.Success }
        repeat(5) { createFile("artifact-$it.jar") }

        uploader(client, clock).withConcurrency(10).upload()

        assertThat(clock.spacedUntil).isEmpty()
        assertThat(clock.nowMillis()).isZero()
    }

    @Test
    fun `version-level metadata goes first, and artifact-level metadata waits for it`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { path, _ ->
            if (path.endsWith("1.0-SNAPSHOT/maven-metadata.xml")) HttpPutResult.Error(403, "Forbidden") else HttpPutResult.Success
        }
        createFile("io/komune/a/1.0-SNAPSHOT/a-1.0-1.jar")
        createFile("io/komune/a/1.0-SNAPSHOT/maven-metadata.xml")
        createFile("io/komune/a/maven-metadata.xml")

        assertThatThrownBy { uploader(client, clock).withConcurrency(3).upload() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("partially published: 0 of 2 maven-metadata file(s) uploaded")

        assertThat(client.calls.map { it.path }).doesNotContain("io/komune/a/maven-metadata.xml")
    }

    @Test
    fun `for a fixed version the failure says the uploaded files are already visible`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { path, _ ->
            if (path.endsWith(".pom")) HttpPutResult.Error(400, "Bad Request") else HttpPutResult.Success
        }
        createFile("io/komune/test/1.0.0/test-1.0.0.jar")
        createFile("io/komune/test/1.0.0/test-1.0.0.pom")
        createFile("io/komune/test/maven-metadata.xml")

        assertThatThrownBy { uploader(client, clock).upload() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("No maven-metadata.xml was uploaded")
            .hasMessageContaining("can already be fetched by path")
    }

    @Test
    fun `zero concurrency is refused rather than reported as a clean upload`() {
        createFile("a.jar")

        assertThatThrownBy { uploader(HttpPutClient { _, _, _ -> HttpPutResult.Success }, FakeClock()).withConcurrency(0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a failure that outlived the pause is retried at once, and pauses again if it repeats`() {
        val clock = FakeClock()
        val aCalls = AtomicInteger(0)
        val bCalls = AtomicInteger(0)
        val bInFlight = CountDownLatch(1)
        val client = RecordingClient(clock) { path, _ ->
            if (path == "a.jar") {
                if (aCalls.getAndIncrement() == 0) {
                    bInFlight.await(2, TimeUnit.SECONDS)
                    HttpPutResult.Error(429, "error code: 1015")
                } else {
                    HttpPutResult.Success
                }
            } else {
                val call = bCalls.getAndIncrement()
                if (call == 0) {
                    bInFlight.countDown()
                    // In flight for the whole pause: its 429 arrives once the pause is over.
                    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                    while (clock.nowMillis() < 30_000L && System.nanoTime() < until) Thread.sleep(2)
                }
                if (call < 2) HttpPutResult.Error(429, "error code: 1015") else HttpPutResult.Success
            }
        }
        createFile("a.jar")
        createFile("b.jar")

        val summary = uploader(client, clock).withConcurrency(2).upload()

        assertThat(summary.uploaded).isEqualTo(2)
        assertThat(clock.pauses).hasSize(2)
        assertThat(clock.pauses.first()).isEqualTo(30_000L)
        // b's late 429 waits for no pause of its own: it goes at one of the next two spaced starts.
        val bStarts = client.calls.filter { it.path == "b.jar" }.map { it.at }
        assertThat(bStarts.first()).isZero()
        assertThat(bStarts[1]).isBetween(30_000L, 30_250L)
    }

    @Test
    fun `an unexpected error cancels the uploads waiting on a pause`() {
        val pauseStarted = CountDownLatch(1)
        val bInFlight = CountDownLatch(1)
        val clock = object : UploadClock {
            override fun nowMillis(): Long = 0
            override suspend fun sleep(millis: Long) = delay(millis)
            override suspend fun pause(millis: Long) {
                pauseStarted.countDown()
                delay(TimeUnit.MINUTES.toMillis(10))
            }
        }
        val client = HttpPutClient { url, _, _ ->
            if (url.endsWith("/a.jar")) {
                bInFlight.await(2, TimeUnit.SECONDS)
                HttpPutResult.Error(429, "error code: 1015")
            } else {
                bInFlight.countDown()
                pauseStarted.await(2, TimeUnit.SECONDS)
                error("boom")
            }
        }
        createFile("a.jar")
        createFile("b.jar")

        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            assertThatThrownBy { uploader(client, clock).withConcurrency(2).upload() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("boom")
        }
    }

    @Test
    fun `the budget running out while several uploads are in flight ends the run`() {
        val clock = FakeClock()
        val firstWave = CountDownLatch(3)
        val client = RecordingClient(clock) { _, call ->
            if (call < 3) {
                firstWave.countDown()
                firstWave.await(2, TimeUnit.SECONDS)
            }
            HttpPutResult.Error(429, "error code: 1015")
        }
        repeat(9) { createFile("artifact-$it.jar") }
        val policy = PausePolicy(rateLimitBudgetMillis = 100_000L)

        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            assertThatThrownBy { uploader(client, clock, policy).withConcurrency(3).upload() }
                .isInstanceOf(GradleException::class.java)
                .hasMessageContaining("upload failed for 9 file(s): 0 of 9 file(s) uploaded")
        }
        assertThat(clock.pauses).containsExactly(30_000L, 60_000L)
    }

    @Test
    fun `a file that keeps failing gives up alone while the others go on`() {
        val clock = FakeClock()
        val client = RecordingClient(clock) { path, _ ->
            if (path == "x.jar") HttpPutResult.Error(503, "Service Unavailable") else HttpPutResult.Success
        }
        listOf("a.jar", "b.jar", "c.jar", "x.jar", "maven-metadata.xml").forEach(::createFile)

        assertThatThrownBy { uploader(client, clock).withConcurrency(2).withRetry(attempts = 3).upload() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("3 of 5 file(s) uploaded, 0 already existed, 1 failed, 1 not attempted")
            .hasMessageContaining("x.jar → HTTP 503")

        assertThat(client.calls.count { it.path == "x.jar" }).isEqualTo(3)
    }

    private fun createFile(relativePath: String): File {
        val file = File(stagingDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText("test-content")
        return file
    }
}
