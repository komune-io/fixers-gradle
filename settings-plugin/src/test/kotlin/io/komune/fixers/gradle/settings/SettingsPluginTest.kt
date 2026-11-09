package io.komune.fixers.gradle.settings

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Runs the plugin in throwaway builds. The f2 catalog comes from a local Maven repository that
 * holds it at this plugin's version, published the way Gradle publishes a version catalog.
 */
class SettingsPluginTest {

    @TempDir
    lateinit var projectDir: File

    @TempDir
    lateinit var repoDir: File

    private val version = FixersSettingsVersion.VALUE

    @Test
    fun `should load the f2 catalog as catalogue at the plugin version`() {
        publishCatalog()
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            val library = extensions.getByType<VersionCatalogsExtension>().named("catalogue")
                .findLibrary("probe").get().get().toString()
            tasks.register("printCatalog") {
                doLast { println("catalogue probe=" + library) }
            }
            """.trimIndent()
        )

        val result = run("printCatalog")

        assertThat(result.output).contains("catalogue probe=io.komune.probe:probe:1.2.3")
    }

    @Test
    fun `should add only the Node and Yarn repositories and keep the project's own in order`() {
        publishCatalog()
        writeSettings(
            repositories = """
                mavenLocal()
                maven { name = "Probe"; url = uri("${repoDir.toURI()}") }
            """.trimIndent(),
            after = """
                val names = dependencyResolutionManagement.repositories.map { it.name }
                println("repositories=" + names.joinToString())
            """.trimIndent(),
        )

        val result = run("help")

        assertThat(result.output).contains("repositories=Node.js, Yarn, MavenLocal, Probe")
    }

    @Test
    fun `should refuse a project catalog that declares another fixers version`() {
        publishCatalog()
        writeSettings()
        File(projectDir, "gradle").mkdirs()
        File(projectDir, "gradle/libs.versions.toml").writeText("[versions]\nfixers = \"0.0.1\"\n")

        val result = runner("help").buildAndFail()

        assertThat(result.output).contains(
            "gradle/libs.versions.toml declares fixers = \"0.0.1\", but the fixers settings plugin is $version"
        )
    }

    @Test
    fun `should read the fixers version from the versions section only`() {
        publishCatalog()
        writeSettings()
        File(projectDir, "gradle").mkdirs()
        File(projectDir, "gradle/libs.versions.toml").writeText(
            "[libraries]\nfixers = \"io.komune:other:0.0.1\"\n\n[versions]\nfixers = \"$version\"\n"
        )

        run("help")
    }

    @Test
    fun `should forbid repositories declared by a project`() {
        publishCatalog()
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText("repositories { mavenCentral() }\n")

        val result = runner("help").buildAndFail()

        assertThat(result.output)
            .contains("Build was configured to prefer settings repositories over project repositories")
    }

    @Test
    fun `should build under the Komune CI init script`() {
        publishCatalog()
        writeSettings(
            after = """
                gradle.settingsEvaluated {
                    println("repositories=" + dependencyResolutionManagement.repositories.map { it.name }.joinToString())
                }
            """.trimIndent(),
        )
        val initScript = File(CI_INIT_SCRIPT).absoluteFile
        check(initScript.exists()) { "$initScript is missing" }

        val result = runner("help", "--init-script", initScript.path).build()

        assertThat(result.output).contains("repositories=Node.js, Yarn, Probe, MavenRepo, FixersGitHubPackages")
    }

    @Test
    fun `should give a build that prefers settings repositories the CI repositories in its settings`() {
        File(projectDir, "settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
                repositories { maven { name = "Probe"; url = uri("${repoDir.toURI()}") } }
            }
            rootProject.name = "probe"
            gradle.settingsEvaluated {
                println("repositories=" + dependencyResolutionManagement.repositories.map { it.name }.joinToString())
            }
            """.trimIndent()
        )
        File(projectDir, "build.gradle.kts").writeText("println(\"projectRepositories=\" + repositories.size)\n")

        val result = runner("help", "--init-script", File(CI_INIT_SCRIPT).absoluteFile.path).build()

        assertThat(result.output)
            .contains("repositories=Probe, MavenRepo, FixersGitHubPackages")
            .contains("projectRepositories=0")
    }

    @Test
    fun `should accept a project catalog that declares the same fixers version`() {
        publishCatalog()
        writeSettings()
        File(projectDir, "gradle").mkdirs()
        File(projectDir, "gradle/libs.versions.toml").writeText("[versions]\nfixers = \"$version\"\n")

        run("help")
    }

    @Test
    fun `should clear the download base urls the Kotlin plugin would turn into project repositories`() {
        publishCatalog()
        writeSettings(
            repositories = """
                mavenCentral()
                maven { name = "Probe"; url = uri("${repoDir.toURI()}") }
            """.trimIndent(),
            pluginRepositories = "gradlePluginPortal()\n        mavenCentral()",
            after = """include("js")""",
        )
        File(projectDir, "js").mkdirs()
        File(projectDir, "js/build.gradle.kts").writeText(
            """
            import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
            import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec

            plugins { kotlin("multiplatform") version "$KOTLIN_VERSION" }
            kotlin { js { browser() } }

            gradle.projectsEvaluated {
                val specs = listOf(project, rootProject).flatMap { p ->
                    listOfNotNull(
                        p.extensions.findByType(NodeJsEnvSpec::class.java)?.let { "node=" + it.downloadBaseUrl.orNull },
                        p.extensions.findByType(YarnRootEnvSpec::class.java)?.let { "yarn=" + it.downloadBaseUrl.orNull },
                    )
                }
                println("downloadBaseUrls=" + specs.sorted())
            }
            """.trimIndent()
        )

        val result = run("help")

        val line = result.output.lines().single { it.startsWith("downloadBaseUrls=") }
        assertThat(line).contains("node=null", "yarn=null").doesNotContain("http")
    }

    private fun writeSettings(
        repositories: String = """maven { name = "Probe"; url = uri("${repoDir.toURI()}") }""",
        pluginRepositories: String = "gradlePluginPortal()",
        after: String = "",
    ) {
        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    $pluginRepositories
                }
            }
            plugins {
                id("io.komune.fixers.gradle.settings")
            }
            dependencyResolutionManagement {
                repositories {
                    $repositories
                }
            }
            rootProject.name = "probe"
            $after
            """.trimIndent()
        )
    }

    /** Lays out `io.komune.f2:f2-gradle-catalog:<version>` with one library, `probe`. */
    private fun publishCatalog() {
        val dir = File(repoDir, "io/komune/f2/f2-gradle-catalog/$version").apply { mkdirs() }
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
    }

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments, "--stacktrace")
        .withPluginClasspath()
        .forwardOutput()

    private fun run(vararg arguments: String): BuildResult = runner(*arguments).build()

    private companion object {
        const val KOTLIN_VERSION = "2.4.21"

        /** The init script the Komune CI action writes to `~/.gradle`, from the module directory. */
        const val CI_INIT_SCRIPT = "../.github/actions/setup-gradle-github-pkg/init.gradle.kts"
    }
}
