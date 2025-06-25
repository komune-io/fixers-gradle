package io.komune.fixers.gradle.publishing

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.XmlProvider
import groovy.util.Node

/**
 * Extension to hold publishing-related properties and functions for Gradle projects.
 * 
 * This extension provides a centralized way to configure publishing-related settings
 * for Maven publications, including signing properties, JAR tasks, and POM metadata.
 * It is designed to work with the PublishingPlugin to simplify the configuration of
 * Maven Central and other repository publications.
 *
 * @property project The Gradle project this extension is associated with
 */
open class PublishingExtension(private val project: Project) {
    /**
     * The GPG signing key used for signing artifacts.
     * 
     * This property is used by the signing plugin to sign Maven artifacts.
     * By default, it's read from the GPG_SIGNING_KEY environment variable.
     * For security reasons, it's recommended to provide this through environment
     * variables rather than hardcoding it in build scripts.
     * 
     * This property uses Gradle's Property API for lazy evaluation and better configuration.
     */
    val signingKey: Property<String> = project.objects.property(String::class.java).apply {
        set(System.getenv("GPG_SIGNING_KEY") ?: "")
    }

    /**
     * The password for the GPG signing key.
     * 
     * This property is used by the signing plugin to sign Maven artifacts.
     * By default, it's read from the GPG_SIGNING_PASSWORD environment variable.
     * For security reasons, it's recommended to provide this through environment
     * variables rather than hardcoding it in build scripts.
     * 
     * This property uses Gradle's Property API for lazy evaluation and better configuration.
     */
    val signingPassword: Property<String> = project.objects.property(String::class.java).apply {
        set(System.getenv("GPG_SIGNING_PASSWORD") ?: "")
    }


    /**
     * Reference to the sources JAR task.
     * 
     * This property holds a reference to the task that generates a JAR file containing
     * the source code. This JAR is required for publishing to Maven Central.
     * It is initialized by the [initializeJarTasks] method, which is called by the PublishingPlugin.
     */
    lateinit var sourcesJar: TaskProvider<Jar>

    /**
     * Reference to the Javadoc JAR task.
     * 
     * This property holds a reference to the task that generates a JAR file containing
     * the Javadoc documentation. This JAR is required for publishing to Maven Central.
     * It is initialized by the [initializeJarTasks] method, which is called by the PublishingPlugin.
     */
    lateinit var javadocJar: TaskProvider<Jar>

    /**
     * List of plugin marker publication names.
     * 
     * When publishing Gradle plugins, this list contains the names of the marker publications
     * that should have POM metadata configured. These marker publications are automatically
     * created by the Gradle Plugin Publishing plugin and are used to publish plugin markers
     * to the Gradle Plugin Portal.
     * 
     * This property uses Gradle's ListProperty API for lazy evaluation and better configuration.
     * By default, it's an empty list, but it should be configured by the client if plugin
     * marker publications need to be customized.
     * 
     * Example:
     * ```kotlin
     * publishingConfig {
     *     markerPublications.set(listOf(
     *         "io.komune.fixers.gradle.configPluginMarkerMaven",
     *         "io.komune.fixers.gradle.dependenciesPluginMarkerMaven"
     *     ))
     * }
     * ```
     */
    val markerPublications: ListProperty<String> = project.objects.listProperty(String::class.java).apply {
        convention(emptyList())
    }

    /**
     * Function to add standard POM configuration elements.
     * 
     * This function adds license information, developer details, and SCM (Source Control Management)
     * information to the POM file. It is used by both [configurePomMetadata] and
     * [configureMavenCentralMetadata] to ensure consistent POM configuration.
     * 
     * The function is initialized by [configurePomFunctions] with a default implementation,
     * but can be customized by clients if needed.
     * 
     * @param root The root Node of the POM XML to which configuration will be added
     */
    lateinit var addPomConfiguration: (Node) -> Unit

    /**
     * Function to configure basic POM metadata.
     * 
     * This function adds a URL and calls [addPomConfiguration] to add standard
     * POM elements. It is used for plugin marker publications.
     * 
     * The function is initialized by [configurePomFunctions] with a default implementation,
     * but can be customized by clients if needed.
     * 
     * @param xmlProvider The XML provider containing the POM to configure
     */
    lateinit var configurePomMetadata: (XmlProvider) -> Unit

    /**
     * Function to configure Maven Central specific POM metadata.
     * 
     * This function adds name, description, URL, and calls [addPomConfiguration]
     * to add standard POM elements. It is used for Maven Central publications
     * and must meet Maven Central's requirements.
     * 
     * The function is initialized by [configurePomFunctions] with a default implementation,
     * but can be customized by clients if needed.
     * 
     * @param xmlProvider The XML provider containing the POM to configure
     */
    lateinit var configureMavenCentralMetadata: (XmlProvider) -> Unit

