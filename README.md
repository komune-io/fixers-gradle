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

Every build that applies a fixers project plugin applies the settings plugin first, in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.komune.fixers.gradle.settings") version "x.y.z"
}
```

It declares the repositories of the build (see [io.komune.fixers.gradle.settings](#iokomune-fixers-gradle-settings)); the project plugins fail without it. Then, in your root `build.gradle.kts` file, apply the desired plugins:

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
```

It:
- creates the `catalogue` version catalog from `io.komune.f2:f2-gradle-catalog`, at the plugin's own version, and fails when `gradle/libs.versions.toml` declares another `fixers = "…"` version, or when `settings.gradle.kts` creates `catalogue` itself; the build that produces the catalog (f2) sets `io.komune.fixers.settings.catalogue=false` in `gradle.properties` and creates its own;
- declares the Node.js and Yarn distributions Kotlin/JS downloads, each as an exclusive repository serving that module only, and clears the Kotlin plugin's download base URLs so it adds no project repository of its own;
- declares, after the repositories the build itself lists in `dependencyResolutionManagement`, Maven Central and the Sonatype snapshots (`KomuneSnapshots`), which serve `io.komune` groups and snapshot versions only.

It sets `repositoriesMode` to `FAIL_ON_PROJECT_REPOS` and fails the build when the settings change it: every repository lives in the settings, and a `repositories { }` block in a build script fails the build. The fixers project plugins add none, and the Komune CI init script adds GitHub Packages, for `io.komune` groups only and last, to the settings.

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

It fails in a build without the settings plugin, and in a module when the root project does not apply it: the conventions below are wired from the root. Applied to the root project, it enforces them on every project:
- **Spring Boot images**: in a module that applies `org.springframework.boot`, fixers sets the builder (`docker.io/paketobuildpacks/builder-noble-java-tiny`), the run image (`docker.io/paketobuildpacks/ubuntu-noble-run-tiny`) and the buildpacks (`urn:cnb:builder:paketo-buildpacks/java` and `docker.io/paketobuildpacks/health-checker`), each pinned by digest, and `BP_HEALTH_CHECKER_ENABLED=true`. A build may set other images; `bootBuildImage` fails unless the builder, the run image and the health checker are pinned by digest (`…:<version>@sha256:<digest>`) and the environment keeps `BP_HEALTH_CHECKER_ENABLED=true` (`environment.put`, not `environment.set`). Configuration, `check` and the other tasks are not affected.
- **Yarn resolutions**: once Kotlin/JS is in the build, fixers forces `**/ws` and `**/ajv` to patched versions (transitive packages of the Kotlin and ktor JS dependencies with advisories), and `check` runs `checkYarnResolutions`, which fails on a resolution of a path fixers forces, and on a `resolution(...)` that is not scoped with `**/`, that forces a range or several versions instead of one exact version, or that has no effect on `kotlin-js-store/yarn.lock` (its parent does not depend on the package, or the lock does not hold the forced version); an unparented `**/pkg` counts as depended on when a lock entry depends on the package, or when a Kotlin/JS module asks for it in its own `package.json` (yarn keeps the modules out of the lock as workspaces; the check reads those listed in the root `package.json`); a spec of the package that neither asks for fails the check with `the lock holds <spec>, which neither a lock entry nor a workspace asks for`; a `peerDependencies` entry is not an ask, and a workspace written as a pattern, missing or unreadable fails the check, and a package is matched by every spec of its lock entry, `npm:` aliases included; it fails too when the lock is missing.

### io.komune.fixers.gradle.kotlin.jvm

The JVM plugin simplifies the configuration of Kotlin JVM projects. It applies the Java and Kotlin JVM plugins and configures them with sensible defaults.

#### Usage Example

```kotlin
plugins {
    id("io.komune.fixers.gradle.kotlin.jvm") version "x.y.z"
}

// The plugin automatically configures:
// - JDK version (from fixers.jdk.version or default 17)
// - Kotlin compiler options: -Xjsr305=strict, -Wextra, -Xreturn-value-checker=full, -Xreport-all-warnings,
//   warnings as errors: final on the extension, and a compile task refuses to run with it off or with
//   -nowarn, -Xsuppress-warning or -Xwarning-level other than :error
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
// - Compiler options on every target: -Wextra, -Xreturn-value-checker=full, -Xreport-all-warnings,
//   warnings as errors, held as in the JVM plugin; -Xjsr305=strict on the JVM target. expect/actual
//   classes (Beta) warn, so they fail: use expect functions or an interface instead
// - JS target: es2015, Long as bigint
// - JS tests run by Playwright in headless Chromium and Firefox (jsBrowserTest)
```

