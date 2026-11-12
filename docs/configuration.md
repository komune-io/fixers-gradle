# Fixers Gradle Build-Composite Configuration

This document provides comprehensive documentation for the build-composite configuration model used in Fixers Gradle plugins.

## Table of Contents

- [Overview](#overview)
- [KTS Fixers Extension](#kts-fixers-extension)
- [Environment Properties](#environment-properties)
- [Configuration Models](#configuration-models)
  - [Bundle](#bundle)
  - [JDK](#jdk)
  - [StrictCompilation](#strictcompilation)
  - [NPM](#npm)
  - [Kt2Ts](#kt2ts)
  - [Publication](#publication)
  - [PublishConfig](#publishconfig)
  - [Sonar](#sonar)
  - [Detekt](#detekt)
  - [Jacoco](#jacoco)
- [Examples](#examples)

## Overview

The build-composite module provides a configuration system for Fixers Gradle plugins. The configuration is accessible through the `fixers` extension in your Gradle build scripts.

The configuration system allows you to:
- Configure project metadata
- Set up publishing settings
- Configure JDK versions
- Set up NPM package publishing
- Configure Kotlin to TypeScript generation
- Set up Sonar analysis
- Configure Detekt for code quality checks
- Configure JaCoCo for code coverage

## KTS Fixers Extension

The `fixers` extension is the main entry point for configuring the build-composite module. It's available in your Gradle build scripts after applying the `io.komune.fixers.gradle.config` plugin.

### Basic Usage

```kotlin
plugins {
    id("io.komune.fixers.gradle.config")
}

fixers {
  // Bundle configuration
  bundle {
    name.set("my-project")
    version.set("1.0.0")
    description.set("My awesome project")
    id.set("io.komune.my-project")
    url.set("https://github.com/komune-io/my-project")

    // License information
    licenseName.set("The Apache Software License, Version 2.0")
    licenseUrl.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
    licenseDistribution.set("repo")

    // Developer information
    developerId.set("Komune")
    developerName.set("Komune Team")
    developerOrganization.set("Komune")
    developerOrganizationUrl.set("https://komune.io")

    // SCM information
    scmConnection.set("scm:git:git://github.com/komune-io/my-project.git")
    scmDeveloperConnection.set("scm:git:ssh://github.com/komune-io/my-project.git")
  }

  // JDK configuration
  jdk {
    version.set(17)
  }

  // Strict compilation: the test compilations may ignore return values
  strictCompilation {
    returnValueCheckerInTests.set(false)
  }

  // NPM configuration
  npm {
    publish.set(true)
    organization.set("komune-io")
    clean.set(true)
    version.set("1.0.0")
  }

  // Kotlin to TypeScript configuration
  kt2Ts {
    outputDirectory.set("platform/web/kotlin")
    inputDirectory.set("build/js/packages/my-project")
  }
  pom {
    name.set("My Project")
    description.set("My awesome project")
  }
  // Maven publication configuration

  // Publishing configuration
  publish {
    mavenCentralUrl.set("https://central.sonatype.com/api/v1/publisher")
    mavenSnapshotsUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
    pkgGithubUsername.set("github-username")
    pkgGithubToken.set("github-token")
    signingGpgKey.set("signing-key")
    signingGpgKeyPassword.set("signing-password")
  }

  // Sonar configuration
  sonar {
    organization.set("my-org")
    projectKey.set("my-project")
    url.set("https://sonarcloud.io")
    jacoco.set("build/reports/jacoco/test/jacocoTestReport.xml")
    language.set("kotlin")
    detekt.set("build/reports/detekt/detekt.xml")
    exclusions.set("**/*.java")
    githubSummaryComment.set("true")
    sources.set(".")
    inclusions.set("**/src/*main*/kotlin/**/*.kt")
    verbose.set(true)
  }

  detekt {
    checkstyleReport.set(true)
    htmlReport.set(true)
    sarifReport.set(true)
    markdownReport.set(true)
  }
}

```

### Extension Methods

The `fixers` extension provides several configuration methods:

- `bundle(Action<Bundle>)`: Configure project metadata
- `jdk(Action<Jdk>)`: Configure JDK settings
- `strictCompilation(Action<StrictCompilation>)`: Exempt the test compilations from the return value checker
- `npm(Action<Npm>)`: Configure NPM package publishing
- `kt2Ts(Action<Kt2Ts>)`: Configure Kotlin to TypeScript generation
- `pom(Action<MavenPom>)`: Configure Maven publication
- `publish(Action<PublishConfig>)`: Configure publishing settings
- `sonar(Action<Sonar>)`: Configure Sonar analysis
- `detekt(Action<Detekt>)`: Configure Detekt for code quality checks

## Environment Properties

The configuration system supports setting properties through environment variables. This is particularly useful for CI/CD pipelines where you might want to set different values for different environments.

Properties are resolved in the following order:
1. Environment variable (if present)
2. Project property (if present)
3. Default value (if defined)

For example, the JDK version can be set using:
- Environment variable: `FIXERS_JDK_VERSION`
- Project property: `fixers.jdk.version`
- Default value: `17`

## Configuration Models

### Bundle

The `Bundle` class contains configuration for project metadata.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| name | FIXERS_BUNDLE_NAME | fixers.bundle.name | Project name | The name of the project |
| group | FIXERS_BUNDLE_GROUP | fixers.bundle.group | - | The group (Maven groupId) of the project |
| description | FIXERS_BUNDLE_DESCRIPTION | fixers.bundle.description | - | The description of the project |
| version | FIXERS_BUNDLE_VERSION | fixers.bundle.version | The VERSION environment variable, else the VERSION file | The version of the project |
| url | FIXERS_BUNDLE_URL | fixers.bundle.url | - | The URL of the project |
| licenseName | FIXERS_BUNDLE_LICENSE_NAME | fixers.bundle.license.name | "The Apache Software License, Version 2.0" | The name of the license |
| licenseUrl | FIXERS_BUNDLE_LICENSE_URL | fixers.bundle.license.url | "https://www.apache.org/licenses/LICENSE-2.0.txt" | The URL of the license |
| licenseDistribution | FIXERS_BUNDLE_LICENSE_DISTRIBUTION | fixers.bundle.license.distribution | "repo" | The distribution type of the license |
| developerId | FIXERS_BUNDLE_DEVELOPER_ID | fixers.bundle.developer.id | "Komune" | The ID of the developer |
| developerName | FIXERS_BUNDLE_DEVELOPER_NAME | fixers.bundle.developer.name | "Komune Team" | The name of the developer |
| developerOrganization | FIXERS_BUNDLE_DEVELOPER_ORGANIZATION | fixers.bundle.developer.organization | "Komune" | The organization of the developer |
| developerOrganizationUrl | FIXERS_BUNDLE_DEVELOPER_ORGANIZATION_URL | fixers.bundle.developer.organizationUrl | "https://komune.io" | The URL of the developer's organization |
| scmConnection | FIXERS_BUNDLE_SCM_CONNECTION | fixers.bundle.scm.connection | "scm:git:git://github.com/komune-io/<root project name>.git" | The connection URL for SCM |
| scmDeveloperConnection | FIXERS_BUNDLE_SCM_DEVELOPER_CONNECTION | fixers.bundle.scm.developerConnection | "scm:git:ssh://github.com/komune-io/<root project name>.git" | The developer connection URL for SCM |

#### Example

```kotlin
fixers {
    bundle {
        name.set("my-project")
        version.set("1.0.0")
        description.set("My awesome project")

        licenseName.set("MIT License")
        licenseUrl.set("https://opensource.org/licenses/MIT")
    }
}
```

### JDK

The `Jdk` class contains configuration for JDK version.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| version | FIXERS_JDK_VERSION | fixers.jdk.version | 17 | The JDK version to use |

#### Example

```kotlin
fixers {
    jdk {
        version.set(11)
    }
}
```

### StrictCompilation

The `StrictCompilation` class holds what the strict Kotlin compilation lets a repository decide for itself. Every compilation fails on an ignored return value (`-Xreturn-value-checker=full`), and a compile task given its own value (`check`, `disable`) refuses to run: a breaking change for a build that passed one. A repository whose tests call functions for their effect can exempt its test compilations, a decision for the whole repository, read from the root project (a `strictCompilation {}` in a subproject is ignored). Production code stays checked, and so do test fixtures, which other modules call. `-Wextra`, `-Xreport-all-warnings` and warnings as errors stay on in the tests. The project property is read only when the root project applies the fixers config plugin.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| returnValueCheckerInTests | — | fixers.strictCompilation.returnValueCheckerInTests | true | Whether the test compilations fail on an ignored return value: the compilations named `test` (JVM `test`; `jvmTest`, `jsTest` with `commonTest` in a multiplatform module) and those of a JVM test suite. A test source set named otherwise (Android `debugUnitTest`, a hand-made one) stays checked. `false` compiles them with `-Xreturn-value-checker=disable` |

#### Example

```kotlin
fixers {
    strictCompilation {
        returnValueCheckerInTests = false
    }
}
```

With the checker disabled, the compiler also takes unnamed local variables away from those compilations: Kotlin enables them only together with the checker. A `val _ = call()` left in a test source then fails with `The feature "unnamed local variables" is experimental and should be enabled explicitly`. Switching the setting off therefore means removing every `val _ =` from the test sources, back to the plain call.

### NPM

The `Npm` class contains configuration for NPM package publishing.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| publish | FIXERS_NPM_PUBLISH | fixers.npm.publish | true | Whether to publish NPM packages |
| organization | FIXERS_NPM_ORGANIZATION | fixers.npm.organization | "komune-io" | The organization name for NPM packages |
| clean | FIXERS_NPM_CLEAN | fixers.npm.clean | true | Whether to clean NPM packages before publishing |
| version | FIXERS_NPM_VERSION | fixers.npm.version | - | The version for NPM packages |
| tag | FIXERS_NPM_TAG | fixers.npm.tag | "next" | Dist-tag used when publishing a semver prerelease (a version containing `-`, e.g. `0.35.0-SNAPSHOT.abc1234`). Release versions (no `-`) always use npm's default `latest`. |

#### Example

```kotlin
fixers {
    npm {
        publish.set(true)
        organization.set("my-org")
        clean.set(true)
    }
}
```

### Kt2Ts

The `Kt2Ts` class contains configuration for Kotlin to TypeScript generation. It is read from the root project only: a `kt2Ts` block in a subproject fails the build. `outputDirectory` is the committed copy, so it lies outside `build/`.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| outputDirectory | FIXERS_KT2TS_OUTPUT_DIRECTORY | fixers.kt2ts.outputDirectory | "platform/web/kotlin" | The directory where TypeScript files will be generated |
| inputDirectory | FIXERS_KT2TS_INPUT_DIRECTORY | fixers.kt2ts.inputDirectory | - | The directory containing Kotlin JavaScript output to be converted |
| committedFiles | - | - | `Kt2TsFiles.DECLARATIONS` | What `tsGen` writes to `outputDirectory` and `checkTsGen` compares: `DECLARATIONS`, the `.d.mts` and `package.json` of each package only; `ALL`, every file of the Kotlin/JS packages, `.mjs` included, for a front that imports the copy. Every `package.json` is written at version `0.0.0` |
| additionalCleaning | - | - | - | Additional cleaning operations to perform |

The exported types describe the Kotlin objects, which is what the compiled module takes and returns, and `tsGen` leaves them as the Kotlin/JS emitter writes them: a `List`/`Set`/`Map` is a `KtList`/`KtSet`/`KtMap` (their declarations and the `__doNotUseOrImplementIt` marker kept), a `Long` a `bigint`, an enum the class with its entries, a `T?` a `Nullable<T>` (`T | null | undefined`). None of them is a JSON value: a `KtList` is no array, a `bigint` does not add to a number, an enum instance is never equal to its name.

`tsGen` writes two declarations at the head of every `.d.mts`:

- `KtOpaque<N>`, the type of a value the export cannot express and a build lets through with `@Suppress("NON_EXPORTABLE_TYPE")`: an `Instant` or a `LocalDate` (`any/* Instant */`) is typed `KtOpaque<"Instant">`.
- `Json<T>`, the JSON a Kotlin value of type `T` is sent as: a list or a set is an array, a map an object (with optional keys when they are an enum's names: a map holds the entries it has), a `Long` a number, an enum its name, a `KtOpaque` a string, a nullable property optional and `null`; methods and the marker are dropped. A front reads and writes a body as `Json<ProductDTO>`, and the compiler refuses that value where Kotlin code expects a `ProductDTO`, and refuses a Kotlin object where a `Json<ProductDTO>` is expected, at any depth, when it implements an exported interface (its `__doNotUseOrImplementIt` marker) or is a data class (its `equals`, `hashCode`).
  `Json<T>` does not type four things. A sealed or polymorphic type gets no discriminant, so a serializer's `type` field is untyped. A nullable property is optional. F2's Kotlin client omits nulls (`explicitNulls = false`), Jackson and a default kotlinx `Json` write `null`. A reader that requires the property refuses the JSON that omits it: a default kotlinx `Json` does, Jackson and F2's `Json` read it as `null`. An instance of an exported class that is neither of the two above is not told apart from a plain object. A `Long` becomes a `number`, which loses precision beyond 2^53.

Each `.d.mts` declares the whole program of its package and imports none, so its `Json` is the one its readers see. Two packages' copies agree with each other: `Json` recognises the Kotlin types by their members (`asJsReadonlyArrayView`, `name` and `ordinal`), never by the brand the emitter gives each copy. A Kotlin declaration exported as `Json` or `KtOpaque`, or under the name of a type the module keeps unexported (`JsonKey`, `JsonMap`, `JsonField`, `JsonFields`, `JsonOptional`, `JsonObject`, the emitter's `Nullable` and `KtSingleton`), makes `checkTsGen` fail on the duplicate.

`tsGen` and `checkTsGen` fail on an `additionalCleaning` rule that changes no file of the contract, naming its suffix and pattern: a rule written against the output before 0.47.0 (`[]`, `Record<`, `number`) matches nothing now. `npmTsGen` cleans one package at a time and does not check this, since a rule may rightly not apply to one of them.

`tsGen`, `checkTsGen`, and `npmTsGen` when NPM cleaning is enabled, type a Kotlin `Any` as `unknown`, and fail, naming the file and line, on a type left `any` (`any/* Foo */`, a type the JS export cannot express: export it, or stop exporting the declaration and write its JS form in `jsMain`). fixers compiles ES modules only: a `.d.ts` is neither cleaned nor kept by `DECLARATIONS`.

`tsGen` depends on every `*ProductionLibraryDistribution` task of the build, which writes the Kotlin/JS packages it reads. It does not wait for a module built with `binaries.executable()`. It syncs them into `outputDirectory`, deleting what the Kotlin/JS output no longer has but keeping any `node_modules` a JS package manager linked there, cleans the copy, and leaves the Kotlin/JS build untouched; it never looks under `node_modules`, so a dangling link there does not fail it and an install does not make it stale. `cleanTsGen` deletes `outputDirectory` entirely. It does not rewrite the `build/packages/js` of subprojects, which `npmTsGen` cleans after the NPM package is assembled and before it is packed or published. The task wiring requires Gradle 8.6 or later.

`checkTsGen` regenerates the declarations under `build/kt2ts-check` and fails when `outputDirectory` differs from them, or when a `.d.mts` declares a top-level name twice, exported or not: any two classes, interfaces, functions, `const`/`let`/`var`, enums (`const enum` included) or type aliases, except one type (interface, type alias) and one value (function, `const`/`let`/`var`), which TypeScript keeps apart; namespaces are counted on their own, one of a name being a companion. With `committedFiles = DECLARATIONS` it compares the `.d.mts` and `package.json` only. It ignores `node_modules` and source maps, and with `ALL` compares `.mjs` files once the declarations the Kotlin/JS emitter reorders between two compilations are sorted. In a build that emits Kotlin/JS, `check` depends on it: `outputDirectory` is meant to be committed.

#### Example

```kotlin
fixers {
    kt2Ts {
        outputDirectory.set("platform/web/kotlin")
        inputDirectory.set("build/js/packages/my-project")
    }
}
```

### Publication (configured via `pom`)

The `Publication` class contains configuration for Maven publication. Although the class is named `Publication`, it is configured using the `pom()` method in the fixers extension.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| configure | - | - | - | Configuration action for the Maven POM |

#### Example

```kotlin
fixers {
    pom {
        name.set("My Project")
        description.set("My awesome project")
    }
}
```

### PublishConfig

The `PublishConfig` class contains configuration for publishing settings.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| mavenCentralUrl | FIXERS_PUBLISH_MAVEN_CENTRAL_URL | fixers.publish.maven.central.url | "https://central.sonatype.com/api/v1/publisher" | The URL for Maven Central |
| mavenSnapshotsUrl | FIXERS_PUBLISH_MAVEN_SNAPSHOTS_URL | fixers.publish.maven.snapshots.url | "https://central.sonatype.com/repository/maven-snapshots/" | The URL for Maven Snapshots |
| mavenSnapshotsMinRequestIntervalMillis | FIXERS_PUBLISH_MAVEN_SNAPSHOTS_MIN_REQUEST_INTERVAL_MILLIS | fixers.publish.maven.snapshots.minRequestIntervalMillis | 250 | Least time between two upload starts to Maven Central Snapshots (4 a second). A snapshot of more than 450 files is spaced from its first request to stay under Maven Central's quota (observed: about 490 uploads, then a 20-minute block): one start every 2.67 s. The step is sharp: 450 files take about 2 min, 451 about 20 min. The quota is counted per upload run; two builds publishing at the same time (possibly sharing it) are not coordinated. A 429 widens the spacing further for the rest of the run and never narrows it. `0` disables the configured spacing, not the quota's. GitHub Packages is not spaced |
| mavenCentralPublishingType | FIXERS_PUBLISH_MAVEN_CENTRAL_PUBLISHING_TYPE | fixers.publish.maven.central.publishingType | "AUTOMATIC" | Central Portal publishing type. `AUTOMATIC` publishes once validation passes; `USER_MANAGED` stops at `VALIDATED` and waits for a manual release from the portal UI |
| mavenCentralStatusPollTimeoutSeconds | FIXERS_PUBLISH_MAVEN_CENTRAL_STATUS_POLL_TIMEOUT_SECONDS | fixers.publish.maven.central.statusPollTimeoutSeconds | 600 | How long `promote` waits for a Central Portal deployment to reach a terminal state. A `FAILED` deployment fails the build; running out of time only warns. Set to `0` to skip polling entirely |
| mavenCentralUsername | FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME | fixers.publish.maven.central.username | - | The Maven Central username |
| mavenCentralPassword | FIXERS_PUBLISH_MAVEN_CENTRAL_PASSWORD | fixers.publish.maven.central.password | - | The Maven Central password |
| pkgGithubUsername | FIXERS_PUBLISH_GITHUB_USERNAME | fixers.publish.github.username | - | The GitHub username for package deployment |
| pkgGithubToken | FIXERS_PUBLISH_GITHUB_TOKEN | fixers.publish.github.token | - | The GitHub token for package deployment |
| signingGpgKey | FIXERS_PUBLISH_SIGNING_GPG_KEY | fixers.publish.signing.gpgKey | - | The GPG signing key for artifacts |
| signingGpgKeyPassword | FIXERS_PUBLISH_SIGNING_GPG_KEY_PASSWORD | fixers.publish.signing.gpgKeyPassword | - | The GPG signing key password |
| gradlePortalKey | FIXERS_PUBLISH_GRADLE_PORTAL_KEY | fixers.publish.gradle.portal.key | - | Gradle Plugin Portal publish key — bridged to the `gradle.publish.key` Gradle project property |
| gradlePortalSecret | FIXERS_PUBLISH_GRADLE_PORTAL_SECRET | fixers.publish.gradle.portal.secret | - | Gradle Plugin Portal publish secret — bridged to the `gradle.publish.secret` Gradle project property |
| gradlePluginPortalEnabled | FIXERS_PUBLISH_GRADLE_PORTAL_ENABLED | fixers.publish.gradle.portal.enabled | true | Whether to publish to the Gradle Plugin Portal during promote |
| stagingDirectory | FIXERS_PUBLISH_STAGING_DIRECTORY | fixers.publish.staging.directory | "staging-deploy" | Directory for staging deployments |
| githubPackagesUrl | FIXERS_PUBLISH_GITHUB_PACKAGES_URL | fixers.publish.github.packages.url | Computed from root project name | GitHub Packages URL for publishing |

#### What a snapshot publish sends

A `-SNAPSHOT` version is not signed. `stage` (GitHub Packages) and `promote` (Maven Central
Snapshots) send only what a resolver needs:
- every `.pom`, `.module` and `maven-metadata.xml`;
- every file a `.module` names, so the KMP sources jars stay;
- the `.sha1` of each of those files.

Left out: `.md5`/`.sha256`/`.sha512`, the placeholder javadoc jars, the sources jars of plain JVM
modules (no `.module` names them) and `kotlin-tooling-metadata.json`. A release sends every staged
file: javadoc, sources, all checksums and signatures, as Maven Central requires.

Two consequences of a snapshot being trimmed: an IDE's "download sources" finds nothing for a
plain JVM module's snapshot, and the version-level `maven-metadata.xml` Gradle writes may still
list the classifiers left out (a lookup of one is a harmless 404).

#### Gradle Plugin Portal credential bridge

`com.gradle.plugin-publish` reads `gradle.publish.key` / `gradle.publish.secret` via `providers.gradleProperty()` (which checks Gradle project properties and `extraProperties`) and also checks `GRADLE_PUBLISH_KEY` / `GRADLE_PUBLISH_SECRET` env vars — these names are hard-coded by the plugin. `PublishPlugin.bridgeGradlePortalCredentials()` bridges `FIXERS_PUBLISH_GRADLE_PORTAL_KEY` / `FIXERS_PUBLISH_GRADLE_PORTAL_SECRET` env vars (or the corresponding gradle properties) into the root project's `extraProperties` at plugin apply time. Using `extraProperties` instead of `System.setProperty()` keeps credentials scoped to the current build invocation — they do not leak across Gradle daemon builds. Explicit `-Pgradle.publish.key=...` always wins — the bridge only sets the property if it is not already set.

#### Example

```kotlin
fixers {
    publish {
        mavenCentralUrl.set("https://central.sonatype.com/api/v1/publisher")
        mavenSnapshotsUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
        pkgGithubUsername.set("github-username")
        pkgGithubToken.set("github-token")
        signingGpgKey.set("signing-key")
        signingGpgKeyPassword.set("signing-password")
        gradlePortalKey.set("gradle-portal-key")
        gradlePortalSecret.set("gradle-portal-secret")
    }
}
```

### Sonar

The `Sonar` class contains configuration for Sonar analysis.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| url | FIXERS_SONAR_URL | fixers.sonar.url | "https://sonarcloud.io" | The Sonar host URL |
| organization | FIXERS_SONAR_ORGANIZATION | fixers.sonar.organization | "" | The Sonar organization |
| projectKey | FIXERS_SONAR_PROJECT_KEY | fixers.sonar.projectKey | "" | The Sonar project key |
| jacoco | FIXERS_SONAR_JACOCO | fixers.sonar.jacoco | "**/build/reports/jacoco/**/jacocoTestReport.xml" | The path to the JaCoCo XML report |
| language | FIXERS_SONAR_LANGUAGE | fixers.sonar.language | "kotlin" | The language of the project |
| detekt | FIXERS_SONAR_DETEKT_REPORT_PATHS | fixers.sonar.detekt.reportPaths | "build/reports/detekt/merge.xml" | The path to the Detekt XML report |
| exclusions | FIXERS_SONAR_EXCLUSIONS | fixers.sonar.exclusions | "**/build/**,**/.gradle/**,**/node_modules/**,**/buildSrc/**,**/*.java" | The exclusions pattern |
| githubSummaryComment | FIXERS_SONAR_GITHUB_SUMMARY_COMMENT | fixers.sonar.githubSummaryComment | "true" | Whether to add a summary comment to GitHub pull requests |
| sources | FIXERS_SONAR_SOURCES | fixers.sonar.sources | "." | The sources pattern |
| inclusions | FIXERS_SONAR_INCLUSIONS | fixers.sonar.inclusions | "**/src/*main*/kotlin/**/*.kt" | The inclusions pattern |
| verbose | FIXERS_SONAR_VERBOSE | fixers.sonar.verbose | true | Whether to enable verbose output |

#### Sonar token bridge

The `org.sonarqube` gradle plugin reads `sonar.token` from system properties, `SONAR_TOKEN` env var, or the extension DSL `sonar { properties { property("sonar.token", …) } }` — these names are hard-coded by the plugin. `SonarQubeConfigurator.buildSonarProperties()` reads `FIXERS_SONAR_TOKEN` and sets `sonar.token` via the extension DSL, so local developers and CI workflows can export `FIXERS_SONAR_TOKEN` consistently with the rest of the `FIXERS_*` namespace. Using the extension DSL instead of `System.setProperty()` keeps credentials scoped to the current build invocation — they do not leak across Gradle daemon builds. Explicit `-Dsonar.token=…` or `SONAR_TOKEN` env var still wins (higher precedence in the plugin's resolution chain).

**Scope of the bridge:** local `gradle sonar` runs and `make-jvm` / `mise-jvm` CI jobs that run gradle in-process. **Does NOT affect** `sonarqube-scan-action@v7` used in `sec-workflow.yml` — that action runs in a separate process and reads `SONAR_TOKEN` env var directly. `sec-workflow.yml` maps `SONAR_TOKEN: ${{ secrets.FIXERS_SONAR_TOKEN }}` at step level to satisfy both conventions.

#### Example

```kotlin
fixers {
    sonar {
        organization.set("my-org")
        projectKey.set("my-project")
        url.set("https://sonarcloud.io")
        jacoco.set("build/reports/jacoco/test/jacocoTestReport.xml")
        language.set("kotlin")
        detekt.set("build/reports/detekt/detekt.xml")
        exclusions.set("**/*.java")
        githubSummaryComment.set("true")
    }
}
```

### Detekt

The `Detekt` class chooses the reports Detekt writes. The rules are fixers' own: every rule, the configuration fixers ships, no baseline, final; a `detekt.yml` at the root of the build is read on top of it, for the exceptions the repository decides for itself or for a path (see the README).

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| checkstyleReport | FIXERS_DETEKT_REPORT_CHECKSTYLE | fixers.detekt.report.checkstyle | true | Whether to enable checkstyle (XML) report |
| htmlReport | FIXERS_DETEKT_REPORT_HTML | fixers.detekt.report.html | true | Whether to enable the HTML report |
| sarifReport | FIXERS_DETEKT_REPORT_SARIF | fixers.detekt.report.sarif | true | Whether to enable the SARIF report |
| markdownReport | FIXERS_DETEKT_REPORT_MARKDOWN | fixers.detekt.report.markdown | true | Whether to enable the Markdown report |

#### Example

```kotlin
fixers {
    detekt {
        htmlReport.set(false)
        markdownReport.set(false)
    }
}
```

### Jacoco

The `Jacoco` class contains configuration for JaCoCo code coverage.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| enabled | FIXERS_JACOCO_ENABLED | fixers.jacoco.enabled | true | Whether to enable JaCoCo code coverage |
| htmlReport | FIXERS_JACOCO_REPORT_HTML | fixers.jacoco.report.html | true | Whether to enable the HTML report |
| xmlReport | FIXERS_JACOCO_REPORT_XML | fixers.jacoco.report.xml | true | Whether to enable the XML report |
| xmlReportFilename | FIXERS_JACOCO_REPORT_XML_FILENAME | fixers.jacoco.report.xml.filename | "jacocoTestReport.xml" | The filename for the JaCoCo XML report |

#### Example

```kotlin
fixers {
    jacoco {
        enabled.set(true)
        htmlReport.set(true)
        xmlReport.set(true)
        xmlReportFilename.set("jacocoTestReport.xml")
    }
}
```

## Examples

### Basic Configuration

```kotlin
plugins {
    id("io.komune.fixers.gradle.config")
}

fixers {
    bundle {
        name.set("my-project")
        version.set("1.0.0")
        description.set("My awesome project")
    }

    jdk {
        version.set(17)
    }

    npm {
        publish.set(true)
        organization.set("my-org")
    }
}
```

### Publishing Configuration

```kotlin
plugins {
    id("io.komune.fixers.gradle.config")
}

fixers {
    bundle {
        name.set("my-project")
        version.set("1.0.0")
        description.set("My awesome project")
    }

    pom { pom: org.gradle.api.publish.maven.MavenPom ->
        pom.name.set("My Project")
        pom.description.set("My awesome project")
    }

    publish {
        mavenCentralUrl.set("https://central.sonatype.com/api/v1/publisher")
    }
}
```

### Kotlin to TypeScript Configuration

```kotlin
plugins {
    id("io.komune.fixers.gradle.config")
}

fixers {
    kt2Ts {
        outputDirectory.set("platform/web/kotlin")
        inputDirectory.set("build/js/packages/my-project")
    }
}
```
