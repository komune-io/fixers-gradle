package io.komune.fixers.gradle.integration.config

import io.komune.fixers.gradle.integration.BaseIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

/**
 * Integration tests for the ConfigPlugin.
 * Tests the plugin in a real project with different configurations.
 */
class ConfigPluginIntegrationTest : BaseIntegrationTest() {

    /**
     * Test that the ConfigPlugin applies correctly and the fixers extension is available.
     */
    @Test
    fun `should apply ConfigPlugin and configure fixers extension`() {
        // Set up the build file with the ConfigPlugin
        writeBuildFile("""
            plugins {
                kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                id("io.komune.fixers.gradle.config")
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
                    // The fixers extension is registered on the root project
                    val fixersExt = rootProject.extensions.getByName("fixers")
                    println("Fixers extension found: ${'$'}{fixersExt}")

                    // Access the extension properties directly
                    val config = fixersExt as io.komune.fixers.gradle.config.ConfigExtension
                    println("Bundle ID: ${'$'}{config.bundle.id.get()}")
                }
            }
        """.trimIndent())

        // Run the verification task
        val result = runGradle("verifyConfig")

        // Verify that the task completed successfully and the fixers extension is configured
        assertThat(result.task(":verifyConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Fixers extension found")
        assertThat(result.output).contains("Bundle ID: test-bundle")
    }

    /**
     * Test that subproject can override root project configuration.
     * Subproject values should be preserved, and missing values should inherit from root.
     */
    @Test
    fun `should allow subproject to override root configuration`() {
        // Set up multi-project build
        settingsFile.writeText("""
            rootProject.name = "integration-test-project"
            include("subproject")
        """.trimIndent())

        // Root project configuration
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
            }

            fixers {
                bundle {
                    name.set("root-project-name")
                    description.set("Root project description")
                    url.set("https://github.com/komune-io/root")
                }
                jdk {
                    version.set(17)
                }
            }
        """.trimIndent())

        // Create subproject directory
        val subprojectDir = testProjectDir.resolve("subproject").toFile()
        subprojectDir.mkdirs()

        // Subproject configuration - overrides name but inherits description and url
        val subprojectBuildFile = subprojectDir.resolve("build.gradle.kts")
        subprojectBuildFile.writeText("""
            plugins {
                id("io.komune.fixers.gradle.config")
            }

            // Get a reference to the config during configuration phase
            val fixersConfig = extensions.findByName("fixers") as io.komune.fixers.gradle.config.ConfigExtension

            fixers {
                bundle {
                    name.set("subproject-override-name")
                    // description NOT set - should inherit from root
                    // url NOT set - should inherit from root
                }
                // jdk NOT set - should inherit from root
            }

            // Task to verify the configuration inheritance
            tasks.register("verifyOverride") {
                doLast {
                    // Use the config reference captured at configuration time
                    println("Subproject bundle name: ${'$'}{fixersConfig.bundle.name.get()}")
                    println("Subproject bundle description: ${'$'}{fixersConfig.bundle.description.orNull}")
                    println("Subproject bundle url: ${'$'}{fixersConfig.bundle.url.orNull}")
                    println("Subproject jdk version: ${'$'}{fixersConfig.jdk.version.orNull}")
                }
            }
        """.trimIndent())

        // Run the verification task on subproject
        val result = runGradle(":subproject:verifyOverride")

        // Verify that subproject override is preserved
        assertThat(result.output).contains("Subproject bundle name: subproject-override-name")
        // Verify that unset values inherit from root
        assertThat(result.output).contains("Subproject bundle description: Root project description")
        assertThat(result.output).contains("Subproject bundle url: https://github.com/komune-io/root")
        assertThat(result.output).contains("Subproject jdk version: 17")
    }

    /**
     * Test that the ConfigPlugin correctly configures all extension properties.
     */
    @Test
    fun `should configure all extension properties`() {
        // Set up the build file with the ConfigPlugin and all extension properties
        writeBuildFile("""
            plugins {
                kotlin("jvm") version "${getCompatibleKotlinVersion(null)}"
                id("io.komune.fixers.gradle.config")
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

                detekt {
                    disable = false
                }

                sonar {
                    projectKey = "test-project"
                    organization = "test-org"
                    url = "https://sonarcloud.io"
                    language = "kotlin"
                }

                kt2Ts {
                    outputDirectory = "generated/ts"
                    inputDirectory = "src/main/kotlin"
                }
            }

            // Task to verify the configuration
            tasks.register("verifyConfig") {
                doLast {
                    // Access the extension properties directly
                    val config = rootProject.extensions.getByName("fixers") as io.komune.fixers.gradle.config.ConfigExtension
                    println("Bundle ID: ${'$'}{config.bundle.id.get()}")
                    println("Detekt disabled: ${'$'}{config.detekt.disable.get()}")
                    println("Sonar project key: ${'$'}{config.sonar.projectKey.get()}")
                    println("Kt2Ts output directory: ${'$'}{config.kt2Ts.outputDirectory.get()}")
                }
            }
        """.trimIndent())

        // Run the verification task
        val result = runGradle("verifyConfig")

        // Verify that all properties are correctly configured
        assertThat(result.output).contains("Bundle ID: test-bundle")
        assertThat(result.output).contains("Detekt disabled: false")
        assertThat(result.output).contains("Sonar project key: test-project")
        assertThat(result.output).contains("Kt2Ts output directory: generated/ts")
    }
}
