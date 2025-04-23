# Integration Tests for Fixers Gradle Plugins

This directory contains integration tests for the Fixers Gradle plugins. These tests verify that the plugins work correctly in real-world scenarios by creating test projects, applying the plugins, and verifying their behavior.

## Test Structure

The integration tests are organized as follows:

- `BaseIntegrationTest.kt`: A base class that provides utilities for creating test projects and running Gradle tasks.
- Plugin-specific test classes in subdirectories:
  - `check/CheckPluginIntegrationTest.kt`: Tests for the CheckPlugin
  - `config/ConfigPluginIntegrationTest.kt`: Tests for the ConfigPlugin
  - `kotlin/KotlinJvmPluginIntegrationTest.kt`: Tests for the KotlinJvmPlugin
  - `kotlin/KotlinMppPluginIntegrationTest.kt`: Tests for the KotlinMppPlugin
  - `npm/NpmPluginIntegrationTest.kt`: Tests for the NpmPlugin
  - `publish/PublishPluginIntegrationTest.kt`: Tests for the PublishPlugin

## What the Tests Verify

The integration tests verify the following aspects of the plugins:

1. **Basic functionality**: Each plugin can be applied to a project and performs its basic functions.
2. **Configuration options**: The plugins correctly handle different configuration options.
3. **Gradle version compatibility**: The plugins work with different Gradle versions (7.3, 7.4, 7.5, 7.6).
4. **Real-world scenarios**: The plugins behave correctly in realistic project setups.

## Running the Tests

You can run the integration tests using Gradle:

```bash
./gradlew :plugin:test --tests "io.komune.fixers.gradle.integration.*"
```

To run tests for a specific plugin:

```bash
./gradlew :plugin:test --tests "io.komune.fixers.gradle.integration.check.*"
./gradlew :plugin:test --tests "io.komune.fixers.gradle.integration.config.*"
./gradlew :plugin:test --tests "io.komune.fixers.gradle.integration.kotlin.*"
./gradlew :plugin:test --tests "io.komune.fixers.gradle.integration.npm.*"
./gradlew :plugin:test --tests "io.komune.fixers.gradle.integration.publish.*"
```

## Adding New Tests

To add a new integration test:

1. Create a new test class that extends `BaseIntegrationTest`.
2. Use the provided utilities to create a test project and set up the build file.
3. Apply the plugin(s) you want to test.
4. Run Gradle tasks to verify the plugin's behavior.
5. Use assertions to check that the plugin behaves as expected.

Example:

```kotlin
class MyPluginIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `should apply MyPlugin and configure something`() {
        // Create source files
        val sourceDir = testProjectDir.resolve("src/main/kotlin").toFile()
        sourceDir.mkdirs()
        File(sourceDir, "Sample.kt").writeText("""
            package com.example
            
            class Sample {
                fun hello() = "Hello, World!"
            }
        """.trimIndent())

        // Set up the build file
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.config")
                id("io.komune.fixers.gradle.my-plugin")
            }
            
            repositories {
                mavenCentral()
            }
            
            fixers {
                // Configure the plugin
            }
            
            // Task to verify the configuration
            tasks.register("verifyConfig") {
                doLast {
                    println("Configuration: ${'$'}{findProperty("some.property")}")
                }
            }
        """.trimIndent())

        // Run the verification task
        val result = runGradle("verifyConfig")

        // Verify the results
        assertThat(result.task(":verifyConfig")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Configuration:")
    }
}
```

## Best Practices

1. **Test real-world scenarios**: Create test projects that resemble real-world usage of the plugins.
2. **Test different configurations**: Verify that the plugins handle different configuration options correctly.
3. **Test with different Gradle versions**: Use the `@ParameterizedTest` and `@ValueSource` annotations to test with different Gradle versions.
4. **Verify task outcomes**: Check that tasks complete with the expected outcome (SUCCESS, UP-TO-DATE, etc.).
5. **Check output files**: Verify that the plugins generate the expected output files.