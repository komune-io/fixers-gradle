# Fixers Gradle Build-Composite Configuration

This document provides comprehensive documentation for the build-composite configuration model used in Fixers Gradle plugins.

## Table of Contents

- [Overview](#overview)
- [KTS Fixers Extension](#kts-fixers-extension)
- [Environment Properties](#environment-properties)
- [Configuration Models](#configuration-models)
  - [Bundle](#bundle)
  - [JDK](#jdk)
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

  // NPM configuration
  npm {
    publish.set(true)
    organization.set("komune-io")
    clean.set(true)
    version.set("1.0.0")
  }

  // Kotlin to TypeScript configuration
  kt2Ts {
    outputDirectory.set("build/generated/kt2ts")
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
    detektConfigPath.set("detekt.yml")
  }

  detekt {
    disable.set(false)
    baseline.set("detekt-baseline.xml")
    config.set("detekt-config.yml")
    buildUponDefaultConfig.set(true)
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
| id | FIXERS_BUNDLE_ID | fixers.bundle.id | - | The ID of the project |
| description | FIXERS_BUNDLE_DESCRIPTION | fixers.bundle.description | - | The description of the project |
| version | FIXERS_BUNDLE_VERSION | fixers.bundle.version | From VERSION file | The version of the project |
| url | FIXERS_BUNDLE_URL | fixers.bundle.url | - | The URL of the project |
| licenseName | FIXERS_BUNDLE_LICENSE_NAME | fixers.bundle.license.name | "The Apache Software License, Version 2.0" | The name of the license |
| licenseUrl | FIXERS_BUNDLE_LICENSE_URL | fixers.bundle.license.url | "https://www.apache.org/licenses/LICENSE-2.0.txt" | The URL of the license |
| licenseDistribution | FIXERS_BUNDLE_LICENSE_DISTRIBUTION | fixers.bundle.license.distribution | "repo" | The distribution type of the license |
| developerId | FIXERS_BUNDLE_DEVELOPER_ID | fixers.bundle.developer.id | "Komune" | The ID of the developer |
| developerName | FIXERS_BUNDLE_DEVELOPER_NAME | fixers.bundle.developer.name | "Komune Team" | The name of the developer |
| developerOrganization | FIXERS_BUNDLE_DEVELOPER_ORGANIZATION | fixers.bundle.developer.organization | "Komune" | The organization of the developer |
| developerOrganizationUrl | FIXERS_BUNDLE_DEVELOPER_ORGANIZATION_URL | fixers.bundle.developer.organizationUrl | "https://komune.io" | The URL of the developer's organization |
| scmConnection | FIXERS_BUNDLE_SCM_CONNECTION | fixers.bundle.scm.connection | "scm:git:git://github.com/komune-io/fixers-gradle.git" | The connection URL for SCM |
| scmDeveloperConnection | FIXERS_BUNDLE_SCM_DEVELOPER_CONNECTION | fixers.bundle.scm.developerConnection | "scm:git:ssh://github.com/komune-io/fixers-gradle.git" | The developer connection URL for SCM |

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

The `Kt2Ts` class contains configuration for Kotlin to TypeScript generation.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| outputDirectory | FIXERS_KT2TS_OUTPUT_DIRECTORY | fixers.kt2ts.outputDirectory | "platform/web/kotlin" | The directory where TypeScript files will be generated |
| inputDirectory | FIXERS_KT2TS_INPUT_DIRECTORY | fixers.kt2ts.inputDirectory | - | The directory containing Kotlin JavaScript output to be converted |
| additionalCleaning | - | - | - | Additional cleaning operations to perform |

#### Example

```kotlin
fixers {
    kt2Ts {
        outputDirectory.set("build/generated/kt2ts")
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
| mavenCentralUsername | FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME | fixers.publish.maven.central.username | - | The Maven Central username |
| mavenCentralPassword | FIXERS_PUBLISH_MAVEN_CENTRAL_PASSWORD | fixers.publish.maven.central.password | - | The Maven Central password |
| pkgGithubUsername | FIXERS_PUBLISH_GITHUB_USERNAME | fixers.publish.github.username | - | The GitHub username for package deployment |
| pkgGithubToken | FIXERS_PUBLISH_GITHUB_TOKEN | fixers.publish.github.token | - | The GitHub token for package deployment |
| signingGpgKey | FIXERS_PUBLISH_SIGNING_GPG_KEY | fixers.publish.signing.gpgKey | - | The GPG signing key for artifacts |
| signingGpgKeyPassword | FIXERS_PUBLISH_SIGNING_GPG_KEY_PASSWORD | fixers.publish.signing.gpgKeyPassword | - | The GPG signing key password |
| gradlePortalKey | FIXERS_PUBLISH_GRADLE_PORTAL_KEY | fixers.publish.gradle.portal.key | - | Gradle Plugin Portal publish key — bridged to the `gradle.publish.key` system property that `com.gradle.plugin-publish` reads at task-execution time |
| gradlePortalSecret | FIXERS_PUBLISH_GRADLE_PORTAL_SECRET | fixers.publish.gradle.portal.secret | - | Gradle Plugin Portal publish secret — bridged to the `gradle.publish.secret` system property |
| gradlePluginPortalEnabled | FIXERS_PUBLISH_GRADLE_PORTAL_ENABLED | fixers.publish.gradle.portal.enabled | true | Whether to publish to the Gradle Plugin Portal during promote |
| stagingDirectory | FIXERS_PUBLISH_STAGING_DIRECTORY | fixers.publish.staging.directory | "staging-deploy" | Directory for staging deployments |
| githubPackagesUrl | FIXERS_PUBLISH_GITHUB_PACKAGES_URL | fixers.publish.github.packages.url | Computed from root project name | GitHub Packages URL for publishing |
| gradlePlugin | - | - | - | List of marker publications for Gradle plugins (non-env DSL-only) |

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
| detektConfigPath | FIXERS_SONAR_DETEKT_CONFIG_PATH | fixers.sonar.detektConfigPath | "detekt.yml" | The path to the Detekt configuration file |

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

The `Detekt` class contains configuration for Detekt code quality checks.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| disable | FIXERS_DETEKT_DISABLE | fixers.detekt.disable | false | Whether to disable Detekt |
| baseline | FIXERS_DETEKT_BASELINE | fixers.detekt.baseline | - | The baseline file for Detekt |
| config | FIXERS_DETEKT_CONFIG | fixers.detekt.config | "detekt.yml" | The configuration file for Detekt |
| buildUponDefaultConfig | FIXERS_DETEKT_BUILD_UPON_DEFAULT_CONFIG | fixers.detekt.buildUponDefaultConfig | true | Whether to build upon the default config |
| checkstyleReport | FIXERS_DETEKT_REPORT_CHECKSTYLE | fixers.detekt.report.checkstyle | true | Whether to enable checkstyle (XML) report |
| htmlReport | FIXERS_DETEKT_REPORT_HTML | fixers.detekt.report.html | true | Whether to enable the HTML report |
| sarifReport | FIXERS_DETEKT_REPORT_SARIF | fixers.detekt.report.sarif | true | Whether to enable the SARIF report |
| markdownReport | FIXERS_DETEKT_REPORT_MARKDOWN | fixers.detekt.report.markdown | true | Whether to enable the Markdown report |

#### Example

```kotlin
fixers {
    detekt {
        disable.set(false)
        baseline.set("detekt-baseline.xml")
        config.set("detekt-config.yml")
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
        outputDirectory.set("build/generated/kt2ts")
        inputDirectory.set("build/js/packages/my-project")
    }
}
```