An exported declaration whose signature uses a type the JS export cannot express (`NON_EXPORTABLE_TYPE`) would appear as `any` in the TypeScript declarations, and two exported declarations of one name in two packages (`EXPORTING_JS_NAME_CLASH_ES`) would merge into one TypeScript type: both fail the compilation. Fix them in the code: for a non-exportable type, export the type, or stop exporting the declaration; for a clash, rename one of the two declarations, or give one a distinct JavaScript name with `@JsName("…")`. There is no option to turn them back into warnings. The compiler reports a clash when the declaring module compiles; `checkTsGen` catches the same names coming from dependencies: it fails when one `.d.mts` of the contract exports a name twice, whatever the two declarations are (class, interface, function, `const`/`let`/`var`, enum or type alias, under their `@JsName` when they have one), except a class or an interface and the namespace of its companion, and one type (interface, type alias) and one value (function, `const`/`let`/`var`) of the same name, which TypeScript keeps apart.

`@JsExport.Ignore` fails every Kotlin/JS compilation, naming each file and line, and nothing turns it off. It hides a member of an exported declaration, so the JS surface becomes the Kotlin API minus what JS cannot express: an interface with no callable member, a class with no constructor. Keep the Kotlin API unexported, and write its JS form in `jsMain`: a declaration exported in its place that takes and returns JS values only (`Promise`, arrays, the JSON a `Json<T>` describes), or stop exporting the declaration. The sources are read by a small lexer that knows strings (raw ones and templates included) and nested comments: the annotation is found qualified or not, under a use-site target, cut across lines, through its import, an alias of `JsExport`, or `import kotlin.js.JsExport.*` and a bare `@Ignore`. It matches names, not resolved symbols: a class of your own named `JsExport` with an `Ignore` inside is refused too, so is any `@Ignore` in a file that star-imports `kotlin.js.JsExport`, and an alias of the annotation declared in a dependency is not seen.

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
        scmConnection = "scm:git:git://github.com/komune-io/<root project>.git"    // default
        scmDeveloperConnection = "scm:git:ssh://github.com/komune-io/<root project>.git" // default
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
        committedFiles = Kt2TsFiles.DECLARATIONS // default; ALL: every file, .mjs included
        additionalCleaning.set(mapOf(
            "fileName" to listOf(
                Regex("pattern") to "replacement"
            )
        ))
    }
}
```

`kt2Ts` is read from the root project only, which registers `tsGen`, `cleanTsGen` and `checkTsGen`; a `kt2Ts` block in a subproject fails the build. `tsGen` copies every package under the input directory into the output directory and writes `Json<T>` and `KtOpaque` at the head of each `.d.mts`; it fails when a type is left `any` (`any/* Foo */`, a type the JS export cannot express). The exported types describe the Kotlin objects (`KtList`, `bigint`, enum classes, `Nullable<T>`, as the emitter writes them); `Json<T>` describes their JSON.

`committedFiles` chooses what `tsGen` writes and `checkTsGen` compares. `DECLARATIONS`, the default, keeps the declarations (`.d.mts`) and each `package.json` only: the TypeScript contract a library commits to review it. `ALL` copies every file of the Kotlin/JS packages, `.mjs` included, for a front that imports the copy. In both modes each `package.json` carries version `0.0.0` instead of the build's, so a release rewrites none of them.

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
        checkstyleReport = true       // default: true
        htmlReport = true             // default: true
        sarifReport = true            // default: true
        markdownReport = true         // default: true
    }
}
```

Detekt runs every rule (`allRules`) on top of its default configuration, with the configuration fixers ships, which holds the exemptions decided once for every Komune build; no baseline, and every finding fails the build. These are final on the extension, and a Detekt task that changes them refuses to run; a `detekt.yml` at the root of the build fails it. The exemptions: no KDoc required on public declarations, while one over a private declaration fails (rename it, or say the why in a `//` comment); `LongParameterList` ignores Spring stereotypes (`@Service`, `@Component`, `@Configuration`, `@ConfigurationProperties`, `@Controller`, `@RestController`, `@Repository`); `ForbiddenVoid` accepts `Void` as a type argument; `FunctionNameMaxLength` skips test sources. Where a rule does not apply, suppress the finding with `@Suppress("RuleName")` and the reason next to it. Sources under a `generated` directory (KSP, OpenAPI) are left out.

The `detekt` task of a JVM module receives its main and test compile classpaths, so the rules that need types run; in a multiplatform module, `check` runs `detektMain<Target>` and `detektTest<Target>` (`detektMainJvm`, `detektTestJvm` for `jvm()`), the typed tasks of every JVM target, and the source set tasks of what only non-JVM compilations compile (`detektJsMainSourceSet`, `detektJsTestSourceSet`), without types: Detekt registers no typed task for those targets.

Typed Detekt loads the Kotlin compiler in the Gradle daemon: give it room in `gradle.properties`, e.g. `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g`, or the build fails on `OutOfMemoryError: Metaspace`.

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
