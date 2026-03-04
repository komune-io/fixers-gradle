package io.komune.fixers.gradle.integration.publish

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * Integration tests for the PublishPlugin.
 * Tests use multi-project builds to match real-world usage where PublishPlugin
 * is applied on subprojects (not the root project).
 */
class PublishPluginIntegrationTest : BaseIntegrationTest() {

    /**
     * Sets up a multi-project build with a "lib" subproject.
     * The root applies the config plugin; the subproject applies publish + kotlin.
     */
    private fun setupMultiProject(subprojectBuildContent: String, rootBuildExtra: String = "") {
        settingsFile.writeText("""
            rootProject.name = "integration-test-project"
            include("lib")
        """.trimIndent())

        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
            }
            $rootBuildExtra
        """.trimIndent())

        val libDir = testProjectDir.resolve("lib").toFile()
        libDir.mkdirs()
        File(libDir, "build.gradle.kts").writeText(subprojectBuildContent)
    }

    /**
     * Creates a simple Kotlin source file for testing in the lib subproject.
     */
    private fun createSimpleKotlinSourceFile() {
        val sourceDir = testProjectDir.resolve("lib/src/main/kotlin").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "Sample.kt").writeText("""
            package com.example

            class Sample {
                fun hello() = "Hello, World!"
            }
        """.trimIndent())
    }

    /**
     * Creates a JVM subproject build file with the PublishPlugin.
     */
    private fun createJvmPublishProject() {
        setupMultiProject("""
            plugins {
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
        assertThat(result.task(":lib:verifyConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Has maven-publish plugin: true")
        assertThat(result.output).contains("Has publications:")
        assertThat(result.output).contains("Has repositories:")
    }

    /**
     * Test that the PublishPlugin applies correctly and configures Maven publishing.
     */
    @Test
    fun `should apply PublishPlugin and configure Maven publishing`() {
        createSimpleKotlinSourceFile()
        createJvmPublishProject()

        val result = runGradle(":lib:verifyConfig")

        verifyMavenPublishingConfig(result)
    }

    /**
     * Creates Kotlin source files for multiplatform project in the lib subproject.
     */
    private fun createMultiplatformSourceFiles() {
        val commonSourceDir = testProjectDir.resolve("lib/src/commonMain/kotlin").toFile()
        commonSourceDir.mkdirs()
        File(commonSourceDir, "Sample.kt").writeText("""
            package com.example

            expect class Sample() {
                fun hello(): String
            }
        """.trimIndent())

        val jvmSourceDir = testProjectDir.resolve("lib/src/jvmMain/kotlin").toFile()
        jvmSourceDir.mkdirs()
        File(jvmSourceDir, "SampleJvm.kt").writeText("""
            package com.example

            actual class Sample {
                actual fun hello() = "Hello from JVM!"
            }
        """.trimIndent())

        val jsSourceDir = testProjectDir.resolve("lib/src/jsMain/kotlin").toFile()
        jsSourceDir.mkdirs()
        File(jsSourceDir, "SampleJs.kt").writeText("""
            package com.example

            actual class Sample {
                actual fun hello() = "Hello from JS!"
            }
        """.trimIndent())
    }

    /**
     * Creates a Kotlin Multiplatform subproject with publishing.
     */
    private fun createMppPublishProject() {
        val rootExtra = """
            fixers {
                bundle {
                    id = "test-bundle"
                    name = "Test Bundle"
                    description = "A test bundle for integration testing"
                    url = "https://github.com/komune-io/fixers-gradle"
                }
            }
        """.trimIndent()

        setupMultiProject("""
            plugins {
                id("io.komune.fixers.gradle.kotlin.mpp")
                id("io.komune.fixers.gradle.publish")
            }

            repositories {
                mavenCentral()
            }

            group = "com.example"
            version = "1.0.0"

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
        """.trimIndent(), rootExtra)
    }

    /**
     * Verifies that MPP publishing is correctly configured.
     */
    private fun verifyMppPublishingConfig(result: org.gradle.testkit.runner.BuildResult) {
        assertThat(result.task(":lib:verifyMppPublishing")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
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
        createMultiplatformSourceFiles()
        createMppPublishProject()

        val result = runGradle(":lib:verifyMppPublishing")

        verifyMppPublishingConfig(result)
    }

    /**
     * Creates a build file for a Gradle plugin project with publishing.
     * This remains a single-project build since com.gradle.plugin-publish
     * internally applies maven-publish, making the publishing DSL available.
     */
    private fun createGradlePluginPublishBuildFile() {
        writeBuildFile("""
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

            fixers {
                publish {
                    gradlePlugin.set(listOf("testPluginPluginMarkerMaven"))
                }
            }

            tasks.register("verifyGradlePluginPublishing") {
                doLast {
                    println("Has maven-publish plugin: ${'$'}{plugins.hasPlugin("maven-publish")}")
                    println("Has gradle-plugin-publish plugin: ${'$'}{plugins.hasPlugin("com.gradle.plugin-publish")}")
                    println("Has publications: ${'$'}{publishing.publications.names}")
                    println("Has pluginMaven publication: ${'$'}{publishing.publications.findByName("pluginMaven") != null}")
                    println("Has testPluginPluginMarkerMaven publication: ${'$'}{publishing.publications.findByName("testPluginPluginMarkerMaven") != null}")
                }
            }
        """.trimIndent())
        createTestPluginSource()
    }

    private fun createTestPluginSource() {
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
        createGradlePluginPublishBuildFile()

        val result = runGradle("verifyGradlePluginPublishing")

        verifyGradlePluginPublishingConfig(result)
    }
}
