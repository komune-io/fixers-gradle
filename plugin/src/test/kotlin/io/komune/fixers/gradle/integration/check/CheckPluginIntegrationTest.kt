package io.komune.fixers.gradle.integration.check

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * Integration tests for the CheckPlugin.
 * Tests the plugin in a real project with different configurations.
 */
class CheckPluginIntegrationTest : BaseIntegrationTest() {

    /**
     * Test that the CheckPlugin applies correctly and Detekt runs successfully.
     */
    @Test
    fun `should apply CheckPlugin and run Detekt successfully`() {
        // Create a simple Kotlin source file with no issues
        val sourceDir = testProjectDir.resolve("src/main/kotlin").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "Sample.kt").writeText("""
            package com.example

            class Sample {
                fun hello() = "Hello, World!"
            }
        """.trimIndent())

        // Create a detekt config file
        createFile("detekt.yml", """
            complexity:
              LongParameterList:
                active: true
                threshold: 5
            style:
              MagicNumber:
                active: true
        """.trimIndent())

        // Set up the build file with the CheckPlugin
        writeBuildFile("""
            plugins {
                kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.check")
            }

            repositories {
                mavenCentral()
            }

            fixers {
                detekt {
                    disable = false
                }
            }
        """.trimIndent())

        // Run the detekt task
        val result = runGradle("detektMain")

        // Verify that the task completed successfully
        assertThat(result.task(":detektMain")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    /**
     * Test that the CheckPlugin correctly disables Detekt when configured to do so.
     */
    @Test
    fun `should not run Detekt when disabled`() {
        // Set up the build file with the CheckPlugin and Detekt disabled
        writeBuildFile("""
            plugins {
                kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.check")
            }

            repositories {
                mavenCentral()
            }

            fixers {
                detekt {
                    disable = true
                }
            }
        """.trimIndent())

        // Try to run the detekt task - it should fail because the task doesn't exist
        val result = runGradleAndFail("detektMain")

        // Verify that the task failed because it doesn't exist
        assertThat(result.output).contains("Task 'detektMain' not found")
    }

    /**
     * Test that the SonarQube plugin is applied and configured correctly.
     * 
     * Note: This test only verifies that the SonarQube plugin is applied,
     * without actually running the analysis, to avoid issues with SonarQube
     * scanner dependencies.
     */
    @Test
    fun `should apply and configure SonarQube plugin`() {
        // Set up the build file with the CheckPlugin and SonarQube configuration
        writeBuildFile("""
            plugins {
                kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.check")
            }

            repositories {
                mavenCentral()
            }

            fixers {
                sonar {
                    projectKey = "test-project"
                    organization = "test-org"
                    url = "https://sonarcloud.io"
                }
            }
        """.trimIndent())

        // Just run the tasks command to verify that the sonarqube task is available
        val result = runGradle("tasks")

        // Verify that the sonarqube task is available
        assertThat(result.output).contains("sonar")
    }
}
