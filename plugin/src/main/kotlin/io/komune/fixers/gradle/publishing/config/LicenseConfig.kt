package io.komune.fixers.gradle.publishing.config

import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration class for license information in the POM file.
 *
 * This class provides properties for configuring license information
 * that will be included in the POM file.
 *
 * @property project The Gradle project this configuration is associated with
 */
class LicenseConfig(private val project: Project) {
    /**
     * The name of the license.
     * Default: "The Apache Software License, Version 2.0"
     */
    val name: Property<String> = project.objects.property(String::class.java).apply {
        convention("The Apache Software License, Version 2.0")
    }

    /**
     * The URL of the license.
     * Default: "https://www.apache.org/licenses/LICENSE-2.0.txt"
     */
    val url: Property<String> = project.objects.property(String::class.java).apply {
        convention("https://www.apache.org/licenses/LICENSE-2.0.txt")
    }

    /**
     * The distribution type of the license.
     * Default: "repo"
     */
    val distribution: Property<String> = project.objects.property(String::class.java).apply {
        convention("repo")
    }
}
