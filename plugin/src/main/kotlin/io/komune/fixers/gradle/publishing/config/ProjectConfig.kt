package io.komune.fixers.gradle.publishing.config

import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration class for project information in the POM file.
 *
 * This class provides properties for configuring project information
 * that will be included in the POM file.
 *
 * @property project The Gradle project this configuration is associated with
 */
class ProjectConfig(private val project: Project) {
    /**
     * The name of the project.
     * Default: project.name
     */
    val name: Property<String> = project.objects.property(String::class.java).apply {
        convention(project.name)
    }

    /**
     * The description of the project.
     * Default: "Gradle plugin to facilitate kotlin multiplateform configuration."
     */
    val description: Property<String> = project.objects.property(String::class.java).apply {
        convention("Gradle plugin to facilitate kotlin multiplateform configuration.")
    }

    /**
     * The URL of the project.
     * Default: "https://github.com/komune-io/fixers-gradle"
     */
    val url: Property<String> = project.objects.property(String::class.java).apply {
        convention("https://github.com/komune-io/fixers-gradle")
    }
}
