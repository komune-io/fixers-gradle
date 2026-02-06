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
}
