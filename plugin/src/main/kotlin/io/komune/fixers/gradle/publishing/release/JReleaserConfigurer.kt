package io.komune.fixers.gradle.publishing.release

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.jreleaser.model.Active
import org.jreleaser.model.Signing
import org.jreleaser.gradle.plugin.JReleaserExtension

/**
 * Configurer for JReleaser in the publishing plugin.
 *
 * This class is responsible for configuring JReleaser with version information,
 * signing, and deployment settings.
 */
class JReleaserConfigurer {
    /**
     * Configures JReleaser.
     * 
     * This method configures JReleaser with version information, signing, and deployment settings.
     * It implements configuration avoidance patterns by:
     * 1. Using the plugins.withId pattern to defer configuration
     * 2. Using Gradle's Provider API for lazy evaluation
     * 3. Deferring task registration until needed
     * 
     * @param project The Gradle project
     */
    fun configureJReleaser(project: Project) {
        val versionFromFileProvider = project.provider {
            readVersionFromFile(project)
        }

        versionFromFileProvider.orNull?.let { version ->
            if (version.isNotEmpty()) {
                project.version = version
            } else {
                project.logger.warn("Version file is empty. Using project version: ${project.version}")
            }
        } ?: run {
            project.logger.warn("Version file not found. Using project version: ${project.version}")
        }

        project.plugins.withId("org.jreleaser") {
            try {
                configureJReleaserExtension(project, versionFromFileProvider)

                project.logger.info("JReleaser configured successfully")
            } catch (e: Exception) {
                project.logger.error("Failed to configure JReleaser: ${e.message}")
                throw IllegalStateException("Failed to configure JReleaser", e)
            }
        }
    }

    /**
     * Reads the version from the VERSION file in the root project.
     * 
     * @param project The Gradle project
     * @return The version from the file, or null if the file doesn't exist
     * @throws IllegalStateException if reading the version file fails
     */
    private fun readVersionFromFile(project: Project): String? {
        val versionFile = project.rootProject.file("VERSION")
        return if (versionFile.exists()) {
            try {
                versionFile.readText().trim()
            } catch (e: Exception) {
                project.logger.error("Failed to read version from file: ${e.message}")
                throw IllegalStateException("Failed to read version from file: ${versionFile.absolutePath}", e)
            }
        } else {
            project.logger.info("Version file not found: ${versionFile.absolutePath}")
            null
        }
    }

    /**
     * Configures the JReleaser extension.
     * 
     * This method configures the JReleaser extension with version information, signing, and deployment settings.
     * It uses Gradle's Provider API for lazy evaluation of the version and Property API for better configuration.
     * 
     * @param project The Gradle project
     * @param versionFromFileProvider A provider for the version from the VERSION file
     * @throws IllegalStateException if configuring the JReleaser extension fails
     */
    @Suppress("LongMethod", "ThrowsCount")
    private fun configureJReleaserExtension(project: Project, versionFromFileProvider: Provider<String?>) {
        try {
            val stagingRepositoryPathProperty = project.objects.property(String::class.java).apply {
                set(project.provider {
                    project.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath
                })
            }

            val mavenCentralUrlProperty = project.objects.property(String::class.java).apply {
                convention("https://central.sonatype.com/api/v1/publisher")
            }

            val snapshotUrlProperty = project.objects.property(String::class.java).apply {
                convention("https://central.sonatype.com/repository/maven-snapshots/")
            }

            project.extensions.configure<JReleaserExtension> {
                try {
                    project {
                        version.set(versionFromFileProvider.map { it ?: project.version.toString() })
                    }
                    signing {
                        active.set(Active.ALWAYS)
                        armored.set(true)
                        mode.set(Signing.Mode.COSIGN)
                    }
                    deploy {
                        maven {
                            mavenCentral {
                                create("MAVENCENTRAL") {
                                    active.set(Active.RELEASE)
                                    url.set(mavenCentralUrlProperty)
                                    try {
                                        stagingRepository(stagingRepositoryPathProperty.get())
                                    } catch (e: Exception) {
                                        project.logger.error(
                                            "Failed to set staging repository for MAVENCENTRAL: ${e.message}"
                                        )
                                        throw IllegalStateException(
                                            "Failed to set staging repository for MAVENCENTRAL", 
                                            e
                                        )
                                    }
                                }
                            }
                            nexus2 {
                                create("SNAPSHOT") {
                                    active.set(Active.SNAPSHOT)
                                    url.set(snapshotUrlProperty)
                                    snapshotUrl.set(snapshotUrlProperty)
                                    applyMavenCentralRules.set(true)
                                    snapshotSupported.set(true)
                                    closeRepository.set(true)
                                    releaseRepository.set(true)
                                    try {
                                        stagingRepository(stagingRepositoryPathProperty.get())
                                    } catch (e: Exception) {
                                        project.logger.error(
                                            "Failed to set staging repository for SNAPSHOT: ${e.message}"
                                        )
                                        throw IllegalStateException(
                                            "Failed to set staging repository for SNAPSHOT", 
                                            e
                                        )
                                    }
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
                } catch (e: Exception) {
                    project.logger.error("Failed to configure JReleaser extension properties: ${e.message}")
                    throw IllegalStateException("Failed to configure JReleaser extension properties", e)
                }
            }
        } catch (e: Exception) {
            project.logger.error("Failed to configure JReleaser extension: ${e.message}")
            throw IllegalStateException("Failed to configure JReleaser extension", e)
        }
    }
}
