# Fixers Gradle

Gradle plugins to facilitate the configuration of Kotlin modules. These plugins provide sensible defaults and simplify the setup of Kotlin JVM and Multiplatform projects, Maven/NPM publication, and static code analysis.

## Table of Contents
- [Installation](#installation)
- [Plugins](#plugins)
  - [io.komune.fixers.gradle.settings](#iokomune-fixers-gradle-settings)
  - [io.komune.fixers.gradle.config](#iokomune-fixers-gradle-config)
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

### io.komune.fixers.gradle.settings

A settings plugin, published apart from the others because it carries no Kotlin Gradle plugin: on the settings classpath, that one would make every `kotlin("…") version "…"` request of the build fail.

```kotlin
// settings.gradle.kts
plugins {
    id("io.komune.fixers.gradle.settings") version "x.y.z"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

It:
- creates the `catalogue` version catalog from `io.komune.f2:f2-gradle-catalog`, at the plugin's own version, and fails when `gradle/libs.versions.toml` declares another `fixers = "…"` version;
- declares the Node.js and Yarn distributions Kotlin/JS downloads, each as an exclusive repository serving that module only, and clears the Kotlin plugin's download base URLs so it adds no project repository of its own.

It sets `repositoriesMode` to `FAIL_ON_PROJECT_REPOS`: every repository lives in the settings, and a `repositories { }` block in a build script fails the build. The fixers project plugins then add none (`fixers { repositories { } }` has no effect), and the Komune CI init script adds its repositories to the settings instead of the projects. It adds no Maven repository itself: Maven Central, snapshots or `mavenLocal()` stay the project's to declare, in the order it chooses.

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

Applied to the root project, it also enforces two conventions on every project:
- **Spring Boot images**: in a module that applies `org.springframework.boot`, `checkBootImage` (run by `check`) and `bootBuildImage` itself fail unless the builder is pinned by digest (`…:<version>@sha256:<digest>`) and the buildpacks include a versioned health checker (`docker.io/paketobuildpacks/health-checker:<version>` or `urn:cnb:registry:paketo-buildpacks/health-checker@<version>`); fixers sets `BP_HEALTH_CHECKER_ENABLED=true`. The versions stay in the project; `test` alone is not affected.
- **Yarn resolutions**: once Kotlin/JS is in the build, `check` runs `checkYarnResolutions`, which fails on a `resolution(...)` that is not scoped with `**/`, that forces a range or several versions instead of one exact version, or that has no effect on `kotlin-js-store/yarn.lock` (its parent does not depend on the package, or the lock does not hold the forced version); it fails too when the lock is missing.

### io.komune.fixers.gradle.kotlin.jvm

The JVM plugin simplifies the configuration of Kotlin JVM projects. It applies the Java and Kotlin JVM plugins and configures them with sensible defaults.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.kotlin.jvm") version "x.y.z"
}

// The plugin automatically configures:
// - JDK version (from fixers.jdk.version or default 17)
// - Kotlin compiler options: -Xjsr305=strict, -Wextra, -Xreturn-value-checker=check, warnings as errors
// - Default dependencies (Kotlin Reflect, Coroutines, JUnit)
// - The JUnit Platform launcher on testRuntimeOnly when the tests use JUnit (Gradle 9 no longer provides it)
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
// - Compiler options on every target: -Wextra, -Xreturn-value-checker=check, warnings as errors;
//   -Xjsr305=strict on the JVM target. expect/actual classes (Beta) warn, so they fail: use expect
//   functions or an interface instead
// - JS tests run by Playwright in headless Chromium and Firefox (jsBrowserTest)
```

Every JS test runs in both Chromium and Firefox. Playwright downloads the browsers on the first test run (`kotlinInstallPlaywrightBrowsers`), so the build machine needs network access to `cdn.playwright.dev` but no installed browser. A project upgrading from a version that ran the tests with Karma sees `kotlinStoreYarnLock` fail with "Lock file was changed" until `kotlin-js-store/yarn.lock` is regenerated: delete it and `build/js`, then run `./gradlew kotlinUpgradeYarnLock`.

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
        name = "my-project"
        group = "com.example"
        description = "My awesome project"
        version = "1.0.0"                                          // default: the VERSION environment variable, else the VERSION file
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

The `.d.ts` cleaning expects a single contract package: set `kotlin.js.ir.output.granularity=whole-program` in the project's `gradle.properties`. The plugin leaves it to the project, since it changes the layout of every JS library the project publishes.

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

`config` is resolved from the root project, `baseline` from each module: `detektBaseline` writes one file per module, and an absolute path makes the modules share one. The `detekt` and `detektBaseline` tasks of a JVM module receive its main and test compile classpaths, so the rules that need types run; in a multiplatform module, `check` runs `detektMain<Target>` and `detektTest<Target>` (`detektMainJvm`, `detektTestJvm` for `jvm()`), the typed tasks of each JVM target, and their baselines are written by `detektBaselineMain<Target>` and `detektBaselineTest<Target>`, not `detektBaseline`.

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

- **`config/`**, **`plugin/`**: Standard Gradle subprojects that are configured to be published to a Maven repository. They don't contain any source code directly. Instead, the source code is copied from the `build-composite/` directory during the build process.

- **`settings-plugin/`**: The settings plugin, with its own sources: it must stay free of the Kotlin Gradle plugin the convention plugins depend on.

- **`sandbox/`**: Test environment for validating plugins locally.

- **`integration-tests/`**: Integration tests for the published plugins.

This setup allows for the local development and testing of the plugins in a streamlined way, while also enabling the publication of the plugins as standard, independent artifacts for other projects to use.
