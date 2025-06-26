package io.komune.fixers.gradle.publishing.config

import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration class for SCM (Source Control Management) information in the POM file.
 *
 * This class provides properties for configuring SCM information
 * that will be included in the POM file.
 *
 * @property project The Gradle project this configuration is associated with
 */
class ScmConfig(private val project: Project) {
    /**
     * The URL of the SCM repository.
     * Default: "https://github.com/komune-io/fixers-gradle"
     */
    val url: Property<String> = project.objects.property(String::class.java).apply {
        convention("https://github.com/komune-io/fixers-gradle")
    }

    /**
     * The connection string for the SCM repository.
     * Default: "scm:git:git://github.com/komune-io/fixers-gradle.git"
     */
    val connection: Property<String> = project.objects.property(String::class.java).apply {
        convention("scm:git:git://github.com/komune-io/fixers-gradle.git")
    }

    /**
     * The developer connection string for the SCM repository.
     * Default: "scm:git:ssh://github.com/komune-io/fixers-gradle.git"
     */
    val developerConnection: Property<String> = project.objects.property(String::class.java).apply {
        convention("scm:git:ssh://github.com/komune-io/fixers-gradle.git")
    }
}
