# Fixers Gradle

Gradle plugins to facilitate the configuration of Kotlin modules. These plugins provide sensible defaults and simplify the setup of Kotlin JVM and Multiplatform projects, Maven/NPM publication, and static code analysis.

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

> **Note:** The `FixersDependencies` API is deprecated. Use the Fixers version catalog instead:
>
> ```kotlin
> // In settings.gradle.kts
> dependencyResolutionManagement {
>     versionCatalogs {
>         create("fixers") {
>             from("io.komune.fixers.gradle:catalog:x.y.z")
>         }
>     }
> }
>
> // In build.gradle.kts
> dependencies {
>     implementation(fixers.bundles.kotlin.coroutines.jvm)
>     testImplementation(fixers.bundles.test.junit)
> }
> ```

### io.komune.fixers.gradle.kotlin.jvm

The JVM plugin simplifies the configuration of Kotlin JVM projects. It applies the Java and Kotlin JVM plugins and configures them with sensible defaults.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.kotlin.jvm") version "x.y.z"
}

// The plugin automatically configures:
// - JDK version (from fixers.jdk.version or default 17)
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
    }

    detekt {
        disable = false               // default: false
        config = "detekt.yml"         // default: detekt.yml
        baseline = "detekt-baseline.xml"
        buildUponDefaultConfig = true // default: true
        checkstyleReport = true       // default: true
        htmlReport = true             // default: true
        sarifReport = true            // default: true
        markdownReport = true         // default: true
    }

    jacoco {
        enabled = true                             // default: true
        htmlReport = true                          // default: true
        xmlReport = true                           // default: true
        xmlReportFilename = "jacocoTestReport.xml" // default
    }
}
```

## Configuration Options

The Fixers Gradle plugins provide a central configuration through the `fixers` extension. Here are the available configuration options:

### Bundle

```kotlin
fixers {
    bundle {
        id = "my-project"
        name = "my-project"
        group = "com.example"
        description = "My awesome project"
        version = "1.0.0"                                          // default: from VERSION file
        url = "https://github.com/my-org/my-project"
        licenseName = "The Apache Software License, Version 2.0"   // default
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt" // default
        licenseDistribution = "repo"                                // default
        developerId = "Komune"                                      // default
        developerName = "Komune Team"                               // default
        developerOrganization = "Komune"                            // default
        developerOrganizationUrl = "https://komune.io"              // default
        scmConnection = "scm:git:git://github.com/komune-io/fixers-gradle.git"    // default
        scmDeveloperConnection = "scm:git:ssh://github.com/komune-io/fixers-gradle.git" // default
    }
}
```

### JDK

```kotlin
fixers {
    jdk {
        version = 17 // default: 17
    }
}
```

### Kotlin to TypeScript Conversion

```kotlin
fixers {
    kt2Ts {
        outputDirectory = "platform/web/kotlin" // default
        inputDirectory = "build/js/packages"
        additionalCleaning.set(mapOf(
            "fileName" to listOf(
                Regex("pattern") to "replacement"
            )
        ))
    }
}
```

### SonarQube

```kotlin
fixers {
    sonar {
        url = "https://sonarcloud.io"                     // default
        organization = "my-org"
        projectKey = "my-project"
        language = "kotlin"                               // default
        sources = "."                                     // default
        inclusions = "**/src/*main*/kotlin/**/*.kt"       // default
        exclusions = "**/build/**,**/.gradle/**,**/node_modules/**,**/buildSrc/**,**/*.java" // default
        jacoco = "**/build/reports/jacoco/**/jacocoTestReport.xml" // default
        detekt = "build/reports/detekt/merge.xml"         // default
        detektConfigPath = "detekt.yml"                   // default
        verbose = true                                    // default
        githubSummaryComment = "true"                     // default
        properties {
            property("sonar.coverage.exclusions", "src/generated/**/*")
        }
    }
}
```

### Detekt

```kotlin
fixers {
    detekt {
        disable = false               // default: false
        config = "detekt.yml"         // default: detekt.yml
        baseline = "detekt-baseline.xml"
        buildUponDefaultConfig = true // default: true
        checkstyleReport = true       // default: true
        htmlReport = true             // default: true
        sarifReport = true            // default: true
        markdownReport = true         // default: true
    }
}
```

### JaCoCo

```kotlin
fixers {
    jacoco {
        enabled = true                             // default: true
        htmlReport = true                          // default: true
        xmlReport = true                           // default: true
        xmlReportFilename = "jacocoTestReport.xml" // default
    }
}
```

### NPM Publication

```kotlin
fixers {
    npm {
        publish = true          // default: true
        organization = "komune-io" // default
        clean = true            // default: true
        version = "1.0.0"      // default: project version
    }
}
```

### Repositories

```kotlin
fixers {
    repositories {
        mavenLocal = false           // default: false
        mavenCentral = true          // default: true
        sonatypeSnapshots = false    // default: false
        maven("https://custom.repo/maven")
    }
}
```

### Publish

```kotlin
fixers {
    publish {
        gradlePluginPortalEnabled = true // default: true
        stagingDirectory = "staging-deploy" // default
        githubPackagesUrl = "https://maven.pkg.github.com/komune-io/my-project" // default: computed
        mavenCentralUrl = "https://central.sonatype.com/api/v1/publisher" // default
        mavenSnapshotsUrl = "https://central.sonatype.com/repository/maven-snapshots/" // default
        // Credentials (typically set via env vars, not in build scripts)
        // pkgGithubUsername, pkgGithubToken, mavenCentralUsername, mavenCentralPassword
        // signingGpgKey, signingGpgKeyPassword
    }
}
```

## Project Structure

This project uses a composite build to facilitate the development and testing of the Gradle plugins. Here's a brief overview of the project structure:

- **`build-composite/`**: Contains the source code for all the plugins as "convention plugins". This allows for a better developer experience within this repository, including features like code completion and easy navigation in the IDE.

- **`config/`**, **`dependencies/`**, **`plugin/`**: Standard Gradle subprojects that are configured to be published to a Maven repository. They don't contain any source code directly. Instead, the source code is copied from the `build-composite/` directory during the build process.

- **`sandbox/`**: Test environment for validating plugins locally.

- **`integration-tests/`**: Integration tests for the published plugins.

This setup allows for the local development and testing of the plugins in a streamlined way, while also enabling the publication of the plugins as standard, independent artifacts for other projects to use.
