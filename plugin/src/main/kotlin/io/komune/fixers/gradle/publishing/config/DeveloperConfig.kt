package io.komune.fixers.gradle.publishing.config

import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration class for developer information in the POM file.
 *
 * This class provides properties for configuring developer information
 * that will be included in the POM file.
 *
 * @property project The Gradle project this configuration is associated with
 */
class DeveloperConfig(private val project: Project) {
    /**
     * The ID of the developer or organization.
     * Default: "Komune"
     */
    val id: Property<String> = project.objects.property(String::class.java).apply {
        convention("Komune")
    }

    /**
     * The name of the developer or organization.
     * Default: "Komune Team"
     */
    val name: Property<String> = project.objects.property(String::class.java).apply {
        convention("Komune Team")
    }

    /**
     * The organization name.
     * Default: "Komune"
     */
    val organization: Property<String> = project.objects.property(String::class.java).apply {
        convention("Komune")
    }

    /**
     * The URL of the organization.
     * Default: "https://komune.io"
     */
    val organizationUrl: Property<String> = project.objects.property(String::class.java).apply {
        convention("https://komune.io")
    }
}
