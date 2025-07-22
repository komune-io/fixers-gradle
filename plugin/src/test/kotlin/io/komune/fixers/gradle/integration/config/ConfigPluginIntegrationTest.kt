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
