# Fixers Gradle 

Gradle plugins to facilitate the configuration of Kotlin modules. These plugins provide sensible defaults and simplify the setup of Kotlin JVM and Multiplatform projects, Maven publication, and static code analysis.

## Table of Contents
- [Installation](#installation)
- [Plugins](#plugins)
  - [io.komune.fixers.gradle.config](#iokomune-fixers-gradle-config)
  - [io.komune.fixers.gradle.dependencies](#iokomune-fixers-gradle-dependencies)
  - [io.komune.fixers.gradle.kotlin.jvm](#iokomune-fixers-gradle-kotlin-jvm)
  - [io.komune.fixers.gradle.kotlin.mpp](#iokomune-fixers-gradle-kotlin-mpp)
  - [io.komune.fixers.gradle.publish](#iokomune-fixers-gradle-publish)
  - [io.komune.fixers.gradle.npm](#iokomune-fixers-gradle-npm)
  - [io.komune.fixers.gradle.check](#iokomune-fixers-gradle-check)
- [Configuration Options](#configuration-options)
- [Troubleshooting](#troubleshooting)
- [Project Structure](#project-structure)

## Installation

To use the Fixers Gradle plugins in your project, add the following to your `settings.gradle.kts` file:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Then, in your `build.gradle.kts` file, apply the desired plugins:

```kotlin
plugins {
    id("io.komune.fixers.gradle.config") version "x.y.z"
    // Add other plugins as needed
}
```

Replace `x.y.z` with the latest version of the plugins.

## Plugins

### io.komune.fixers.gradle.config

The Config plugin provides a central configuration for all Fixers Gradle plugins. It creates a `fixers` extension that can be used to configure various aspects of your project.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.config") version "x.y.z"
}

fixers {
    bundle {
        name = "my-project"
        description = "My awesome project"
        version = "1.0.0"
        url = "https://github.com/my-org/my-project"
    }

    jdk {
        version = 17
    }

    // Additional configuration options
}
```

### io.komune.fixers.gradle.dependencies

The Dependencies plugin registers Fixers dependencies versions for use in your project.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.dependencies") version "x.y.z"
}

dependencies {
    implementation(FixersDependencies.Jvm.Kotlin.coroutines)
    testImplementation(FixersDependencies.Jvm.Test.junit)
}
```

### io.komune.fixers.gradle.kotlin.jvm

The JVM plugin simplifies the configuration of Kotlin JVM projects. It applies the Java and Kotlin JVM plugins and configures them with sensible defaults.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.kotlin.jvm") version "x.y.z"
}

// The plugin automatically configures:
// - JDK version (from fixers.jdk.version or default)
// - Kotlin compiler options
// - Default dependencies (Kotlin Reflect, Coroutines, JUnit)
```

### io.komune.fixers.gradle.kotlin.mpp

The MPP plugin simplifies the configuration of Kotlin Multiplatform projects. It sets up common, JVM, and JS targets with appropriate dependencies.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.kotlin.mpp") version "x.y.z"
}

// The plugin automatically configures:
// - Kotlin Multiplatform with common, JVM, and JS targets
// - Default dependencies for each target
// - Compiler options
```

### io.komune.fixers.gradle.publish

The Publish plugin simplifies the configuration of Maven publication. It applies the Maven Publish and Signing plugins and configures them with sensible defaults.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.publish") version "x.y.z"
}

fixers {
    bundle {
        name = "my-project"
        description = "My awesome project"
        version = "1.0.0"
        url = "https://github.com/my-org/my-project"
    }

    // Publication will be configured automatically based on the bundle information
}
```

### io.komune.fixers.gradle.npm

The NPM plugin simplifies the configuration of NPM publication for Kotlin/JS projects.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.npm") version "x.y.z"
}

fixers {
    npm {
        organization = "my-org"
        publish = true
        clean = true
        version = "1.0.0"
    }
}
```

### io.komune.fixers.gradle.check

The Check plugin simplifies the configuration of static code analysis with SonarQube and Detekt. It also configures JaCoCo for code coverage reporting.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.check") version "x.y.z"
}

fixers {
    sonar {
        organization = "my-org"
        projectKey = "my-project"
        url = "https://sonarcloud.io"
    }

    detekt {
        // Detekt configuration
    }
}
```

## Configuration Options

The Fixers Gradle plugins provide a central configuration through the `fixers` extension. Here are the available configuration options:

### Bundle

```kotlin
fixers {
    bundle {
        name = "my-project"
        description = "My awesome project"
        version = "1.0.0"
        url = "https://github.com/my-org/my-project"
    }
}
```

### JDK

```kotlin
fixers {
    jdk {
        version = 17 // Default is 17
    }
}
```

### Kotlin to TypeScript Conversion

```kotlin
fixers {
    kt2Ts {
        outputDirectory = "platform/web/kotlin" // Default
        inputDirectory = "src/jsMain/kotlin"
        additionalCleaning = mapOf(
            "fileName" to listOf(
                Regex("pattern") to "replacement"
            )
        )
    }
}
```

### SonarQube

```kotlin
fixers {
    sonar {
        url = "https://sonarcloud.io"
        organization = "my-org"
        projectKey = "my-project"
        language = "kotlin"
        detekt = "build/reports/detekt/detekt.xml"
        jacoco = "${rootDir}/**/build/reports/jacoco/test/jacocoTestReport.xml"
        exclusions = "**/*.java"
        githubSummaryComment = "true"
    }
}
```

### NPM Publication

```kotlin
fixers {
    npm {
        publish = true
        organization = "my-org"
        clean = true
        version = "1.0.0"
    }
}
```


## Project Structure

This project uses a composite build to facilitate the development and testing of the Gradle plugins. Here's a brief overview of the project structure:

- **`build-composite`**: This directory contains the source code for all the plugins as "convention plugins". This allows for a better developer experience within this repository, including features like code completion and easy navigation in the IDE.

- **`config`, `dependencies`, `plugin`**: These are standard Gradle subprojects that are configured to be published to a Maven repository. They don't contain any source code directly. Instead, the source code is copied from the `build-composite` directory during the build process.

This setup allows for the local development and testing of the plugins in a streamlined way, while also enabling the publication of the plugins as standard, independent artifacts for other projects to use.
