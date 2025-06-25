package io.komune.fixers.gradle.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension as GradlePublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.jreleaser.model.Signing
import org.jreleaser.gradle.plugin.JReleaserExtension

/**
 * Consolidated plugin that replaces all the separate publishing plugins.
 * It can be configured for different types of projects (module, plugin, jreleaser).
 */
class PublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("java-library")
        project.plugins.apply("maven-publish")
        project.plugins.apply("signing")
        project.plugins.apply("org.jreleaser")

        val extension = project.publishing {
            configurePomFunctions()
        }

        val sourcesJarTask = project.tasks.register<Jar>("sourcesJar") {
            from(project.the<SourceSetContainer>().getByName("main").allJava)
            archiveClassifier.set("sources")
        }

        val javadocJarTask = project.tasks.register<Jar>("javadocJar") {
            dependsOn("javadoc")
            from(project.tasks.named("javadoc").get().outputs)
            archiveClassifier.set("javadoc")
        }

        extension.initializeJarTasks(sourcesJarTask, javadocJarTask)

        val isPlugin = project.plugins.hasPlugin("com.gradle.plugin-publish")

        val configureJReleaser = project.plugins.hasPlugin("org.jreleaser")

        project.afterEvaluate {
            project.extensions.configure<GradlePublishingExtension>("publishing") {
                publications {
                    if (isPlugin) {
                        findByName("pluginMaven")?.let { publication ->
                            (publication as MavenPublication).pom.withXml(extension.configureMavenCentralMetadata)
                        }

                        val markerPublications = listOf(
                            "io.komune.fixers.gradle.configPluginMarkerMaven",
                            "io.komune.fixers.gradle.dependenciesPluginMarkerMaven",
                            "io.komune.fixers.gradle.kotlin.jvmPluginMarkerMaven",
                            "io.komune.fixers.gradle.kotlin.mppPluginMarkerMaven",
                            "io.komune.fixers.gradle.publishPluginMarkerMaven",
                            "io.komune.fixers.gradle.npmPluginMarkerMaven",
                            "io.komune.fixers.gradle.checkPluginMarkerMaven"
                        )

                        markerPublications.forEach { publicationName ->
                            findByName(publicationName)?.let { publication ->
                                (publication as MavenPublication).pom.withXml(extension.configurePomMetadata)
                            }
                        }
                    } else {
                        // Create a new publication for modules
                        create<MavenPublication>("mavenJava") {
                            from(project.components.getByName("java"))
                            artifact(extension.sourcesJar.get())
                            artifact(extension.javadocJar.get())
                            pom.withXml(extension.configureMavenCentralMetadata)
                        }
                    }
                }
                repositories {
                    maven {
                        url = project.uri(project.layout.buildDirectory.dir("staging-deploy"))
                    }
                }
            }

            project.extensions.configure<SigningExtension>("signing") {
                useInMemoryPgpKeys(
                    extension.signingKey,
                    extension.signingPassword
                )

                if (!isPlugin) {
                    val publishing = project.extensions.getByType<GradlePublishingExtension>()
                    sign(publishing.publications.getByName("mavenJava"))
                }
            }
        }

        if (configureJReleaser) {
            project.plugins.apply("org.jreleaser")

            val versionFile = project.rootProject.file("VERSION")
            val versionFromFile = if (versionFile.exists()) {
                versionFile.readText().trim()
            } else {
                null
            }

            if (!versionFromFile.isNullOrEmpty()) {
                project.version = versionFromFile
            }

            project.extensions.configure<JReleaserExtension> {
                project {
                    version.set(versionFromFile ?: project.version.toString())
                }
                signing {
                    active.set(org.jreleaser.model.Active.ALWAYS)
                    armored.set(true)
                    mode.set(Signing.Mode.COSIGN)
                }
                deploy {
                    maven {
                        mavenCentral {
                            create("MAVENCENTRAL") {
                                active.set(org.jreleaser.model.Active.RELEASE)
                                url.set("https://central.sonatype.com/api/v1/publisher")
                                stagingRepository(project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)
                            }
                        }
                        nexus2 {
                            create("SNAPSHOT") {
                                active.set(org.jreleaser.model.Active.SNAPSHOT)
                                url.set("https://central.sonatype.com/repository/maven-snapshots/")
                                snapshotUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
                                applyMavenCentralRules.set(true)
                                snapshotSupported.set(true)
                                closeRepository.set(true)
                                releaseRepository.set(true)
                                stagingRepository(project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)
                            }
                        }
                    }
                }
                gitRootSearch.set(true)
                release {
                    github {
                        skipRelease.set(true)
                    }
                }
            }

            project.tasks.register("deploy") {
                group = "publishing"
                description = "Publishes all plugin marker artifacts (skipping JReleaser due to configuration issues)"
                dependsOn("publish", "jreleaserDeploy")
            }
        }
    }
}
