package io.komune.fixers.gradle.publishing.tasks

import org.gradle.api.Project

/**
 * Registrar for the deploy task in the publishing plugin.
 *
 * This class is responsible for registering the deploy task that combines
 * the publish and jreleaserDeploy tasks into a single task.
 */
class DeployTaskRegistrar {
    /**
     * Registers the deploy task.
     * 
     * This method minimizes work done during configuration phase by:
     * 1. Using task registration instead of task creation
     * 2. Using lazy configuration with providers
     * 3. Avoiding eager validation of task existence
     * 4. Using task providers for dependencies
     * 
     * @param project The Gradle project
     * @throws IllegalStateException if registering the deploy task fails
     */
    fun registerDeployTask(project: Project) {
        try {
            val publishTaskProvider = project.tasks.named("publish")
            val jreleaserDeployTaskProvider = project.tasks.named("jreleaserDeploy")

            project.tasks.register("deploy") {
                group = "publishing"
                description = "Publishes all plugin marker artifacts to Maven repositories"

                dependsOn(
                    publishTaskProvider.map { it.path },
                    jreleaserDeployTaskProvider.map { it.path }
                )

                doFirst {
                    project.logger.lifecycle("Starting deployment process...")
                    project.logger.lifecycle("Publishing artifacts to Maven repositories")
                }

                doLast {
                    project.logger.lifecycle("Deployment completed successfully")
                }
            }

            // Log that the task was registered
            project.logger.info("Deploy task registered successfully")
        } catch (e: Exception) {
            project.logger.error("Failed to register deploy task: ${e.message}")
            throw IllegalStateException("Failed to register deploy task", e)
        }
    }
}
