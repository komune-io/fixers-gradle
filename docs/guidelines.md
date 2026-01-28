# Fixers Gradle Project Guidelines

This document provides guidelines and information for developers working on the Fixers Gradle project.

## Build/Configuration Instructions

### Prerequisites
- JDK 17 or higher
- Gradle (or use the included Gradle wrapper)

### Building the Project
The project uses a Makefile to simplify common tasks. Here are the main build commands:

```bash
# Build the project
make build

# Run tests
make test

# Lint the code
make lint
```

Under the hood, these commands use Gradle. You can also use Gradle directly:

```bash
# Build the project
./gradlew clean build publishToMavenLocal -x test

# Run tests
./gradlew test
cd sandbox && ./gradlew test

# Lint the code
./gradlew detekt
```

### Project Structure
- `build-composite/`: Contains the core implementation code, including configuration models and plugin implementations
- `plugin/`: Contains the Gradle plugin implementation
- `sandbox/`: Contains test projects for manual testing
- `docs/`: Contains project documentation
- `storybook/`: Contains the Storybook documentation site

## Testing Information

### Running Tests
Tests can be run using the Makefile:

```bash
make test
```

Or directly with Gradle:

```bash
./gradlew test
```

To run a specific test:

```bash
./gradlew test --tests "io.komune.fixers.gradle.check.SimpleTest"
```

### Writing Tests
The project uses JUnit 5 for testing and AssertJ for assertions. There are two main types of tests:

1. **Unit Tests**: Test individual components in isolation
   - Example: `CheckPluginTest.kt`

2. **Integration Tests**: Test plugins in real projects
   - Base class: `BaseIntegrationTest.kt`
   - Example: `KotlinJvmPluginIntegrationTest.kt`

#### Example Test
Here's a simple test example:

```kotlin
package io.komune.fixers.gradle.check

import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class SimpleTest {
    @Test
    fun `simple test that always passes`() {
        // Given
        val input = "Hello"

        // When
        val result = input + " World"

        // Then
        assertThat(result).isEqualTo("Hello World")
    }
}
```

### Integration Testing
For integration tests, extend the `BaseIntegrationTest` class, which provides utilities for creating test projects and running Gradle tasks:

```kotlin
class MyIntegrationTest : BaseIntegrationTest() {
    @Test
    fun `test plugin in real project`() {
        // Create build.gradle.kts
        writeBuildFile("""
            plugins {
                id("io.komune.fixers.gradle.my-plugin")
            }
        """.trimIndent())

        // Run Gradle task
        val result = runGradle("myTask")

        // Assert on result
        assertThat(result.output).contains("Task executed successfully")
    }
}
```

## Additional Development Information

### Documentation
The project includes comprehensive documentation:

- **Configuration Documentation**: The `docs/configuration.md` file provides detailed information about the build-composite configuration model used in Fixers Gradle plugins. It includes information about the `fixers` extension, environment properties, configuration models, and examples.

- **Storybook Documentation**: The project includes a Storybook documentation site in the `storybook/` directory. This provides interactive documentation for the project.

#### Building the Storybook Documentation
You can build the Storybook documentation using:

```bash
make -f infra/script/make_docs.mk build-storybook
```

### Code Style
The project uses [Detekt](https://detekt.github.io/detekt/) for static code analysis. The configuration is in `detekt.yml`.

To check code style:

```bash
./gradlew detekt
```

### Publishing
The project can be published to different repositories:

```bash
# Stage to GitHub Packages
make stage

# Publish to Maven Central
make promote
```

### Version Management
The project version is stored in the `VERSION` file at the root of the project. Update this file to change the version.

### Gradle Compatibility
The project supports multiple Gradle versions. The `KOTLIN_VERSION_MAP` in `BaseIntegrationTest.kt` maps Gradle versions to compatible Kotlin versions.

### Debugging
For debugging Gradle plugins, you can use:

```kotlin
// In your plugin code
project.logger.lifecycle("Debug message: $value")
```

In tests, you can use:

```kotlin
// In your test code
println("[DEBUG_LOG] Debug message: $value")
```
