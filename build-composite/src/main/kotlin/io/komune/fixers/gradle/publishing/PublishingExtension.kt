package io.komune.fixers.gradle.publishing

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.XmlProvider
import groovy.util.Node

/**
 * Extension to hold publishing-related properties and functions
 */
open class PublishingExtension(private val project: Project) {
    // Signing properties
    var signingKey: String = System.getenv("GPG_SIGNING_KEY") ?: ""
    var signingPassword: String = System.getenv("GPG_SIGNING_PASSWORD") ?: ""


    // Jar tasks
    lateinit var sourcesJar: TaskProvider<Jar>
    lateinit var javadocJar: TaskProvider<Jar>

    // Marker publications for plugins
    lateinit var markerPublications: List<String>

    // POM configuration functions
    lateinit var addPomConfiguration: (Node) -> Unit
    lateinit var configurePomMetadata: (XmlProvider) -> Unit
    lateinit var configureMavenCentralMetadata: (XmlProvider) -> Unit

    /**
     * Configure POM-related functions with default implementations
     */
    fun configurePomFunctions() {
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

        configurePomMetadata = { xmlProvider: XmlProvider ->
            val root = xmlProvider.asNode()
            root.appendNode("url", "https://github.com/komune-io/fixers-gradle")
            addPomConfiguration(root)
        }

        configureMavenCentralMetadata = { xmlProvider: XmlProvider ->
            val root = xmlProvider.asNode()
            root.appendNode("name", project.name)
            root.appendNode("description", "Gradle plugin to facilitate kotlin multiplateform configuration.")
            root.appendNode("url", "https://github.com/komune-io/fixers-gradle")
            addPomConfiguration(root)
        }
    }

    /**
     * This method should be called from the PublishingPlugin
     * to initialize the sourcesJar and javadocJar tasks.
     */
    fun initializeJarTasks(sourcesJarTask: TaskProvider<Jar>, javadocJarTask: TaskProvider<Jar>) {
        sourcesJar = sourcesJarTask
        javadocJar = javadocJarTask
    }
}

/**
 * Extension function to get or create the publishing extension
 */
fun Project.publishing(configure: PublishingExtension.() -> Unit = {}): PublishingExtension {
    val extension = extensions.findByType(PublishingExtension::class.java) ?:
        extensions.create("publishingConfig", PublishingExtension::class.java, this)
    extension.configure()
    return extension
}