    /**
     * Configure POM-related functions with default implementations.
     * 
     * This method initializes the [addPomConfiguration], [configurePomMetadata], and
     * [configureMavenCentralMetadata] functions with default implementations that
     * add standard POM elements required for Maven Central publication.
     * 
     * The default implementation adds:
     * - License information (Apache 2.0)
     * - Developer information (Komune Team)
     * - SCM information (GitHub repository URL)
     * 
     * This method is typically called by the PublishingPlugin during plugin application,
     * but can also be called manually if needed.
     * 
     * The method also validates that the required properties are set correctly.
     */
    fun configurePomFunctions() {
        // Define the default implementation for addPomConfiguration
        addPomConfiguration = { root: Node ->
            val licenses = root.appendNode("licenses")
            licenses.appendNode("license").apply {
                appendNode("name", "The Apache Software License, Version 2.0")
                appendNode("url", "https://www.apache.org/licenses/LICENSE-2.0.txt")
                appendNode("distribution", "repo")
            }

            val developers = root.appendNode("developers")
            developers.appendNode("developer").apply {
                appendNode("id", "Komune")
                appendNode("name", "Komune Team")
                appendNode("organization", "Komune")
                appendNode("organizationUrl", "https://komune.io")
            }

            val scm = root.appendNode("scm")
            scm.appendNode("url", "https://github.com/komune-io/fixers-gradle")
        }

        // Define the default implementation for configurePomMetadata
        configurePomMetadata = { xmlProvider: XmlProvider ->
            val root = xmlProvider.asNode()
            root.appendNode("url", "https://github.com/komune-io/fixers-gradle")

            // Validate that addPomConfiguration is initialized
            if (!::addPomConfiguration.isInitialized) {
                throw IllegalStateException("addPomConfiguration must be initialized before calling configurePomMetadata")
            }

            addPomConfiguration(root)
        }

        // Define the default implementation for configureMavenCentralMetadata
        configureMavenCentralMetadata = { xmlProvider: XmlProvider ->
            val root = xmlProvider.asNode()
            root.appendNode("name", project.name)
            root.appendNode("description", "Gradle plugin to facilitate kotlin multiplateform configuration.")
            root.appendNode("url", "https://github.com/komune-io/fixers-gradle")

            // Validate that addPomConfiguration is initialized
            if (!::addPomConfiguration.isInitialized) {
                throw IllegalStateException("addPomConfiguration must be initialized before calling configureMavenCentralMetadata")
            }

            addPomConfiguration(root)
        }
    }

    /**
     * Initializes the sources and Javadoc JAR tasks.
     * 
     * This method should be called from the PublishingPlugin to initialize the
     * [sourcesJar] and [javadocJar] properties with the task providers created by the plugin.
     * These tasks are required for publishing to Maven Central, which mandates that
     * artifacts include source code and documentation.
     * 
     * The method validates that the task providers are not null.
     * 
     * @param sourcesJarTask The task provider for the sources JAR task
     * @param javadocJarTask The task provider for the Javadoc JAR task
     * @throws IllegalArgumentException if any of the task providers is null
     */
    fun initializeJarTasks(sourcesJarTask: TaskProvider<Jar>, javadocJarTask: TaskProvider<Jar>) {
        // Validate that the task providers are not null
        requireNotNull(sourcesJarTask) { "sourcesJarTask must not be null" }
        requireNotNull(javadocJarTask) { "javadocJarTask must not be null" }

        sourcesJar = sourcesJarTask
        javadocJar = javadocJarTask
    }
}

/**
 * Extension function to get or create the publishing extension for a Gradle project.
 * 
 * This function provides a convenient way to access and configure the PublishingExtension
 * for a Gradle project. If the extension doesn't exist yet, it creates a new one.
 * 
 * Example usage in a build script:
 * ```kotlin
 * publishing {
 *     markerPublications.set(listOf("pluginMarkerMaven"))
 *     signingKey.set(System.getenv("MY_SIGNING_KEY"))
 *     signingPassword.set(System.getenv("MY_SIGNING_PASSWORD"))
 * }
 * ```
 * 
 * @param configure A configuration block to apply to the extension
 * @return The configured PublishingExtension instance
 */
fun Project.publishing(configure: PublishingExtension.() -> Unit = {}): PublishingExtension {
    val extension = extensions.findByType(PublishingExtension::class.java) ?:
        extensions.create("publishingConfig", PublishingExtension::class.java, this)

    // Initialize the extension with default values if it's newly created
    if (!extension.signingKey.isPresent) {
        extension.configurePomFunctions()
    }

    extension.configure()
    return extension
}
