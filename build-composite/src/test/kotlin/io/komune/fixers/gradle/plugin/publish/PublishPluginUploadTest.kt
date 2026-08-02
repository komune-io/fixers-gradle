package io.komune.fixers.gradle.plugin.publish

import com.sun.net.httpserver.HttpServer
import io.komune.fixers.gradle.config.ConfigExtension
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
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

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
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
        configure: (ConfigExtension) -> Unit = {}
    ): Project {
        val root = ProjectBuilder.builder().build()
        root.version = version
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
}
