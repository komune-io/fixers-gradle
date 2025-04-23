package io.komune.fixers.gradle.integration.kotlin

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * Integration tests for the KotlinMppPlugin.
 * Tests the plugin in a real project with different configurations.
 */
class KotlinMppPluginIntegrationTest : BaseIntegrationTest() {

    /**
     * Creates Kotlin source files for multiplatform project.
     */
    private fun createMultiplatformSourceFiles() {
        // Create a simple Kotlin source file for common code
        createCommonSourceFile()

        // Create a simple Kotlin source file for JVM code
        createJvmSourceFile()

        // Create a simple Kotlin source file for JS code
        createJsSourceFile()
    }

    /**
     * Creates a common source file for Kotlin Multiplatform.
     */
    private fun createCommonSourceFile() {
        val commonSourceDir = testProjectDir.resolve("src/commonMain/kotlin").toFile()
        commonSourceDir.mkdirs()
        File(commonSourceDir, "Sample.kt").writeText("""
            package com.example

            expect class Sample() {
                fun hello(): String
            }
        """.trimIndent())
    }

    /**
     * Creates a JVM source file for Kotlin Multiplatform.
     */
    private fun createJvmSourceFile() {
        val jvmSourceDir = testProjectDir.resolve("src/jvmMain/kotlin").toFile()
        jvmSourceDir.mkdirs()
        File(jvmSourceDir, "SampleJvm.kt").writeText("""
            package com.example

            actual class Sample {
                actual fun hello() = "Hello from JVM!"
            }
        """.trimIndent())
    }

    /**
     * Creates a JS source file for Kotlin Multiplatform.
     */
    private fun createJsSourceFile() {
        val jsSourceDir = testProjectDir.resolve("src/jsMain/kotlin").toFile()
        jsSourceDir.mkdirs()
        File(jsSourceDir, "SampleJs.kt").writeText("""
            package com.example

            actual class Sample {
                actual fun hello() = "Hello from JS!"
            }
        """.trimIndent())
    }

    /**
     * Creates a build file for Kotlin Multiplatform project.
     */
    private fun createMultiplatformBuildFile(includeNodeJs: Boolean = true) {
        val nodeJsConfig = if (includeNodeJs) {
            """
                    nodejs()
            """.trimIndent()
        } else {
            ""
        }

        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.mpp")
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
            }

            kotlin {
                jvm()
                js(IR) {
                    browser()
                    $nodeJsConfig
                }
            }

            // Task to verify the configuration
            tasks.register("verifyConfig") {
                doLast {
                    println("Kotlin version: ${'$'}{kotlin.coreLibrariesVersion}")
                    println("Has JVM target: ${'$'}{kotlin.targets.any { it.name == "jvm" }}")
                    println("Has JS target: ${'$'}{kotlin.targets.any { it.name == "js" }}")
                }
            }
        """.trimIndent())
    }

    /**
     * Test that the KotlinMppPlugin applies correctly and configures Kotlin Multiplatform.
     */
    @Test
    fun `should apply KotlinMppPlugin and configure Kotlin Multiplatform`() {
        // Set up the test project
        createMultiplatformSourceFiles()
        createMultiplatformBuildFile()

        // Run the verification task
        val result = runGradle("verifyConfig")

        // Verify that the task completed successfully and Kotlin MPP is configured
        assertThat(result.task(":verifyConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Kotlin version:")
        assertThat(result.output).contains("Has JVM target:")
        assertThat(result.output).contains("Has JS target:")
    }

    /**
     * Verifies that compilation tasks completed successfully.
     */
    private fun verifyCompilationSuccess(result: org.gradle.testkit.runner.BuildResult) {
        // Verify that the tasks completed successfully
        assertThat(result.task(":compileKotlinJvm")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":compileKotlinJs")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    /**
     * Verifies that compiled output files exist.
     */
    private fun verifyCompiledOutputExists() {
        // Verify that the class files were generated
        val jvmClassFile = testProjectDir.resolve("build/classes/kotlin/jvm/main/com/example/Sample.class").toFile()
        assertThat(jvmClassFile.exists()).isTrue()

        // JS compilation produces JS files
        val jsDir = testProjectDir.resolve("build/classes/kotlin/js/main").toFile()
        assertThat(jsDir.exists()).isTrue()
    }

    /**
     * Test that the KotlinMppPlugin correctly compiles Kotlin code for multiple platforms.
     */
    @Test
    fun `should compile Kotlin code for multiple platforms`() {
        // Set up the test project
        createMultiplatformSourceFiles()
        createMultiplatformBuildFile(includeNodeJs = false)

        // Run the compile tasks
        val result = runGradle("compileKotlinJvm", "compileKotlinJs")

        // Verify compilation success and output
        verifyCompilationSuccess(result)
        verifyCompiledOutputExists()
    }
}
