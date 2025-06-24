/*
 * Copyright 2017-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

// Configures publishing of Maven artifacts for Gradle plugins
plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("composite.pom")
    id("composite.publishing-common")
}

afterEvaluate {
    project.extensions.configure<PublishingExtension>("publishing") {
        publications {
            // Configure existing publications with POM metadata
            // The com.gradle.plugin-publish plugin creates these publications automatically
            // We just need to configure them with our POM metadata
            val configureMavenCentralMetadata = project.extra["configureMavenCentralMetadata"] as (org.gradle.api.XmlProvider) -> Unit
            val configurePomMetadata = project.extra["configurePomMetadata"] as (org.gradle.api.XmlProvider) -> Unit

            publications.findByName("pluginMaven")?.let { publication ->
                (publication as MavenPublication).pom.withXml(configureMavenCentralMetadata)
            }

            publications.findByName("io.komune.fixers.gradle.configPluginMarkerMaven")?.let { publication ->
                (publication as MavenPublication).pom.withXml(configurePomMetadata)
            }

            publications.findByName("io.komune.fixers.gradle.dependenciesPluginMarkerMaven")?.let { publication ->
                (publication as MavenPublication).pom.withXml(configurePomMetadata)
            }

            publications.findByName("io.komune.fixers.gradle.kotlin.jvmPluginMarkerMaven")?.let { publication ->
                (publication as MavenPublication).pom.withXml(configurePomMetadata)
            }

            publications.findByName("io.komune.fixers.gradle.kotlin.mppPluginMarkerMaven")?.let { publication ->
                (publication as MavenPublication).pom.withXml(configurePomMetadata)
            }

            publications.findByName("io.komune.fixers.gradle.publishPluginMarkerMaven")?.let { publication ->
                (publication as MavenPublication).pom.withXml(configurePomMetadata)
            }

            publications.findByName("io.komune.fixers.gradle.npmPluginMarkerMaven")?.let { publication ->
                (publication as MavenPublication).pom.withXml(configurePomMetadata)
            }

            publications.findByName("io.komune.fixers.gradle.checkPluginMarkerMaven")?.let { publication ->
                (publication as MavenPublication).pom.withXml(configurePomMetadata)
            }
        }
        repositories {
            maven {
                url = uri(layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }

    project.extensions.configure<SigningExtension>("signing") {
        useInMemoryPgpKeys(
            project.extra["signingKey"].toString(),
            project.extra["signingPassword"].toString()
        )
    }
}
