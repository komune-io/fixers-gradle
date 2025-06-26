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

/**
 * Plugin for configuring publishing-related settings for Gradle projects.
 *
 * This plugin provides a centralized way to configure publishing-related settings
 * for Maven publications, including signing properties, JAR tasks, and POM metadata.
 * It supports both regular Java libraries and Gradle plugins.
 *
 * It consolidates functionality that was previously spread across separate publishing plugins
 * and can be configured for different types of projects (module, plugin, jreleaser).
 *
 * Usage example:
 * ```kotlin
 * plugins {
 *     id("io.komune.fixers.gradle.publishing")
 * }
 *
 * publishingConfig {
 *     // Configure signing properties
 *     signingKey.set(System.getenv("GPG_SIGNING_KEY"))
 *     signingPassword.set(System.getenv("GPG_SIGNING_PASSWORD"))
 *
 *     // Configure POM metadata
 *     projectConfig.name.set("My Project")
 *     projectConfig.description.set("A description of my project")
 *     projectConfig.url.set("https://myproject.com")
 *
 *     // For plugin projects, specify marker publications
 *     markerPublications.set(listOf(
 *         "io.komune.fixers.gradle.configPluginMarkerMaven"
 *     ))
 * }
 * ```
 */
class PublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply required plugins
        applyRequiredPlugins(project)

        // Create and configure extension
        val extension = createAndConfigureExtension(project)

        // Create and configure JAR tasks
        val (sourcesJarTask, javadocJarTask) = createAndConfigureJarTasks(project)
        extension.initializeJarTasks(sourcesJarTask, javadocJarTask)

        // Determine if this is a plugin project
        val isPlugin = project.plugins.hasPlugin("com.gradle.plugin-publish")

        // Configure publishing and signing
        configurePublishingAndSigning(project, extension, isPlugin)

        // Register deploy task
        registerDeployTask(project)
    }

    /**
     * Applies the required plugins to the project.
     *
     * @param project The Gradle project
     */
    private fun applyRequiredPlugins(project: Project) {
        project.plugins.apply("java-library")
        project.plugins.apply("maven-publish")
        project.plugins.apply("signing")
    }

    /**
     * Creates and configures the PublishingExtension for the project.
     *
     * This method follows Gradle's conventions for registering extensions.
     * It creates a new extension instance, configures it, and registers it with the project.
     *
     * @param project The Gradle project
     * @return The configured PublishingExtension
     */
    private fun createAndConfigureExtension(project: Project): PublishingExtension {
        val extension = project.extensions.create("publishingConfig", PublishingExtension::class.java, project)
        extension.configurePomFunctions()
        return extension
    }

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
    private fun createAndConfigureJarTasks(
        project: Project
    ): Pair<org.gradle.api.tasks.TaskProvider<Jar>, org.gradle.api.tasks.TaskProvider<Jar>> {
        val sourcesJarTask = project.tasks.register<Jar>("sourcesJar") {
            from(project.the<SourceSetContainer>().getByName("main").allJava)
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
     */
    private fun configurePublishingAndSigning(
        project: Project, 
        extension: PublishingExtension, 
        isPlugin: Boolean
    ) {
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
                        configurePublications(project, extension, isPlugin)
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
                    configureSigning(project, extension, isPlugin)
                } catch (e: Exception) {
                    project.logger.error("Failed to configure signing: ${e.message}")
                    error("Failed to configure signing: ${e.message}")
                }
            }
        }
    }

    /**
     * Configures the publications in the publishing extension.
     * 
     * This method configures the publications in the publishing extension based on whether
     * the project is a plugin or a library.
     * 
     * @param project The Gradle project
     * @param extension The PublishingExtension
     * @param isPlugin Whether the project is a Gradle plugin
     */
    private fun configurePublications(
        project: Project, 
        extension: PublishingExtension, 
        isPlugin: Boolean
    ) {
        project.extensions.configure<GradlePublishingExtension>("publishing") {
            publications {
                if (isPlugin) {
                    // Configure plugin publications
                    configurePluginPublications(this, extension)
                } else {
                    // Configure library publication
                    configureLibraryPublication(this, project, extension)
                }
            }
        }
    }

    /**
     * Configures publications for a Gradle plugin project.
     * 
     * This method configures both the main plugin publication and any marker publications
     * specified in the extension. It applies the appropriate POM metadata to each publication.
     * 
     * @param publications The publications container
     * @param extension The PublishingExtension
     */
    private fun configurePluginPublications(
        publications: org.gradle.api.publish.PublicationContainer,
        extension: PublishingExtension
    ) {
        // Configure main plugin publication
        configureMainPluginPublication(publications, extension)

        // Configure marker publications
        configureMarkerPublications(publications, extension)
    }

    /**
     * Configures the main plugin publication.
     * 
     * @param publications The publications container
     * @param extension The PublishingExtension
     */
    private fun configureMainPluginPublication(
        publications: org.gradle.api.publish.PublicationContainer,
        extension: PublishingExtension
    ) {
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
    }

    /**
     * Configures marker publications for plugin projects.
     * 
     * @param publications The publications container
     * @param extension The PublishingExtension
     */
    private fun configureMarkerPublications(
        publications: org.gradle.api.publish.PublicationContainer,
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

            configureEachMarkerPublication(publications, extension, markerPublications)
        } catch (e: Exception) {
            error("Failed to configure marker publications: ${e.message}")
        }
    }

    /**
     * Configures each marker publication in the list.
     * 
     * @param publications The publications container
     * @param extension The PublishingExtension
     * @param markerPublications List of marker publication names
     */
    private fun configureEachMarkerPublication(
        publications: org.gradle.api.publish.PublicationContainer,
        extension: PublishingExtension,
        markerPublications: List<String>
    ) {
        markerPublications.forEach { publicationName ->
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
    }

    /**
     * Configures a publication for a library project.
     * 
     * @param publications The publications container
     * @param project The Gradle project
     * @param extension The PublishingExtension
     */
    private fun configureLibraryPublication(
        publications: org.gradle.api.publish.PublicationContainer,
        project: Project,
        extension: PublishingExtension
    ) {
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

    /**
     * Configures the signing extension.
     * 
     * @param project The Gradle project
     * @param extension The PublishingExtension
     * @param isPlugin Whether the project is a Gradle plugin
     */
    private fun configureSigning(project: Project, extension: PublishingExtension, isPlugin: Boolean) {
        check(extension.signingKey.isPresent && extension.signingKey.get().isNotEmpty()) {
            "Signing key is required for publishing. " +
            "Set it using the GPG_SIGNING_KEY environment variable or in the build script."
        }

        check(extension.signingPassword.isPresent && extension.signingPassword.get().isNotEmpty()) {
            "Signing password is required for publishing. " +
            "Set it using the GPG_SIGNING_PASSWORD environment variable or in the build script."
        }

        try {
            project.extensions.configure<SigningExtension>("signing") {
                useInMemoryPgpKeys(
                    extension.signingKey.get(),
                    extension.signingPassword.get()
                )

                if (!isPlugin) {
                    val publishing = project.extensions.getByType<GradlePublishingExtension>()
                    try {
                        sign(publishing.publications.getByName("mavenJava"))
                    } catch (e: Exception) {
                        project.logger.error("Failed to sign mavenJava publication: ${e.message}")
                        error("Failed to sign mavenJava publication: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            project.logger.error("Failed to configure signing: ${e.message}")
            error("Failed to configure signing: ${e.message}")
        }
    }

    /**
     * Registers the deploy task.
     * 
     * This task depends on publish task to deploy all artifacts.
     * 
     * @param project The Gradle project
     */
    private fun registerDeployTask(project: Project) {
        project.tasks.register("deploy") {
            group = "publishing"
            description = "Publishes all artifacts to Maven repositories"
            dependsOn("publish")
        }
    }
}
