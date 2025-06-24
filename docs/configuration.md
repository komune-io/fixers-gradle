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
  publication {
    name.set("My Project")
    description.set("My awesome project")

  }
  // Maven publication configuration

  // Publishing configuration
  publish {
    mavenCentralUrl.set("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
    mavenSnapshotsUrl.set("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    pkgDeployType.set(io.komune.fixers.gradle.config.model.PkgDeployType.PUBLISH)
    pkgMavenRepo.set(io.komune.fixers.gradle.config.model.PkgMavenRepo.MAVEN_CENTRAL)
    pkgGithubUsername.set("github-username")
    pkgGithubToken.set("github-token")
    signingKey.set("signing-key")
    signingPassword.set("signing-password")
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
  }

  detekt {
    enabled.set(true)
    baseline.set("detekt-baseline.xml")
    config.set("detekt-config.yml")
  }
}

```

### Extension Methods

The `fixers` extension provides several configuration methods:

- `bundle(Action<Bundle>)`: Configure project metadata
- `jdk(Action<Jdk>)`: Configure JDK settings
- `npm(Action<Npm>)`: Configure NPM package publishing
- `kt2Ts(Action<Kt2Ts>)`: Configure Kotlin to TypeScript generation
- `publication(Action<MavenPom>)`: Configure Maven publication
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
- Environment variable: `JDK_VERSION`
- Project property: `jdk.version`
- Default value: `17`

## Configuration Models

### Bundle

The `Bundle` class contains configuration for project metadata.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| name | - | - | Project name | The name of the project |
| id | BUNDLE_ID | bundle.id | - | The ID of the project |
| description | BUNDLE_DESCRIPTION | bundle.description | - | The description of the project |
| version | BUNDLE_VERSION | bundle.version | - | The version of the project |
| url | BUNDLE_URL | bundle.url | - | The URL of the project |
| signingKey | GPG_SIGNING_KEY | signing.key | - | The signing key for artifacts |
| signingPassword | GPG_SIGNING_PASSWORD | signing.password | - | The signing password for artifacts |
| licenseName | LICENSE_NAME | license.name | "The Apache Software License, Version 2.0" | The name of the license |
| licenseUrl | LICENSE_URL | license.url | "https://www.apache.org/licenses/LICENSE-2.0.txt" | The URL of the license |
| licenseDistribution | LICENSE_DISTRIBUTION | license.distribution | "repo" | The distribution type of the license |
| developerId | DEVELOPER_ID | developer.id | "Komune" | The ID of the developer |
| developerName | DEVELOPER_NAME | developer.name | "Komune Team" | The name of the developer |
| developerOrganization | DEVELOPER_ORGANIZATION | developer.organization | "Komune" | The organization of the developer |
| developerOrganizationUrl | DEVELOPER_ORGANIZATION_URL | developer.organizationUrl | "https://komune.io" | The URL of the developer's organization |
| scmConnection | SCM_CONNECTION | scm.connection | "scm:git:git://github.com/komune-io/fixers-gradle.git" | The connection URL for SCM |
| scmDeveloperConnection | SCM_DEVELOPER_CONNECTION | scm.developerConnection | "scm:git:ssh://github.com/komune-io/fixers-gradle.git" | The developer connection URL for SCM |

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
| version | JDK_VERSION | jdk.version | 17 | The JDK version to use |

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
| publish | NPM_PUBLISH | npm.publish | true | Whether to publish NPM packages |
| organization | NPM_ORGANIZATION | npm.organization | "komune-io" | The organization name for NPM packages |
| clean | NPM_CLEAN | npm.clean | true | Whether to clean NPM packages before publishing |
| version | NPM_VERSION | npm.version | - | The version for NPM packages |

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
| outputDirectory | KT2TS_OUTPUT_DIRECTORY | kt2ts.outputDirectory | "platform/web/kotlin" | The directory where TypeScript files will be generated |
| inputDirectory | KT2TS_INPUT_DIRECTORY | kt2ts.inputDirectory | - | The directory containing Kotlin JavaScript output to be converted |
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

### Publication

The `Publication` class contains configuration for Maven publication.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| configure | - | - | - | Configuration action for the Maven POM |

#### Example

```kotlin
fixers {
    publication {
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
| mavenCentralUrl | MAVEN_CENTRAL_URL | publish.mavenCentralUrl | "https://central.sonatype.com/api/v1/publisher" | The URL for Maven Central |
| mavenSnapshotsUrl | MAVEN_SNAPSHOTS_URL | publish.mavenSnapshotsUrl | "https://central.sonatype.com/repository/maven-snapshots/" | The URL for Maven Snapshots |
| pkgDeployType | PKG_DEPLOY_TYPE | publish.pkgDeployType | PkgDeployType.PUBLISH | The deployment type (PUBLISH or PROMOTE) |
| pkgMavenRepo | PKG_MAVEN_REPO | publish.pkgMavenRepo | - | The Maven repository for package deployment |
| pkgGithubUsername | PKG_GITHUB_USERNAME | publish.pkgGithubUsername | - | The GitHub username for package deployment |
| pkgGithubToken | PKG_GITHUB_TOKEN | publish.pkgGithubToken | - | The GitHub token for package deployment |
| signingKey | GPG_SIGNING_KEY | signing.key | - | The signing key for artifacts |
| signingPassword | GPG_SIGNING_PASSWORD | signing.password | - | The signing password for artifacts |
| gradlePlugin | - | - | - | Configuration for Gradle plugin publishing |

#### Example

```kotlin
fixers {
    publish {
        mavenCentralUrl.set("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        mavenSnapshotsUrl.set("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        pkgDeployType.set(io.komune.fixers.gradle.config.model.PkgDeployType.PUBLISH)
        pkgMavenRepo.set(io.komune.fixers.gradle.config.model.PkgMavenRepo.MAVEN_CENTRAL)
        pkgGithubUsername.set("github-username")
        pkgGithubToken.set("github-token")
        signingKey.set("signing-key")
        signingPassword.set("signing-password")
    }
}
```

### Sonar

The `Sonar` class contains configuration for Sonar analysis.

#### Properties

| Property | Environment Variable | Project Property | Default Value | Description |
|----------|---------------------|------------------|---------------|-------------|
| organization | SONAR_ORGANIZATION | sonar.organization | "" | The Sonar organization |
| projectKey | SONAR_PROJECT_KEY | sonar.projectKey | "" | The Sonar project key |
| url | SONAR_URL | sonar.url | "https://sonarcloud.io" | The Sonar host URL |
| jacoco | SONAR_JACOCO | sonar.jacoco | "${project.rootDir}/**/build/reports/jacoco/test/jacocoTestReport.xml" | The path to the JaCoCo XML report |
| language | SONAR_LANGUAGE | sonar.language | "kotlin" | The language of the project |
| detekt | SONAR_KOTLIN_DETEKT_REPORT_PATHS | sonar.kotlin.detekt.reportPaths | "build/reports/detekt/detekt.xml" | The path to the Detekt XML report |
| exclusions | SONAR_EXCLUSIONS | sonar.pullrequest.github.summary_comment | "**/*.java" | The exclusions pattern for SonarQube/SonarCloud analysis |
| githubSummaryComment | SONAR_GITHUB_SUMMARY_COMMENT | sonar.githubSummaryComment | "true" | Whether to add a summary comment to GitHub pull requests |

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
| disable | DETEKT_DISABLE | detekt.disable | false | Whether to disable Detekt |
| enabled | DETEKT_ENABLED | detekt.enabled | true | Whether to enable Detekt |
| baseline | DETEKT_BASELINE | detekt.baseline | - | The baseline file for Detekt |
| config | DETEKT_CONFIG | detekt.config | - | The configuration file for Detekt |

#### Example

```kotlin
fixers {
    detekt {
        enabled.set(true)
        baseline.set("detekt-baseline.xml")
        config.set("detekt-config.yml")
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

    publication { pom: org.gradle.api.publish.maven.MavenPom ->
        pom.name.set("My Project")
        pom.description.set("My awesome project")
    }

    publish {
        pkgDeployType.set("PUBLISH")
        pkgMavenRepo.set("maven-central")
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
        enabled.set(true)
        output.set(file("build/generated/kt2ts"))
    }
}
```
