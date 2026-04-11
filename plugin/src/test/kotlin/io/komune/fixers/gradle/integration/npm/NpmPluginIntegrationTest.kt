package io.komune.fixers.gradle.integration.npm

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * Integration tests for the NpmPlugin.
 * Tests the plugin in a real project with different configurations.
 */
class NpmPluginIntegrationTest : BaseIntegrationTest() {

    /**
     * Creates a simple Kotlin/JS source file for testing.
     */
    private fun createJsSourceFile() {
        val sourceDir = testProjectDir.resolve("src/jsMain/kotlin").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "Sample.kt").writeText("""
            package com.example

            class Sample {
                fun hello() = "Hello from JS!"
            }
        """.trimIndent())
    }

    /**
     * Creates a basic package.json file for testing.
     */
    private fun createBasicPackageJson() {
        createFile("package.json", """
            {
              "name": "test-project",
              "version": "1.0.0",
              "description": "Test project for NpmPlugin integration test",
              "main": "index.js",
              "scripts": {
                "test": "echo \"Error: no test specified\" && exit 1"
              },
              "author": "",
              "license": "ISC"
            }
        """.trimIndent())
    }

    /**
     * Creates a build file with the NpmPlugin.
     */
    private fun createNpmBuildFile() {
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.mpp")
                id("io.komune.fixers.gradle.npm")
            }

            repositories {
                mavenCentral()
            }

            fixers {
                bundle {
                    id = "test-bundle"
                    name = "Test Bundle"
                    description = "A test bundle for integration testing"
                    url = "https://github.com/komune-io/fixers-gradle"
                }
                npm {
                    // Configure npm properties
                    publish = true
                    organization = "test-organization"
                }
            }

            kotlin {
                js(IR) {
                    browser()
                    nodejs()
                }
            }

            // Task to verify the configuration
            tasks.register("verifyConfig") {
                doLast {
                    println("NPM package.json: ${'$'}{findProperty("fixers.npm.packageJson")}")
                    println("Has npmInstall task: ${'$'}{tasks.findByName("npmInstall") != null}")
                }
            }
        """.trimIndent())
    }

    /**
     * Verifies that NPM integration is correctly configured.
     */
    private fun verifyNpmIntegration(result: org.gradle.testkit.runner.BuildResult) {
        assertThat(result.task(":verifyConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("NPM package.json:")
        assertThat(result.output).contains("Has npmInstall task:")
    }

    /**
     * Test that the NpmPlugin applies correctly and configures NPM integration.
     */
    @Test
    fun `should apply NpmPlugin and configure NPM integration`() {
        // Set up the test project
        createJsSourceFile()
        createBasicPackageJson()
        createNpmBuildFile()

        // Run the verification task
        val result = runGradle("verifyConfig")

        // Verify that NPM integration is configured
        verifyNpmIntegration(result)
    }

    /**
     * Creates a package.json file with dependencies for testing.
     */
    private fun createPackageJsonWithDependencies() {
        createFile("package.json", """
            {
              "name": "test-project",
              "version": "1.0.0",
              "description": "Test project for NpmPlugin integration test",
              "main": "index.js",
              "dependencies": {
                "lodash": "^4.17.21"
              },
              "devDependencies": {
                "webpack": "^5.75.0"
              },
              "author": "",
              "license": "ISC"
            }
        """.trimIndent())
    }

    /**
     * Creates a build file with NPM dependency configuration.
     */
    private fun createNpmDependenciesBuildFile() {
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.mpp")
                id("io.komune.fixers.gradle.npm")
            }

            repositories {
                mavenCentral()
            }

            fixers {
                bundle {
                    id = "test-bundle"
                    name = "Test Bundle"
                    description = "A test bundle for integration testing"
                    url = "https://github.com/komune-io/fixers-gradle"
                }
                npm {
                    // Configure npm properties
                    publish = true
                    organization = "test-organization"
                    version = "1.0.0"
                }
            }

            kotlin {
                js(IR) {
                    browser()
                    nodejs()
                }
            }

            // Task to verify the configuration
            tasks.register("verifyNpmDependencies") {
                doLast {
                    val packageJsonFile = file("package.json")
                    val packageJsonContent = packageJsonFile.readText()
                    println("Package.json content: ${'$'}{packageJsonContent}")

                    // Check if dependencies were added
                    println("Has lodash dependency: ${'$'}{packageJsonContent.contains("\"lodash\"")}")
                    println("Has react dependency: ${'$'}{packageJsonContent.contains("\"react\"")}")
                    println("Has webpack devDependency: ${'$'}{packageJsonContent.contains("\"webpack\"")}")
                    println("Has typescript devDependency: ${'$'}{packageJsonContent.contains("\"typescript\"")}")
                }
            }
        """.trimIndent())
    }

    /**
     * Verifies that NPM dependencies are correctly configured.
     */
    private fun verifyNpmDependencies(result: org.gradle.testkit.runner.BuildResult) {
        assertThat(result.task(":verifyNpmDependencies")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Has lodash dependency: true")
        assertThat(result.output).contains("Has webpack devDependency: true")
    }

    /**
     * Test that the NpmPlugin correctly configures NPM dependencies.
     */
    @Test
    fun `should configure NPM dependencies`() {
        // Set up the test project
        createJsSourceFile()
        createPackageJsonWithDependencies()
        createNpmDependenciesBuildFile()

        // Run the verification task
        val result = runGradle("verifyNpmDependencies")

        // Verify that NPM dependencies are configured
        verifyNpmDependencies(result)
    }

    /**
     * Creates a build file that exposes the publish tag configured by NpmPlugin on
     * each NpmPublishTask. The project version is injected by the caller via
     * `-PprojectVersion=...` so the test can drive prerelease vs release behavior. If
     * [npmTag] is non-null, it is set in the `fixers { npm { tag } }` DSL to exercise the
     * configurable-dist-tag override.
     */
    private fun createPublishTagBuildFile(npmTag: String? = null) {
        val tagLine = if (npmTag != null) "tag = \"$npmTag\"" else ""
        writeBuildFile("""
            import dev.petuska.npm.publish.task.NpmPublishTask

            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.mpp")
                id("io.komune.fixers.gradle.npm")
            }

            repositories {
                mavenCentral()
            }

            version = providers.gradleProperty("projectVersion").get()

            fixers {
                bundle {
                    id = "test-bundle"
                    name = "Test Bundle"
                    description = "A test bundle for integration testing"
                    url = "https://github.com/komune-io/fixers-gradle"
                }
                npm {
                    publish = true
                    organization = "test-organization"
                    $tagLine
                }
            }

            kotlin {
                js(IR) {
                    browser()
                    nodejs()
                }
            }

            tasks.register("verifyPublishTag") {
                val publishTasks = tasks.withType(NpmPublishTask::class.java)
                doLast {
                    println("NpmPublishTask count: ${'$'}{publishTasks.size}")
                    publishTasks.forEach { task ->
                        val tagValue = if (task.tag.isPresent) task.tag.get() else "<unset>"
                        println("NpmPublishTask ${'$'}{task.name} tag: ${'$'}tagValue")
                    }
                }
            }
        """.trimIndent())
    }

    /**
     * Test that for prerelease project versions (containing `-`, e.g. `0.35.0-SNAPSHOT.cae20d5`),
     * NpmPlugin sets the `tag` on every NpmPublishTask to "next" so that `npm publish` receives
     * the required `--tag=next` flag. Without this, npm 7+ fails with:
     *   "You must specify a tag using --tag when publishing a prerelease version."
     */
    @Test
    fun `should set publish tag to next for prerelease version`() {
        createJsSourceFile()
        createBasicPackageJson()
        createPublishTagBuildFile()

        val result = runGradle("verifyPublishTag", "-PprojectVersion=0.35.0-SNAPSHOT.cae20d5")

        assertThat(result.task(":verifyPublishTag")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        // There must be at least one NpmPublishTask registered by the KotlinJs target integration.
        assertThat(result.output).doesNotContain("NpmPublishTask count: 0")
        // Every registered NpmPublishTask must have been configured with the "next" tag.
        assertThat(result.output).contains("tag: next")
        assertThat(result.output).doesNotContain("tag: <unset>")
    }

    /**
     * Test that for release project versions (no `-`, e.g. `0.35.0`), NpmPlugin does NOT set
     * a publish tag, letting `npm publish` use its default "latest" dist-tag.
     */
    @Test
    fun `should not set publish tag for release version`() {
        createJsSourceFile()
        createBasicPackageJson()
        createPublishTagBuildFile()

        val result = runGradle("verifyPublishTag", "-PprojectVersion=0.35.0")

        assertThat(result.task(":verifyPublishTag")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).doesNotContain("NpmPublishTask count: 0")
        // Every registered NpmPublishTask must be left with the tag unset (npm default "latest").
        assertThat(result.output).contains("tag: <unset>")
        assertThat(result.output).doesNotContain("tag: next")
    }

    /**
     * Test that a user can override the default prerelease dist-tag ("next") via the
     * `fixers { npm { tag = "..." } }` DSL. The plugin must honor that value on every
     * registered NpmPublishTask instead of falling back to "next".
     */
    @Test
    fun `should use configured npm tag override for prerelease version`() {
        createJsSourceFile()
        createBasicPackageJson()
        createPublishTagBuildFile(npmTag = "rc")

        val result = runGradle("verifyPublishTag", "-PprojectVersion=0.35.0-SNAPSHOT.cae20d5")

        assertThat(result.task(":verifyPublishTag")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).doesNotContain("NpmPublishTask count: 0")
        // Every registered NpmPublishTask must pick up the user-configured override, not the default.
        assertThat(result.output).contains("tag: rc")
        assertThat(result.output).doesNotContain("tag: next")
        assertThat(result.output).doesNotContain("tag: <unset>")
    }
}
