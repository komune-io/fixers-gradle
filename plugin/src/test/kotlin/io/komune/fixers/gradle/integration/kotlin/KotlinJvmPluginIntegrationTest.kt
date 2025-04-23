package io.komune.fixers.gradle.integration.kotlin

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * Integration tests for the KotlinJvmPlugin.
 * Tests the plugin in a real project with different configurations.
 */
class KotlinJvmPluginIntegrationTest : BaseIntegrationTest() {

    /**
     * Test that the KotlinJvmPlugin applies correctly and configures Kotlin JVM.
     */
    @Test
    fun `should apply KotlinJvmPlugin and configure Kotlin JVM`() {
        // Create a simple Kotlin source file
        val sourceDir = testProjectDir.resolve("src/main/kotlin").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "Sample.kt").writeText("""
            package com.example

            class Sample {
                fun hello() = "Hello, World!"
            }
        """.trimIndent())

        // Set up the build file with the KotlinJvmPlugin
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.jvm")
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

            // Task to verify the configuration
            tasks.register("verifyConfig") {
                doLast {
                    println("Kotlin version: ${'$'}{kotlin.coreLibrariesVersion}")
                    println("Java version: ${'$'}{java.sourceCompatibility}")
                    println("Has kotlin-stdlib dependency: ${'$'}{configurations.compileClasspath.get().dependencies.any { it.name == "kotlin-stdlib" }}")
                }
            }
        """.trimIndent())

        // Run the verification task
        val result = runGradle("verifyConfig")

        // Verify that the task completed successfully and Kotlin JVM is configured
        assertThat(result.task(":verifyConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Kotlin version:")
        assertThat(result.output).contains("Java version:")
    }

    /**
     * Test that the KotlinJvmPlugin correctly compiles Kotlin code.
     */
    @Test
    fun `should compile Kotlin code`() {
        // Create a simple Kotlin source file
        val sourceDir = testProjectDir.resolve("src/main/kotlin").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "Sample.kt").writeText("""
            package com.example

            class Sample {
                fun hello() = "Hello, World!"
            }
        """.trimIndent())

        // Set up the build file with the KotlinJvmPlugin
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.jvm")
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
        """.trimIndent())

        // Run the compileKotlin task
        val result = runGradle("compileKotlin")

        // Verify that the task completed successfully
        assertThat(result.task(":compileKotlin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Verify that the class file was generated
        val classFile = testProjectDir.resolve("build/classes/kotlin/main/com/example/Sample.class").toFile()
        assertThat(classFile.exists()).isTrue()
    }
}
