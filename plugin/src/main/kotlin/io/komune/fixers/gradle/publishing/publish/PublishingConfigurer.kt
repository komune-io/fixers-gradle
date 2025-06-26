package io.komune.fixers.gradle.publishing.publish

import io.komune.fixers.gradle.publishing.PublishingExtension
import io.komune.fixers.gradle.publishing.sign.SigningConfigurer
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension as GradlePublishingExtension

/**
 * Configurer for core publishing in the publishing plugin.
 *
 * This class is responsible for configuring the publishing extension's repositories
 * and delegating to specialized configurers for publications.
 */
class PublishingConfigurer {
    /**
     * Configuration class for publications.
     *
     * This class holds the parameters needed for configuring publications.
     */
    data class PublicationsConfig(
        val project: Project,
        val extension: PublishingExtension,
        val isPlugin: Boolean,
        val pluginPublicationsConfigurer: PluginPublicationsConfigurer,
        val libraryPublicationsConfigurer: LibraryPublicationsConfigurer
    )
    /**
     * Configures the publishing and signing extensions.
     * 
     * This method implements configuration avoidance patterns by:
     * 1. Using the plugins.withId pattern to defer configuration
     * 2. Splitting configuration into smaller, focused blocks
     * 3. Minimizing the use of afterEvaluate blocks
     * 
     * @param project The Gradle project
     * @param extension The PublishingExtension
     * @param isPlugin Whether the project is a Gradle plugin
     * @param pluginPublicationsConfigurer The configurer for plugin publications
     * @param libraryPublicationsConfigurer The configurer for library publications
     * @param signingConfigurer The configurer for signing
     */
    @Suppress("LongParameterList")
    fun configurePublishingAndSigning(
        project: Project, 
        extension: PublishingExtension, 
        isPlugin: Boolean,
        pluginPublicationsConfigurer: PluginPublicationsConfigurer,
        libraryPublicationsConfigurer: LibraryPublicationsConfigurer,
        signingConfigurer: SigningConfigurer
    ) {
        val publicationsConfig = PublicationsConfig(
            project, extension, isPlugin, 
            pluginPublicationsConfigurer, libraryPublicationsConfigurer
        )

        project.plugins.withId("maven-publish") {
            try {
                project.extensions.configure<GradlePublishingExtension>("publishing") {
                    repositories {
                        maven {
                            url = project.uri(project.layout.buildDirectory.dir("staging-deploy"))
                        }
                    }
                }

                project.afterEvaluate {
                    try {
                        configurePublishing(publicationsConfig)
                    } catch (e: Exception) {
                        project.logger.error("Failed to configure publishing: ${e.message}")
                        error("Failed to configure publishing: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                project.logger.error("Failed to configure publishing repositories: ${e.message}")
                error("Failed to configure publishing repositories: ${e.message}")
            }
        }

        project.plugins.withId("signing") {
            project.afterEvaluate {
                try {
                    signingConfigurer.configureSigning(project, extension, isPlugin)
                } catch (e: Exception) {
                    project.logger.error("Failed to configure signing: ${e.message}")
                    error("Failed to configure signing: ${e.message}")
                }
            }
        }
    }

    /**
     * Configures the publishing extension's publications.
     * 
     * This method configures the publications in the publishing extension based on whether
     * the project is a plugin or a library. The repositories are configured separately
     * in the configurePublishingAndSigning method.
     * 
     * @param config The publications configuration
     * @throws IllegalStateException if required configuration is missing or invalid
     */
    private fun configurePublishing(config: PublicationsConfig) {
        val project = config.project
        val extension = config.extension
        val isPlugin = config.isPlugin
        val pluginPublicationsConfigurer = config.pluginPublicationsConfigurer
        val libraryPublicationsConfigurer = config.libraryPublicationsConfigurer

        try {
            project.extensions.configure<GradlePublishingExtension>("publishing") {
                publications {
                    if (isPlugin) {
                        try {
                            pluginPublicationsConfigurer.configurePluginPublications(this, extension)
                        } catch (e: Exception) {
                            project.logger.error("Failed to configure plugin publications: ${e.message}")
                            error("Failed to configure plugin publications: ${e.message}")
                        }
                    } else {
                        try {
                            libraryPublicationsConfigurer.configureLibraryPublication(this, project, extension)
                        } catch (e: Exception) {
                            project.logger.error("Failed to configure library publication: ${e.message}")
                            error("Failed to configure library publication: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            project.logger.error("Failed to configure publishing: ${e.message}")
            error("Failed to configure publishing: ${e.message}")
        }
    }
}
