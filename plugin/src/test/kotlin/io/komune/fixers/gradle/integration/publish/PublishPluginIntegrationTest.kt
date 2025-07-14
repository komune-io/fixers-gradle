package io.komune.fixers.gradle.integration.publish

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * Integration tests for the PublishPlugin.
 * Tests the plugin in a real project with different configurations.
 */
class PublishPluginIntegrationTest : BaseIntegrationTest() {

    /**
     * Creates a simple Kotlin source file for testing.
     */
    private fun createSimpleKotlinSourceFile() {
        val sourceDir = testProjectDir.resolve("src/main/kotlin").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "Sample.kt").writeText("""
            package com.example

            class Sample {
                fun hello() = "Hello, World!"
            }
        """.trimIndent())
    }

    /**
     * Creates a build file with the PublishPlugin for JVM projects.
     */
    private fun createJvmPublishBuildFile() {
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.jvm")
                id("io.komune.fixers.gradle.publish")
            }

            repositories {
                mavenCentral()
            }

            group = "com.example"
            version = "1.0.0"

            publishing {
                publications {
                    create<org.gradle.api.publish.maven.MavenPublication>("maven") {
                        from(components["java"])
                        pom {
                            licenses {
                                license {
                                    name.set("The Apache License, Version 2.0")
                                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                }
                            }
                            developers {
                                developer {
                                    id.set("test-developer")
                                    name.set("Test Developer")
                                    email.set("test@example.com")
                                }
                            }
                            scm {
                                url.set("https://github.com/komune-io/fixers-gradle")
                            }
                        }
                    }
                }
            }

            // Task to verify the configuration
            tasks.register("verifyConfig") {
                doLast {
                    println("Has maven-publish plugin: ${'$'}{plugins.hasPlugin("maven-publish")}")
                    println("Has publications: ${'$'}{publishing.publications.names}")
                    println("Has repositories: ${'$'}{publishing.repositories.names}")
                }
            }
        """.trimIndent())
    }

    /**
     * Verifies that Maven publishing is correctly configured.
     */
    private fun verifyMavenPublishingConfig(result: org.gradle.testkit.runner.BuildResult) {
        assertThat(result.task(":verifyConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Has maven-publish plugin: true")
        assertThat(result.output).contains("Has publications:")
        assertThat(result.output).contains("Has repositories:")
    }

    /**
     * Test that the PublishPlugin applies correctly and configures Maven publishing.
     */
    @Test
    fun `should apply PublishPlugin and configure Maven publishing`() {
        // Set up the test project
        createSimpleKotlinSourceFile()
        createJvmPublishBuildFile()

        // Run the verification task
        val result = runGradle("verifyConfig")

        // Verify that Maven publishing is configured
        verifyMavenPublishingConfig(result)
    }

    /**
     * Creates Kotlin source files for multiplatform project.
     */
    private fun createMultiplatformSourceFiles() {
        // Create a simple Kotlin source file for common code
        createCommonSourceFile()

        // Create a simple Kotlin source file for JVM code
        createJvmImplementationFile()

        // Create a simple Kotlin source file for JS code
        createJsImplementationFile()
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
     * Creates a JVM implementation file for Kotlin Multiplatform.
     */
    private fun createJvmImplementationFile() {
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
     * Creates a JS implementation file for Kotlin Multiplatform.
     */
    private fun createJsImplementationFile() {
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
     * Creates a build file for Kotlin Multiplatform project with publishing.
     */
    private fun createMppPublishBuildFile() {
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.mpp")
                id("io.komune.fixers.gradle.publish")
            }

            repositories {
                mavenCentral()
            }

            group = "com.example"
            version = "1.0.0"

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
                }
            }

            // Task to verify the configuration
            tasks.register("verifyMppPublishing") {
                doLast {
                    println("Has maven-publish plugin: ${'$'}{plugins.hasPlugin("maven-publish")}")
                    println("Has publications: ${'$'}{publishing.publications.names}")
                    println("Has kotlinMultiplatform publication: ${'$'}{publishing.publications.findByName("kotlinMultiplatform") != null}")
                    println("Has jvm publication: ${'$'}{publishing.publications.findByName("jvm") != null}")
                    println("Has js publication: ${'$'}{publishing.publications.findByName("js") != null}")
                }
            }
        """.trimIndent())
    }

    /**
     * Verifies that MPP publishing is correctly configured.
     */
    private fun verifyMppPublishingConfig(result: org.gradle.testkit.runner.BuildResult) {
        assertThat(result.task(":verifyMppPublishing")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Has maven-publish plugin: true")
        assertThat(result.output).contains("Has kotlinMultiplatform publication: true")
        assertThat(result.output).contains("Has jvm publication: true")
        assertThat(result.output).contains("Has js publication: true")
    }

    /**
     * Test that the PublishPlugin correctly configures publishing for a Kotlin Multiplatform project.
     */
    @Test
    fun `should configure publishing for Kotlin Multiplatform project`() {
        // Set up the test project
        createMultiplatformSourceFiles()
        createMppPublishBuildFile()

        // Run the verification task
        val result = runGradle("verifyMppPublishing")

        // Verify that MPP publishing is configured
        verifyMppPublishingConfig(result)
    }

    /**
     * Creates a build file for a Gradle plugin project with publishing.
     */
    private fun createGradlePluginPublishBuildFile() {
        writeBuildFile(getGradlePluginBuildFileContent())
        createTestPluginImplementation()
    }

    /**
     * Generates the content for the Gradle plugin build file.
     */
    private fun getGradlePluginBuildFileContent(): String {
        return """
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.kotlin.jvm")
                id("io.komune.fixers.gradle.publish")
                id("com.gradle.plugin-publish") version "1.2.1"
            }

            repositories {
                mavenCentral()
            }

            group = "com.example"
            version = "1.0.0"

            fixers {
                bundle {
                    id = "test-bundle"
                    name = "Test Bundle"
                    description = "A test bundle for integration testing"
                    url = "https://github.com/komune-io/fixers-gradle"
                }
            }

            gradlePlugin {
                plugins {
                    create("testPlugin") {
                        id = "com.example.test-plugin"
                        implementationClass = "com.example.TestPlugin"
                        displayName = "Test Plugin"
                        description = "A test plugin for integration testing"
                        tags.set(listOf("test", "example"))
                    }
                }
            }

            // Configure the publishing extension
            fixers {
                publish {
                    gradlePlugin.set(listOf("testPluginPluginMarkerMaven"))
                }
            }

            // Task to verify the configuration
            tasks.register("verifyGradlePluginPublishing") {
                doLast {
                    println("Has maven-publish plugin: ${'$'}{plugins.hasPlugin("maven-publish")}")
                    println("Has gradle-plugin-publish plugin: ${'$'}{plugins.hasPlugin("com.gradle.plugin-publish")}")
                    println("Has publications: ${'$'}{publishing.publications.names}")
                    println("Has pluginMaven publication: ${'$'}{publishing.publications.findByName("pluginMaven") != null}")
                    println("Has testPluginPluginMarkerMaven publication: ${'$'}{publishing.publications.findByName("testPluginPluginMarkerMaven") != null}")
                }
            }
        """.trimIndent()
    }

    /**
     * Creates the TestPlugin implementation class file.
     */
    private fun createTestPluginImplementation() {
        val sourceDir = testProjectDir.resolve("src/main/kotlin/com/example").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "TestPlugin.kt").writeText("""
            package com.example

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class TestPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.register("testPluginTask") {
                        doLast {
                            println("Test plugin task executed")
                        }
                    }
                }
            }
        """.trimIndent())
    }

    /**
     * Verifies that Gradle plugin publishing is correctly configured.
     */
    private fun verifyGradlePluginPublishingConfig(result: org.gradle.testkit.runner.BuildResult) {
        assertThat(result.task(":verifyGradlePluginPublishing")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Has maven-publish plugin: true")
        assertThat(result.output).contains("Has gradle-plugin-publish plugin: true")
        assertThat(result.output).contains("Has pluginMaven publication: true")
        assertThat(result.output).contains("Has testPluginPluginMarkerMaven publication: true")
    }

    /**
     * Test that the PublishPlugin correctly configures publishing for a Gradle plugin project.
     */
    @Test
    fun `should configure publishing for Gradle plugin project`() {
        // Set up the test project
        createGradlePluginPublishBuildFile()

        // Run the verification task
        val result = runGradle("verifyGradlePluginPublishing")

        // Verify that Gradle plugin publishing is configured
        verifyGradlePluginPublishingConfig(result)
    }
}
