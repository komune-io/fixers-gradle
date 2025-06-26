package io.komune.fixers.gradle.publishing

import groovy.util.Node
import io.komune.fixers.gradle.publishing.config.DeveloperConfig
import io.komune.fixers.gradle.publishing.config.LicenseConfig
import io.komune.fixers.gradle.publishing.config.ProjectConfig
import io.komune.fixers.gradle.publishing.config.ScmConfig
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar

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
    //region Signing Properties
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
    //endregion

    //region POM Metadata Configuration
    /**
     * Configuration for license information in the POM file.
     *
     * This property allows customization of the license information that will be
     * included in the POM file. By default, it's set to Apache License 2.0.
     *
     * Example:
     * ```kotlin
     * publishingConfig {
     *     licenseConfig.name.set("MIT License")
     *     licenseConfig.url.set("https://opensource.org/licenses/MIT")
     *     licenseConfig.distribution.set("repo")
     * }
     * ```
     */
    val licenseConfig = LicenseConfig(project)

    /**
     * Configuration for developer information in the POM file.
     *
     * This property allows customization of the developer information that will be
     * included in the POM file. By default, it's set to Komune Team.
     *
     * Example:
     * ```kotlin
     * publishingConfig {
     *     developerConfig.id.set("myteam")
     *     developerConfig.name.set("My Team")
     *     developerConfig.organization.set("My Organization")
     *     developerConfig.organizationUrl.set("https://myorganization.com")
     * }
     * ```
     */
    val developerConfig = DeveloperConfig(project)

    /**
     * Configuration for SCM (Source Control Management) information in the POM file.
     *
     * This property allows customization of the SCM information that will be
     * included in the POM file. By default, it's set to the Komune GitHub repository.
     *
     * Example:
     * ```kotlin
     * publishingConfig {
     *     scmConfig.url.set("https://github.com/myorg/myrepo")
     *     scmConfig.connection.set("scm:git:git://github.com/myorg/myrepo.git")
     *     scmConfig.developerConnection.set("scm:git:ssh://github.com/myorg/myrepo.git")
     * }
     * ```
     */
    val scmConfig = ScmConfig(project)

    /**
     * Configuration for project information in the POM file.
     *
     * This property allows customization of the project information that will be
     * included in the POM file, such as name, description, and URL.
     *
     * Example:
     * ```kotlin
     * publishingConfig {
     *     projectConfig.name.set("My Project")
     *     projectConfig.description.set("A description of my project")
     *     projectConfig.url.set("https://myproject.com")
     * }
     * ```
     */
    val projectConfig = ProjectConfig(project)
    //endregion

    //region JAR Task Properties
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
    //endregion

    //region Publication Configuration
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
    //endregion

    //region POM Configuration Functions
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
     * Validates that all required POM metadata properties are set.
     *
     * This method checks that the required properties for Maven Central publication
     * are set, and throws an exception if any are missing.
     *
     * @throws IllegalStateException if any required properties are missing
     */
    fun validatePomMetadata() {
        // Validate license configuration
        validateLicenseConfig()

        // Validate developer configuration
        validateDeveloperConfig()

        // Validate SCM configuration
        check(scmConfig.url.isPresent) { 
            "SCM URL must be set for Maven Central publication" 
        }

        // Validate project configuration
        validateProjectConfig()
    }

    /**
     * Validates license configuration.
     *
     * @throws IllegalStateException if license configuration is invalid
     */
    private fun validateLicenseConfig() {
        check(licenseConfig.name.isPresent) { 
            "License name must be set for Maven Central publication" 
        }
        check(licenseConfig.url.isPresent) { 
            "License URL must be set for Maven Central publication" 
        }
    }

    /**
     * Validates developer configuration.
     *
     * @throws IllegalStateException if developer configuration is invalid
     */
    private fun validateDeveloperConfig() {
        check(developerConfig.id.isPresent) { 
            "Developer ID must be set for Maven Central publication" 
        }
        check(developerConfig.name.isPresent) { 
            "Developer name must be set for Maven Central publication" 
        }
    }

    /**
     * Validates project configuration.
     *
     * @throws IllegalStateException if project configuration is invalid
     */
    private fun validateProjectConfig() {
        check(projectConfig.name.isPresent) { 
            "Project name must be set for Maven Central publication" 
        }
        check(projectConfig.description.isPresent) { 
            "Project description must be set for Maven Central publication" 
        }
        check(projectConfig.url.isPresent) { 
            "Project URL must be set for Maven Central publication" 
        }
    }

    /**
     * Configure POM-related functions with default implementations.
     *
     * This method initializes the [addPomConfiguration], [configurePomMetadata], and
     * [configureMavenCentralMetadata] functions with default implementations that
     * add standard POM elements required for Maven Central publication.
     *
     * The default implementation adds:
     * - License information (configurable via [licenseConfig])
     * - Developer information (configurable via [developerConfig])
     * - SCM information (configurable via [scmConfig])
     * - Project information (configurable via [projectConfig])
     *
     * This method is typically called by the PublishingPlugin during plugin application,
     * but can also be called manually if needed.
     *
     * The method also validates that the required properties are set correctly by calling
     * [validatePomMetadata].
     */
    fun configurePomFunctions() {
        validatePomMetadata()
        addPomConfiguration = { root: Node ->
            val licenses = root.appendNode("licenses")
            licenses.appendNode("license").apply {
                appendNode("name", licenseConfig.name.get())
                appendNode("url", licenseConfig.url.get())
                appendNode("distribution", licenseConfig.distribution.get())
            }

            val developers = root.appendNode("developers")
            developers.appendNode("developer").apply {
                appendNode("id", developerConfig.id.get())
                appendNode("name", developerConfig.name.get())
                appendNode("organization", developerConfig.organization.get())
                appendNode("organizationUrl", developerConfig.organizationUrl.get())
            }

            val scm = root.appendNode("scm")
            scm.appendNode("url", scmConfig.url.get())
            if (scmConfig.connection.isPresent) {
                scm.appendNode("connection", scmConfig.connection.get())
            }
            if (scmConfig.developerConnection.isPresent) {
                scm.appendNode("developerConnection", scmConfig.developerConnection.get())
            }
        }

        configurePomMetadata = { xmlProvider: XmlProvider ->
            val root = xmlProvider.asNode()
            root.appendNode("url", projectConfig.url.get())

            check(::addPomConfiguration.isInitialized) {
                "addPomConfiguration must be initialized before calling configurePomMetadata"
            }

            addPomConfiguration(root)
        }

        configureMavenCentralMetadata = { xmlProvider: XmlProvider ->
            val root = xmlProvider.asNode()
            root.appendNode("name", projectConfig.name.get())
            root.appendNode("description", projectConfig.description.get())
            root.appendNode("url", projectConfig.url.get())

            check(::addPomConfiguration.isInitialized) {
                "addPomConfiguration must be initialized before calling configureMavenCentralMetadata"
            }

            addPomConfiguration(root)
        }
    }
    //endregion

    //region JAR Task Methods
    /**
     * Initializes the sources and Javadoc JAR tasks.
     *
     * This method should be called from the PublishingPlugin to initialize the
     * [sourcesJar] and [javadocJar] properties with the task providers created by the plugin.
     * These tasks are required for publishing to Maven Central, which mandates that
     * artifacts include source code and documentation.
     *
     * The method validates that the task providers are not null and properly configured.
     *
     * @param sourcesJarTask The task provider for the sources JAR task
     * @param javadocJarTask The task provider for the Javadoc JAR task
     * @throws IllegalArgumentException if any of the task providers is null or improperly configured
     * @return This extension instance for method chaining
     */
    fun initializeJarTasks(sourcesJarTask: TaskProvider<Jar>, javadocJarTask: TaskProvider<Jar>): PublishingExtension {
        requireNotNull(sourcesJarTask) { "sourcesJarTask must not be null" }
        requireNotNull(javadocJarTask) { "javadocJarTask must not be null" }

        sourcesJar = sourcesJarTask
        javadocJar = javadocJarTask

        return this
    }
    //endregion

    /**
     * Configures this extension using the configuration from the config module.
     *
     * This method allows reusing the configuration from the config module in the build-composite plugin.
     * It sets the signing properties, license information, developer information, SCM information,
     * and project information based on the provided configuration.
     *
     * Example:
     * ```kotlin
     * publishingConfig {
     *     // Configure from config module
     *     fromConfig {
     *         signingKey.set(System.getenv("GPG_SIGNING_KEY") ?: "")
     *         signingPassword.set(System.getenv("GPG_SIGNING_PASSWORD") ?: "")
     *
     *         licenseConfig.name.set("The Apache Software License, Version 2.0")
     *         licenseConfig.url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
     *         licenseConfig.distribution.set("repo")
     *
     *         developerConfig.id.set("myteam")
     *         developerConfig.name.set("My Team")
     *         developerConfig.organization.set("My Organization")
     *         developerConfig.organizationUrl.set("https://myorganization.com")
     *
     *         scmConfig.url.set("https://github.com/myorg/myrepo")
     *         scmConfig.connection.set("scm:git:git://github.com/myorg/myrepo.git")
     *         scmConfig.developerConnection.set("scm:git:ssh://github.com/myorg/myrepo.git")
     *
     *         projectConfig.name.set("My Project")
     *         projectConfig.description.set("A description of my project")
     *         projectConfig.url.set("https://myproject.com")
     *     }
     * }
     * ```
     *
     * @param configure The configuration block to apply
     * @return This extension instance for method chaining
     */
    fun fromConfig(configure: PublishingExtension.() -> Unit): PublishingExtension {
        configure(this)
        return this
    }
}
