package io.komune.fixers.gradle.publishing.publish

import io.komune.fixers.gradle.publishing.PublishingExtension
import io.komune.fixers.gradle.publishing.tasks.JarTasksConfigurer
import org.gradle.api.Project
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create

/**
 * Configurer for library publications in the publishing plugin.
 *
 * This class is responsible for configuring the Maven publication
 * for a library project.
 */
class LibraryPublicationsConfigurer {
    /**
     * Configures a publication for a library project.
     * 
     * @param publications The publications container
     * @param project The Gradle project
     * @param extension The PublishingExtension
     * @throws IllegalStateException if required JAR tasks are not properly configured
     */
    fun configureLibraryPublication(
        publications: PublicationContainer,
        project: Project,
        extension: PublishingExtension
    ) {
        val jarTasksConfigurer = JarTasksConfigurer()
        jarTasksConfigurer.validateJarTasks(project, extension)

        publications.create<MavenPublication>("mavenJava") {
            from(project.components.getByName("java"))

            try {
                artifact(extension.sourcesJar.get())
            } catch (e: Exception) {
                project.logger.error("Failed to add sources JAR artifact: ${e.message}")
                error(
                    "Failed to add sources JAR artifact. " +
                    "Make sure initializeJarTasks has been called: ${e.message}"
                )
            }

            try {
                artifact(extension.javadocJar.get())
            } catch (e: Exception) {
                project.logger.error("Failed to add Javadoc JAR artifact: ${e.message}")
                error(
                    "Failed to add Javadoc JAR artifact. " +
                    "Make sure initializeJarTasks has been called: ${e.message}"
                )
            }

            try {
                pom.withXml(extension.configureMavenCentralMetadata)
            } catch (e: Exception) {
                project.logger.error("Failed to configure POM metadata: ${e.message}")
                error(
                    "Failed to configure POM metadata. " +
                    "Make sure configurePomFunctions has been called: ${e.message}"
                )
            }
        }
    }
}
