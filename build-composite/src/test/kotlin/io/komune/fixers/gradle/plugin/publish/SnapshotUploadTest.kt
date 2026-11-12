package io.komune.fixers.gradle.plugin.publish

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** What the uploader sends for a snapshot and for a release, and how a quota spaces it. */
class SnapshotUploadTest {

    @TempDir
    lateinit var stagingDir: File

    private val ts = "1.0.0-20261112.084413-1"

    private fun stageJvmModule(artifactId: String, version: String = "1.0.0-SNAPSHOT", fileVersion: String = ts) {
        val dir = File(stagingDir, "io/komune/test/$artifactId/$version")
        dir.mkdirs()
        val module = """{ "variants": [ { "files": [ { "url": "$artifactId-$version.jar" } ] } ] }"""
        listOf(".pom" to "pom", ".module" to module, ".jar" to "jar", "-sources.jar" to "src", "-javadoc.jar" to "doc")
            .forEach { (suffix, content) ->
                val file = File(dir, "$artifactId-$fileVersion$suffix")
                file.writeText(content)
                File(file.path + ".asc").writeText("sig")
                listOf("md5", "sha1", "sha256", "sha512").forEach { File(file.path + ".$it").writeText(it) }
            }
    }

    private class Recording : HttpPutClient {
        val paths = ConcurrentLinkedQueue<String>()
        override fun put(url: String, authHeader: String, file: File): HttpPutResult {
            paths.add(url.substringAfter("https://repo.example.com/"))
            return HttpPutResult.Success
        }
    }

    private fun upload(snapshot: Boolean, client: HttpPutClient) = MavenRepositoryUploader.toRepository("Test Repo")
        .from(stagingDir, snapshot = snapshot)
        .atUrl("https://repo.example.com")
        .withCredentials("user", "token")
        .withHttpClient(client)
        .upload()

    @Test
    fun `a snapshot upload sends only what resolution needs`() {
        stageJvmModule("lib")
        val client = Recording()

        val summary = upload(snapshot = true, client = client)

        val dir = "io/komune/test/lib/1.0.0-SNAPSHOT"
        assertThat(client.paths).containsExactlyInAnyOrder(
            "$dir/lib-$ts.jar", "$dir/lib-$ts.jar.sha1",
            "$dir/lib-$ts.module", "$dir/lib-$ts.module.sha1",
            "$dir/lib-$ts.pom", "$dir/lib-$ts.pom.sha1",
        )
        assertThat(summary.uploaded).isEqualTo(6)
    }

    @Test
    fun `a release upload sends every staged file`() {
        stageJvmModule("lib", version = "1.0.0", fileVersion = "1.0.0")
        val client = Recording()

        upload(snapshot = false, client = client)

        assertThat(client.paths).hasSize(5 * 6)
        assertThat(client.paths).anyMatch { it.endsWith("-javadoc.jar.asc") }
    }

    @Test
    fun `an upload the quota holds keeps the configured spacing`() {
        assertThat(RequestQuota(requests = 450, windowMillis = 1_200_000).spacingFor(450, 250)).isEqualTo(250)
        assertThat(null.spacingFor(fileCount = 10_000, minIntervalMillis = 250)).isEqualTo(250)
    }

    @Test
    fun `an upload larger than the quota is spaced to stay under it from the first request`() {
        // 1 200 000 ms / 450 requests = 2 666.7 ms, rounded up
        assertThat(RequestQuota(requests = 450, windowMillis = 1_200_000).spacingFor(451, 250)).isEqualTo(2_667)
        assertThat(RequestQuota(requests = 3, windowMillis = 30_000).spacingFor(4, 0)).isEqualTo(10_000)
    }

    @Test
    fun `a 429 on a quota-spaced upload never speeds it up`() {
        stageJvmModule("a")
        stageJvmModule("b")
        val clock = MavenRepositoryUploaderPacingTest.FakeClock()
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val refusedOnce = HttpPutClient { _, _, _ ->
            if (calls.getAndIncrement() == 2) HttpPutResult.Error(429, "error code: 1015") else HttpPutResult.Success
        }

        MavenRepositoryUploader.toRepository("Test Repo")
            .from(stagingDir, snapshot = true)
            .atUrl("https://repo.example.com")
            .withCredentials("user", "token")
            .withHttpClient(refusedOnce)
            .withConcurrency(1)
            .withMinRequestInterval(250, quota = RequestQuota(requests = 4, windowMillis = 40_000))
            .withClock(clock, MavenRepositoryUploaderPacingTest.NoJitter)
            .upload()

        // 12 files over 4 per 40 s: spaced 10 s. After the 429's pause the spacing never drops.
        val starts = clock.spacedUntil.sorted()
        assertThat(clock.pauses).containsExactly(30_000L)
        assertThat(starts.zipWithNext { a, b -> b - a }).allMatch { it >= 10_000L }
    }

    @Test
    fun `the uploader applies the quota's spacing to a large upload`() {
        stageJvmModule("a")
        stageJvmModule("b")
        val clock = MavenRepositoryUploaderPacingTest.FakeClock()
        val quota = RequestQuota(requests = 4, windowMillis = 40_000)

        MavenRepositoryUploader.toRepository("Test Repo")
            .from(stagingDir, snapshot = true)
            .atUrl("https://repo.example.com")
            .withCredentials("user", "token")
            .withHttpClient(Recording())
            .withConcurrency(1)
            .withMinRequestInterval(250, quota = quota)
            .withClock(clock, Random(0))
            .upload()

        // 12 files over a quota of 4 per 40 s: one start every 10 s, so the last at 110 s
        assertThat(clock.spacedUntil.sorted()).containsExactlyElementsOf((1..11).map { it * 10_000L })
    }
}
