package io.komune.fixers.gradle.publishing.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the

/**
 * Configurer for JAR tasks in the publishing plugin.
 *
 * This class is responsible for creating and configuring the sources and Javadoc JAR tasks
 * that are required for publishing to Maven Central.
 */
class JarTasksConfigurer {
    /**
     * Creates and configures the sources and Javadoc JAR tasks.
     *
     * This method minimizes work done during configuration phase by:
     * 1. Using task registration instead of task creation
     * 2. Using lazy configuration with providers
     * 3. Avoiding eager access to task outputs
     *
     * @param project The Gradle project
     * @return A pair of task providers for the sources and Javadoc JAR tasks
     */
    fun createAndConfigureJarTasks(project: Project): Pair<TaskProvider<Jar>, TaskProvider<Jar>> {
        val sourcesJarTask = project.tasks.register<Jar>("sourcesJar") {
            from(project.provider {
                project.the<SourceSetContainer>().getByName("main").allJava
            })
            archiveClassifier.set("sources")
        }

        val javadocJarTask = project.tasks.register<Jar>("javadocJar") {
            dependsOn("javadoc")
            from(project.tasks.named("javadoc").map { it.outputs })
            archiveClassifier.set("javadoc")
        }

        return Pair(sourcesJarTask, javadocJarTask)
    }

    /**
     * Validates that the JAR tasks are properly configured.
     *
     * @param project The Gradle project
     * @param extension The PublishingExtension
     * @throws IllegalStateException if required JAR tasks are not properly configured
     */
    fun validateJarTasks( extension: io.komune.fixers.gradle.publishing.PublishingExtension) {
        try {
            // Access the sourcesJar property to check if it's initialized
            extension.sourcesJar
        } catch (e: UninitializedPropertyAccessException) {
            throw IllegalStateException(
                "Sources JAR task has not been initialized. Make sure initializeJarTasks has been called.",
                e
            )
        }

        try {
            // Access the javadocJar property to check if it's initialized
            extension.javadocJar
        } catch (e: UninitializedPropertyAccessException) {
            throw IllegalStateException(
                "Javadoc JAR task has not been initialized. Make sure initializeJarTasks has been called.",
                e
            )
        }
    }
}
