package io.komune.fixers.gradle.integration.settings

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * The settings plugin together with the project plugins, in one build with a Kotlin/JS module.
 * The f2 catalog comes from a local Maven repository that holds it at the fixers version.
 */
class SettingsAndConfigIntegrationTest : BaseIntegrationTest() {

    private val version = checkNotNull(System.getProperty("fixers.version")) { "fixers.version is not set" }

    @Test
    fun `should resolve Node from the settings repositories with the config plugin applied`() {
        writeBuild()

        val result = runGradle(":kotlinNodeJsSetup")

        assertThat(result.task(":kotlinNodeJsSetup")?.outcome).isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `should fail the build on a repository declared by a project`() {
        writeBuild(libExtra = "repositories { mavenCentral() }")

        val result = runGradleAndFail("help")

        assertThat(result.output)
            .contains("Build was configured to prefer settings repositories over project repositories")
    }

    @Test
    fun `should fail the yarn resolution check when there is no lock`() {
        writeBuild(
            rootExtra = """
                plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
                    the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().resolution("**/ws", "8.22.2")
                }
            """.trimIndent(),
        )

        val result = runGradleAndFail("checkYarnResolutions")

        assertThat(result.output).contains("no yarn.lock to check **/ws against")
    }

    private fun writeBuild(rootExtra: String = "", libExtra: String = "") {
        val repo = publishCatalog()
        settingsFile.writeText(
            """
            pluginManagement { repositories { gradlePluginPortal() } }
            plugins { id("io.komune.fixers.gradle.settings") }
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    maven { name = "Catalog"; url = uri("${repo.toURI()}") }
                }
            }
            rootProject.name = "settings-and-config"
            include("lib")
            """.trimIndent()
        )
        writeBuildFile(
            """
            plugins { id("io.komune.fixers.gradle.config") }
            fixers { bundle { name = "settings-and-config" } }
            $rootExtra
            """.trimIndent()
        )
        createFile(
            "lib/build.gradle.kts",
            """
            plugins { id("io.komune.fixers.gradle.kotlin.mpp") }
            $libExtra
            """.trimIndent()
        )
    }

    /** Lays out `io.komune.f2:f2-gradle-catalog:<version>`, published the way Gradle publishes a catalog. */
    private fun publishCatalog(): File {
        val repo = testProjectDir.resolve("catalog-repo").toFile()
        val dir = File(repo, "io/komune/f2/f2-gradle-catalog/$version").apply { mkdirs() }
        val base = "f2-gradle-catalog-$version"
        File(dir, "$base.toml").writeText("[libraries]\nprobe = \"io.komune.probe:probe:1.2.3\"\n")
        File(dir, "$base.pom").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <!-- do_not_remove: published-with-gradle-metadata -->
              <modelVersion>4.0.0</modelVersion>
              <groupId>io.komune.f2</groupId>
              <artifactId>f2-gradle-catalog</artifactId>
              <version>$version</version>
              <packaging>toml</packaging>
            </project>
            """.trimIndent()
        )
        File(dir, "$base.module").writeText(
            """
            {
              "formatVersion": "1.1",
              "component": { "group": "io.komune.f2", "module": "f2-gradle-catalog", "version": "$version" },
              "variants": [
                {
                  "name": "versionCatalogElements",
                  "attributes": { "org.gradle.category": "platform", "org.gradle.usage": "version-catalog" },
                  "files": [ { "name": "$base.toml", "url": "$base.toml" } ]
                }
              ]
            }
            """.trimIndent()
        )
        return repo
    }
}
