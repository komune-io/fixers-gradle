/*
 * Copyright 2017-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

// Configures publishing of Maven artifacts
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
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifact(tasks.named("sourcesJar").get())
                artifact(tasks.named("javadocJar").get())
                pom.withXml(project.extra["configureMavenCentralMetadata"] as (org.gradle.api.XmlProvider) -> Unit)
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
        val publishing = project.extensions.getByType<PublishingExtension>()
        sign(publishing.publications["mavenJava"])
    }
}
