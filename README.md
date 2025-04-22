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

### Maven Repositories

```kotlin
fixers {
    repositories {
        // The default repositories are Sonatype OSS and GitHub
        // You can customize them or add new ones
    }
}
```

## Troubleshooting

### Maven Publication Issues

If you encounter issues with Maven publication, check the following:

1. **Missing Signing Configuration**: If you see a warning like "No signing config provided, skip signing", make sure you have provided the GPG signing key and password:

   ```kotlin
   // In gradle.properties or as environment variables
   GPG_SIGNING_KEY=your-key
   GPG_SIGNING_PASSWORD=your-password
   ```

2. **Repository Authentication**: Ensure you have provided the correct credentials for the Maven repositories:

   ```kotlin
   // In gradle.properties or as environment variables
   PKG_SONATYPE_OSS_USERNAME=your-username
   PKG_SONATYPE_OSS_TOKEN=your-token
   PKG_GITHUB_USERNAME=your-username
   PKG_GITHUB_TOKEN=your-token
   ```

3. **Repository Selection**: You can specify which repository to use for publication:

   ```kotlin
   // In gradle.properties or as environment variables
   PKG_MAVEN_REPO=sonatype_oss
   ```

### Kotlin Multiplatform Issues

If you encounter issues with Kotlin Multiplatform configuration:

1. **JDK Version**: Make sure you have the correct JDK version installed and configured:

   ```kotlin
   fixers {
       jdk {
           version = 17 // Must match your installed JDK
       }
   }
   ```

2. **JS Target**: The MPP plugin automatically configures the JS target. If you need custom configuration, you can apply the MppJsPlugin separately.

### SonarQube Integration Issues

If you encounter issues with SonarQube integration:

1. **Missing Properties**: Ensure you have provided all required SonarQube properties:

   ```kotlin
   fixers {
       sonar {
           url = "https://sonarcloud.io"
           organization = "my-org"
           projectKey = "my-project"
       }
   }
   ```

2. **Detekt Integration**: The Check plugin automatically configures Detekt integration with SonarQube. If you want to disable it:

   ```kotlin
   fixers {
       detekt {
           disable = true
       }
   }
   ```
