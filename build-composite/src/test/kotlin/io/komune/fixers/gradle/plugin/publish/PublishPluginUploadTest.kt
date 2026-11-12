package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.buildFixersRoot
import com.sun.net.httpserver.HttpServer
import io.komune.fixers.gradle.config.ConfigExtension
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for the PublishPlugin stage/promote upload actions,
 * executed against a local HTTP server.
 */
class PublishPluginUploadTest {

    private lateinit var server: HttpServer
    private val requests = ConcurrentLinkedQueue<String>()
    private val arrivals = ConcurrentLinkedQueue<Long>()

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.executor = java.util.concurrent.Executors.newFixedThreadPool(UPLOAD_THREADS)
        // The Central Portal deployment status endpoint: answer terminal straight away so the
        // promote test does not sit in the polling loop.
        server.createContext("/status") { exchange ->
            requests.add("${exchange.requestMethod} ${exchange.requestURI}")
            exchange.requestBody.readBytes()
            val response = """{"deploymentState":"PUBLISHED"}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.createContext("/") { exchange ->
            arrivals.add(System.nanoTime())
            requests.add("${exchange.requestMethod} ${exchange.requestURI}")
            exchange.requestBody.readBytes()
            val response = "ok".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun baseUrl() = "http://localhost:${server.address.port}"

    private fun evaluatedBuild(
        version: String,
        gradleProperties: Map<String, Any> = emptyMap(),
        configure: (ConfigExtension) -> Unit = {}
    ): Project {
        val root = ProjectBuilder.builder().buildFixersRoot()
        root.version = version
        gradleProperties.forEach { (key, value) -> root.extensions.extraProperties[key] = value }
        val config = root.extensions.create(ConfigExtension.NAME, ConfigExtension::class.java, root)
        configure(config)
        val child = ProjectBuilder.builder().withParent(root).withName("lib").build()
        child.pluginManager.apply("io.komune.fixers.gradle.publish")
        root.plugins.apply(PublishPlugin::class.java)
        (child as ProjectInternal).evaluate()
        val gradle = root.gradle as GradleInternal
        gradle.buildListenerBroadcaster.projectsEvaluated(gradle)
        return root
    }

    private fun Project.stagingDir(): File {
        val dir = layout.buildDirectory.dir("staging-deploy").get().asFile
        dir.mkdirs()
        return dir
    }

    private fun Task.executeActions() {
        actions.forEach { it.execute(this) }
    }

    @Test
    fun `cleanStaging should delete the staging directory`() {
        val root = evaluatedBuild("1.0.0")
        val staging = root.stagingDir()
        File(staging, "artifact.jar").writeText("bytes")

        root.tasks.getByName("cleanStaging").executeActions()

        assertThat(staging).doesNotExist()
    }

    @Test
    fun `stage should upload staging files to github packages`() {
        val root = evaluatedBuild("1.0.0") { config ->
            config.publish.pkgGithubUsername.set("gh-user")
            config.publish.pkgGithubToken.set("gh-token")
            config.publish.githubPackagesUrl.set("${baseUrl()}/maven")
        }
        File(root.stagingDir(), "io/komune/lib/1.0.0/lib-1.0.0.jar").apply {
            parentFile.mkdirs()
            writeText("bytes")
        }

        root.tasks.getByName("stage").executeActions()

        assertThat(requests).contains("PUT /maven/io/komune/lib/1.0.0/lib-1.0.0.jar")
    }

    private fun stageSignedSnapshotModule(root: Project) {
        val dir = File(root.stagingDir(), "io/komune/lib/1.0.0-SNAPSHOT").apply { mkdirs() }
        val module = """{ "variants": [ { "files": [ { "url": "lib-1.0.0-SNAPSHOT.jar" } ] } ] }"""
        listOf(".pom" to "pom", ".module" to module, ".jar" to "jar", "-javadoc.jar" to "doc").forEach { (suffix, text) ->
            val file = File(dir, "lib-1.0.0-20261112.084413-1$suffix").apply { writeText(text) }
            File(file.path + ".asc").writeText("sig")
            listOf("md5", "sha1", "sha256", "sha512").forEach { File(file.path + ".$it").writeText(it) }
        }
    }

    @Test
    fun `stage sends a snapshot to github packages with the same files as maven central snapshots`() {
        val snapshotsRoot = evaluatedBuild("1.0.0-SNAPSHOT") { config ->
            config.publish.mavenCentralUsername.set("central-user")
            config.publish.mavenCentralPassword.set("central-pass")
            config.publish.mavenSnapshotsUrl.set("${baseUrl()}/repo")
            config.publish.mavenSnapshotsMinRequestIntervalMillis.set(0)
        }
        stageSignedSnapshotModule(snapshotsRoot)
        snapshotsRoot.tasks.getByName("promote").executeActions()
        val toCentral = requests.toSet()
        requests.clear()

        val githubRoot = evaluatedBuild("1.0.0-SNAPSHOT") { config ->
            config.publish.pkgGithubUsername.set("gh-user")
            config.publish.pkgGithubToken.set("gh-token")
            config.publish.githubPackagesUrl.set("${baseUrl()}/repo")
        }
        stageSignedSnapshotModule(githubRoot)
        githubRoot.tasks.getByName("stage").executeActions()

        assertThat(requests.toSet()).isEqualTo(toCentral)
        assertThat(toCentral).hasSize(6)
        assertThat(toCentral).noneMatch { it.endsWith(".asc") || it.endsWith(".md5") || it.contains("javadoc") }
    }

    @Test
    fun `stage sends a release to github packages with every staged file`() {
        val root = evaluatedBuild("1.0.0") { config ->
            config.publish.pkgGithubUsername.set("gh-user")
            config.publish.pkgGithubToken.set("gh-token")
            config.publish.githubPackagesUrl.set("${baseUrl()}/maven")
        }
        File(root.stagingDir(), "io/komune/lib/1.0.0").mkdirs()
        listOf("lib-1.0.0.jar", "lib-1.0.0.jar.asc", "lib-1.0.0.jar.md5", "lib-1.0.0-javadoc.jar").forEach {
            File(root.stagingDir(), "io/komune/lib/1.0.0/$it").writeText("bytes")
        }

        root.tasks.getByName("stage").executeActions()

        assertThat(requests).hasSize(4)
    }

    @Test
    fun `stage should fail when github credentials are missing`() {
        val root = evaluatedBuild("1.0.0")
        File(root.stagingDir(), "artifact.jar").writeText("bytes")

        assertThatThrownBy { root.tasks.getByName("stage").executeActions() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("FIXERS_PUBLISH_GITHUB_USERNAME")
    }

    @Test
    fun `promote should upload snapshots to maven snapshots repository`() {
        val root = evaluatedBuild("1.0.0-SNAPSHOT") { config ->
            config.publish.mavenCentralUsername.set("central-user")
            config.publish.mavenCentralPassword.set("central-pass")
            config.publish.mavenSnapshotsUrl.set("${baseUrl()}/snapshots")
        }
        File(root.stagingDir(), "io/komune/lib/lib.jar").apply {
            parentFile.mkdirs()
            writeText("bytes")
        }

        root.tasks.getByName("promote").executeActions()

        assertThat(requests).contains("PUT /snapshots/io/komune/lib/lib.jar")
    }

    @Test
    fun `promote should fail for snapshots when credentials are missing`() {
        val root = evaluatedBuild("1.0.0-SNAPSHOT")
        File(root.stagingDir(), "artifact.jar").writeText("bytes")

        assertThatThrownBy { root.tasks.getByName("promote").executeActions() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME")
    }

    @Test
    fun `promote should upload release bundle to central portal`() {
        val root = evaluatedBuild("1.0.0") { config ->
            config.publish.mavenCentralUsername.set("central-user")
            config.publish.mavenCentralPassword.set("central-pass")
            config.publish.mavenCentralUrl.set(baseUrl())
        }
        File(root.stagingDir(), "io/komune/lib/lib.jar").apply {
            parentFile.mkdirs()
            writeText("bytes")
        }

        root.tasks.getByName("promote").executeActions()

        assertThat(requests).anyMatch { it.startsWith("POST /upload?publishingType=AUTOMATIC") }
    }

    @Test
    fun `promote should fail for releases when credentials are missing`() {
        val root = evaluatedBuild("1.0.0")
        File(root.stagingDir(), "artifact.jar").writeText("bytes")

        assertThatThrownBy { root.tasks.getByName("promote").executeActions() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME")
    }

    // Upload starts as the server saw them, in milliseconds after the first one.
    private fun arrivalOffsetsMillis(): List<Long> {
        val sorted = arrivals.sorted()
        return sorted.map { TimeUnit.NANOSECONDS.toMillis(it - sorted.first()) }
    }

    private fun stageThreeFiles(root: Project, version: String) = listOf("a", "b", "c").forEach { name ->
        File(root.stagingDir(), "io/komune/lib/$version/lib-$name.jar").apply {
            parentFile.mkdirs()
            writeText("bytes")
        }
    }

    @Test
    fun `promote spaces snapshot uploads by the interval the Gradle key sets`() {
        val root = evaluatedBuild(
            version = "1.0.0-SNAPSHOT",
            gradleProperties = mapOf(SPACING_KEY to SPACING_MILLIS),
        ) { config ->
            config.publish.mavenCentralUsername.set("central-user")
            config.publish.mavenCentralPassword.set("central-pass")
            config.publish.mavenSnapshotsUrl.set("${baseUrl()}/snapshots")
        }
        stageThreeFiles(root, "1.0.0-SNAPSHOT")

        root.tasks.getByName("promote").executeActions()

        // Not the 250 ms default: the key was read, and handed to the uploader.
        val offsets = arrivalOffsetsMillis()
        assertThat(offsets).hasSize(3)
        assertThat(offsets.zipWithNext { a, b -> b - a }).allMatch { it >= SPACING_MILLIS - TOLERANCE_MILLIS }
    }

    @Test
    fun `stage sends to github packages unspaced whatever the snapshots key says`() {
        val root = evaluatedBuild(
            version = "1.0.0",
            gradleProperties = mapOf(SPACING_KEY to SPACING_MILLIS),
        ) { config ->
            config.publish.pkgGithubUsername.set("gh-user")
            config.publish.pkgGithubToken.set("gh-token")
            config.publish.githubPackagesUrl.set("${baseUrl()}/maven")
        }
        stageThreeFiles(root, "1.0.0")

        root.tasks.getByName("stage").executeActions()

        val offsets = arrivalOffsetsMillis()
        assertThat(offsets).hasSize(3)
        // Spaced by the key, the third upload could not start before 2 × 600 ms: well under that
        // is unspaced, with room for a slow runner.
        assertThat(offsets.last()).isLessThan(2 * SPACING_MILLIS - 2 * TOLERANCE_MILLIS)
    }

    private companion object {
        const val SPACING_KEY = "fixers.publish.maven.snapshots.minRequestIntervalMillis"
        const val SPACING_MILLIS = 600L
        const val TOLERANCE_MILLIS = 50L
        const val UPLOAD_THREADS = 4
    }
}
