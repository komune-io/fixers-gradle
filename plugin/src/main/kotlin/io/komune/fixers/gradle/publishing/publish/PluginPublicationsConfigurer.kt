package io.komune.fixers.gradle.publishing.publish

import io.komune.fixers.gradle.publishing.PublishingExtension
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.maven.MavenPublication

/**
 * Configurer for plugin publications in the publishing plugin.
 *
 * This class is responsible for configuring both the main plugin publication
 * and any marker publications specified in the extension.
 */
class PluginPublicationsConfigurer {
    /**
     * Configures publications for a Gradle plugin project.
     * 
     * This method configures both the main plugin publication and any marker publications
     * specified in the extension. It applies the appropriate POM metadata to each publication.
     * 
     * @param publications The publications container
     * @param extension The PublishingExtension
     * @throws IllegalStateException if required POM configuration functions are not properly configured
     */
    fun configurePluginPublications(
        publications: PublicationContainer,
        extension: PublishingExtension
    ) {
        validatePomConfigurationFunctions(extension)

        configureMainPluginPublication(publications, extension)

        configureMarkerPublications(publications, extension)
    }

    /**
     * Configures the main plugin publication.
     * 
     * @param publications The publications container
     * @param extension The PublishingExtension
     * @throws IllegalStateException if POM metadata configuration fails
     */
    private fun configureMainPluginPublication(
        publications: PublicationContainer,
        extension: PublishingExtension
    ) {
        try {
            publications.findByName("pluginMaven")?.let { publication ->
                try {
                    (publication as MavenPublication).pom.withXml(extension.configureMavenCentralMetadata)
                } catch (e: Exception) {
                    error("Failed to configure POM metadata for pluginMaven publication: ${e.message}")
                }
            } ?: run {
                println(
                    "Warning: pluginMaven publication not found. " +
                    "This is expected if you're not using the Gradle Plugin Publishing plugin."
                )
            }
        } catch (e: Exception) {
            error("Failed to configure main plugin publication: ${e.message}")
        }
    }

    /**
     * Configures marker publications for a Gradle plugin project.
     * 
     * @param publications The publications container
     * @param extension The PublishingExtension
     * @throws IllegalStateException if marker publication configuration fails
     */
    private fun configureMarkerPublications(
        publications: PublicationContainer,
        extension: PublishingExtension
    ) {
        try {
            val markerPublications = extension.markerPublications.get()
            if (markerPublications.isEmpty()) {
                println(
                    "Warning: No marker publications specified. " +
                    "This is expected if you're not publishing plugin markers."
                )
                return
            }

            markerPublications.forEach { publicationName ->
                configureMarkerPublication(publications, extension, publicationName)
            }
        } catch (e: Exception) {
            error("Failed to configure marker publications: ${e.message}")
        }
    }

    /**
     * Configures a single marker publication.
     *
     * @param publications The publications container
     * @param extension The PublishingExtension
     * @param publicationName The name of the publication to configure
     * @throws IllegalStateException if the publication is not found or POM metadata configuration fails
     */
    private fun configureMarkerPublication(
        publications: PublicationContainer,
        extension: PublishingExtension,
        publicationName: String
    ) {
        publications.findByName(publicationName)?.let { publication ->
            try {
                (publication as MavenPublication).pom.withXml(extension.configurePomMetadata)
            } catch (e: Exception) {
                error(
                    "Failed to configure POM metadata for $publicationName publication: ${e.message}"
                )
            }
        } ?: run {
            error("Marker publication $publicationName not found")
        }
    }

    /**
     * Validates that the POM configuration functions are properly configured.
     * 
     * @param extension The PublishingExtension
     * @throws IllegalStateException if required POM configuration functions are not properly configured
     */
    private fun validatePomConfigurationFunctions(extension: PublishingExtension) {
        try {
            extension.configureMavenCentralMetadata
            extension.configurePomMetadata
            extension.addPomConfiguration
        } catch (e: Exception) {
            error(
                "POM configuration functions have not been initialized. " +
                "Make sure configurePomFunctions has been called: ${e.message}"
            )
        }
    }
}
