package io.komune.fixers.gradle.plugin.publish

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MavenRepositoryUploaderTest {

	@TempDir
	lateinit var stagingDir: File

	class FakeHttpPutClient : HttpPutClient {
		val calls = ConcurrentLinkedQueue<String>()
		var resultToReturn: HttpPutResult = HttpPutResult.Success
		val concurrentPeak = AtomicInteger(0)
		private val currentConcurrent = AtomicInteger(0)

		override fun put(url: String, authHeader: String, file: File): HttpPutResult {
			val current = currentConcurrent.incrementAndGet()
			concurrentPeak.updateAndGet { maxOf(it, current) }
			try {
				return resultToReturn.also { calls.add(url) }
			} finally {
				currentConcurrent.decrementAndGet()
			}
		}
	}

	@Test
	fun `uploads all files from staging directory`() {
		val fake = FakeHttpPutClient()
		createFile("io/komune/test/1.0/test-1.0.jar")
		createFile("io/komune/test/1.0/test-1.0.pom")
		createFile("io/komune/test/1.0/test-1.0.module")

		MavenRepositoryUploader.to("Test Repo")
			.from(stagingDir)
			.at("https://repo.example.com/releases")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.upload()

		assertThat(fake.calls).hasSize(3)
	}

	@Test
	fun `returns correct summary counts`() {
		val fake = FakeHttpPutClient()
		createFile("artifact-1.jar")
		createFile("artifact-2.jar")

		val summary = MavenRepositoryUploader.to("Test Repo")
			.from(stagingDir)
			.at("https://repo.example.com")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.upload()

		assertThat(summary.uploaded).isEqualTo(2)
		assertThat(summary.skipped).isEqualTo(0)
		assertThat(summary.failed).isEqualTo(0)
		assertThat(summary.errors).isEmpty()
	}

	@Test
	fun `skips already-existing artifacts on 409 conflict`() {
		val fake = FakeHttpPutClient().apply {
			resultToReturn = HttpPutResult.Conflict
		}
		createFile("artifact.jar")

		val summary = MavenRepositoryUploader.to("Test Repo")
			.from(stagingDir)
			.at("https://repo.example.com")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.upload()

		assertThat(summary.uploaded).isEqualTo(0)
		assertThat(summary.skipped).isEqualTo(1)
		assertThat(summary.failed).isEqualTo(0)
	}

	@Test
	fun `collects errors and throws GradleException on failures`() {
		val fake = FakeHttpPutClient().apply {
			resultToReturn = HttpPutResult.Error(500, "Internal Server Error")
		}
		createFile("artifact.jar")

		assertThatThrownBy {
			MavenRepositoryUploader.to("Test Repo")
				.from(stagingDir)
				.at("https://repo.example.com")
				.withCredentials("user", "token")
				.withHttpClient(fake)
				.withRetry(attempts = 1, backoffMillis = 0)
				.upload()
		}
			.isInstanceOf(GradleException::class.java)
			.hasMessageContaining("Test Repo upload failed for 1 file(s)")
			.hasMessageContaining("HTTP 500")
	}

	/** Returns each queued result in turn; the last one repeats once the queue is drained. */
	class ScriptedHttpPutClient(results: List<HttpPutResult>) : HttpPutClient {
		private val remaining = ConcurrentLinkedQueue(results)
		private var last: HttpPutResult = results.last()
		val callCount = AtomicInteger(0)

		override fun put(url: String, authHeader: String, file: File): HttpPutResult {
			callCount.incrementAndGet()
			return remaining.poll()?.also { last = it } ?: last
		}
	}

	@Test
	fun `retries transient 5xx failures and succeeds`() {
		val fake = ScriptedHttpPutClient(
			listOf(
				HttpPutResult.Error(503, "Service Unavailable"),
				HttpPutResult.Error(502, "Bad Gateway"),
				HttpPutResult.Success,
			)
		)
		createFile("artifact.jar")

		val summary = MavenRepositoryUploader.to("Test Repo")
			.from(stagingDir)
			.at("https://repo.example.com")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.withRetry(attempts = 3, backoffMillis = 1)
			.upload()

		assertThat(fake.callCount.get()).isEqualTo(3)
		assertThat(summary.uploaded).isEqualTo(1)
		assertThat(summary.failed).isEqualTo(0)
	}

	@Test
	fun `retries rate limiting responses`() {
		val fake = ScriptedHttpPutClient(
			listOf(HttpPutResult.Error(429, "Too Many Requests"), HttpPutResult.Success)
		)
		createFile("artifact.jar")

		val summary = MavenRepositoryUploader.to("Test Repo")
			.from(stagingDir)
			.at("https://repo.example.com")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.withRetry(attempts = 3, backoffMillis = 1)
			.upload()

		assertThat(fake.callCount.get()).isEqualTo(2)
		assertThat(summary.uploaded).isEqualTo(1)
	}

	@Test
	fun `does not retry client errors`() {
		val fake = ScriptedHttpPutClient(listOf(HttpPutResult.Error(401, "Unauthorized")))
		createFile("artifact.jar")

		assertThatThrownBy {
			MavenRepositoryUploader.to("Test Repo")
				.from(stagingDir)
				.at("https://repo.example.com")
				.withCredentials("user", "token")
				.withHttpClient(fake)
				.withRetry(attempts = 3, backoffMillis = 1)
				.upload()
		}.isInstanceOf(GradleException::class.java)

		assertThat(fake.callCount.get()).isEqualTo(1)
	}

	@Test
	fun `does not retry 409 conflicts`() {
		val fake = ScriptedHttpPutClient(listOf(HttpPutResult.Conflict))
		createFile("artifact.jar")

		val summary = MavenRepositoryUploader.to("Test Repo")
			.from(stagingDir)
			.at("https://repo.example.com")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.withRetry(attempts = 3, backoffMillis = 1)
			.upload()

		assertThat(fake.callCount.get()).isEqualTo(1)
		assertThat(summary.skipped).isEqualTo(1)
	}

	@Test
	fun `gives up after the configured number of attempts`() {
		val fake = ScriptedHttpPutClient(listOf(HttpPutResult.Error(500, "Internal Server Error")))
		createFile("artifact.jar")

		assertThatThrownBy {
			MavenRepositoryUploader.to("Test Repo")
				.from(stagingDir)
				.at("https://repo.example.com")
				.withCredentials("user", "token")
				.withHttpClient(fake)
				.withRetry(attempts = 4, backoffMillis = 1)
				.upload()
		}
			.isInstanceOf(GradleException::class.java)
			.hasMessageContaining("HTTP 500")

		assertThat(fake.callCount.get()).isEqualTo(4)
	}

	@Test
	fun `no-op when staging directory is empty`() {
		val fake = FakeHttpPutClient()

		val summary = MavenRepositoryUploader.to("Test Repo")
			.from(stagingDir)
			.at("https://repo.example.com")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.upload()

		assertThat(summary.uploaded).isEqualTo(0)
		assertThat(summary.skipped).isEqualTo(0)
		assertThat(summary.failed).isEqualTo(0)
		assertThat(fake.calls).isEmpty()
	}

	@Test
	fun `no-op when staging directory does not exist`() {
		val fake = FakeHttpPutClient()
		val nonExistentDir = File(stagingDir, "does-not-exist")

		val summary = MavenRepositoryUploader.to("Test Repo")
			.from(nonExistentDir)
			.at("https://repo.example.com")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.upload()

		assertThat(summary.uploaded).isEqualTo(0)
		assertThat(fake.calls).isEmpty()
	}

	@Test
	fun `respects concurrency limit`() {
		val fake = FakeHttpPutClient()
		// Create enough files to potentially exceed concurrency
		repeat(20) { i ->
			createFile("artifact-$i.jar")
		}

		MavenRepositoryUploader.to("Test Repo")
			.from(stagingDir)
			.at("https://repo.example.com")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.withConcurrency(3)
			.upload()

		assertThat(fake.calls).hasSize(20)
		assertThat(fake.concurrentPeak.get()).isLessThanOrEqualTo(3)
	}

	@Test
	fun `constructs correct URLs from base URL and relative paths`() {
		val fake = FakeHttpPutClient()
		createFile("io/komune/test/1.0/test-1.0.jar")

		MavenRepositoryUploader.to("Test Repo")
			.from(stagingDir)
			.at("https://repo.example.com/releases/")
			.withCredentials("user", "token")
			.withHttpClient(fake)
			.upload()

		assertThat(fake.calls).containsExactly(
			"https://repo.example.com/releases/io/komune/test/1.0/test-1.0.jar"
		)
	}

	private fun createFile(relativePath: String): File {
		val file = File(stagingDir, relativePath)
		file.parentFile.mkdirs()
		file.writeText("test-content")
		return file
	}
}
