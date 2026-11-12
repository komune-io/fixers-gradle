package io.komune.fixers.gradle.integration.publish

import com.sun.net.httpserver.HttpServer
import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A real JVM + KMP snapshot build, staged by Gradle and sent by `stage` to a local repository:
 * every file a staged `.module` names arrives, and nothing a resolver does not need.
 */
class PublishSnapshotFilesIntegrationTest : BaseIntegrationTest() {

    private lateinit var server: HttpServer
    private val uploaded = ConcurrentLinkedQueue<String>()

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            uploaded.add(exchange.requestURI.path.removePrefix("/repo/"))
            exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(201, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun setupBuild() {
        settingsFile.writeText("""
            rootProject.name = "snapshot-files-test"
            gradle.extensions.extraProperties["io.komune.fixers.gradle.settings"] = true
            include("lib-jvm")
            include("lib-mpp")
        """.trimIndent())
        testProjectDir.resolve("VERSION").toFile().writeText("1.0.0-SNAPSHOT")
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.publish")
            }

            fixers {
                bundle {
                    name = "Snapshot Files Test"
                    description = "What a snapshot publish sends"
                    url = "https://github.com/komune-io/fixers-gradle"
                }
            }
        """.trimIndent())
        writeSubproject(name = "lib-jvm", plugin = "io.komune.fixers.gradle.kotlin.jvm", extra = "") {
            source(path = "lib-jvm/src/main/kotlin/com/example/JvmLib.kt", code = "class JvmLib")
        }
        writeSubproject(name = "lib-mpp", plugin = "io.komune.fixers.gradle.kotlin.mpp", extra = MPP_TARGETS) {
            source(
                path = "lib-mpp/src/commonMain/kotlin/com/example/MppLib.kt",
                code = "expect fun hello(): String",
            )
            source(
                path = "lib-mpp/src/jvmMain/kotlin/com/example/MppLibJvm.kt",
                code = """actual fun hello() = "jvm"""",
            )
            source(path = "lib-mpp/src/jsMain/kotlin/com/example/MppLibJs.kt", code = """actual fun hello() = "js"""")
        }
    }

    private fun writeSubproject(name: String, plugin: String, extra: String, sources: () -> Unit) {
        val dir = testProjectDir.resolve(name).toFile().apply { mkdirs() }
        File(dir, "build.gradle.kts").writeText("""
            plugins {
                id("$plugin")
                id("io.komune.fixers.gradle.publish")
            }

            repositories {
                mavenCentral()
            }

            group = "com.example"
            version = "1.0.0-SNAPSHOT"

            $extra
        """.trimIndent())
        sources()
    }

    private fun source(path: String, code: String) {
        val file = testProjectDir.resolve(path).toFile()
        file.parentFile.mkdirs()
        file.writeText(listOf("package com.example", "", code, "").joinToString(separator = System.lineSeparator()))
    }

    // The staged file a `.module` url stands for: the url is `-SNAPSHOT`, the staged name is timestamped.
    private fun stagedFileFor(moduleFile: File, url: String): File? {
        val suffix = url.substringAfter("1.0.0-SNAPSHOT")
        val artifactId = moduleFile.parentFile.parentFile.name
        val staged = """${Regex.escape(artifactId)}-1\.0\.0-\d{8}\.\d{6}-\d+${Regex.escape(suffix)}""".toRegex()
        return moduleFile.parentFile.listFiles()?.firstOrNull { staged.matches(it.name) }
    }

    @Test
    fun `stage sends every file a staged module names, sha1 only, and nothing else`() {
        setupBuild()

        val result = runGradle(
            "stage",
            "-Pfixers.publish.github.packages.url=http://localhost:${server.address.port}/repo",
            "-Pfixers.publish.github.username=user",
            "-Pfixers.publish.github.token=token",
        )

        assertThat(result.task(":stage")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val stagingDir = testProjectDir.resolve("build/staging-deploy").toFile()
        val staged = stagingDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(stagingDir).path }.toList()
        val modules = stagingDir.walkTopDown().filter { it.isFile && it.name.endsWith(".module") }.toList()
        // JVM: 1 module; KMP: root, -jvm, -js
        assertThat(modules).hasSizeGreaterThanOrEqualTo(4)

        modules.forEach { module ->
            val urls = MODULE_FILE_URL.findAll(module.readText()).map { it.groupValues[1] }.toList()
            assertThat(urls).describedAs("files named by ${module.name}").isNotEmpty
            urls.forEach { url ->
                val file = stagedFileFor(module, url)
                assertThat(file).describedAs("$url staged next to ${module.name}").isNotNull
                val path = file!!.relativeTo(stagingDir).path
                assertThat(uploaded).describedAs("$path, named by ${module.name}").contains(path, "$path.sha1")
            }
        }
        assertThat(uploaded).allMatch { path -> path in staged }
        assertThat(uploaded).noneMatch { path -> LEFT_OUT_SUFFIXES.any { path.endsWith(it) } }
        assertThat(uploaded).noneMatch { it.contains("-javadoc.jar") || it.contains("kotlin-tooling-metadata.json") }
        assertThat(uploaded).noneMatch { it.startsWith("com/example/lib-jvm/") && it.contains("-sources.jar") }
        assertThat(uploaded).anyMatch { it.startsWith("com/example/lib-mpp-jvm/") && it.endsWith("-sources.jar") }
        assertThat(staged).anyMatch { it.contains("-javadoc.jar") }
    }

    private companion object {
        val MODULE_FILE_URL = """"url"\s*:\s*"([^"/]+)"""".toRegex()
        val LEFT_OUT_SUFFIXES = listOf(".asc", ".md5", ".sha256", ".sha512")
        val MPP_TARGETS = """
            kotlin {
                jvm()
                js { browser() }
            }
        """.trimIndent()
    }
}
