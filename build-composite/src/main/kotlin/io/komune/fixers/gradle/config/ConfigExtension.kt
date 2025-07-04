package io.komune.fixers.gradle.config

import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * Configuration extension for the publishing plugin.
 * Contains all configurable properties used across the publishing functionality.
 */
open class ConfigExtension(project: Project) {
    val licenseName: Property<String> = project.objects.property(String::class.java)

    val licenseUrl: Property<String> = project.objects.property(String::class.java)

    val organizationId: Property<String> = project.objects.property(String::class.java)

    val organizationName: Property<String> = project.objects.property(String::class.java)

    val organizationUrl: Property<String> = project.objects.property(String::class.java)

    val githubOrganization: Property<String> = project.objects.property(String::class.java)

    val githubProject: Property<String> = project.objects.property(String::class.java)

    val repositoryUrl: Property<String> = project.objects.property(String::class.java).apply {
        convention(project.provider { "https://github.com/${githubOrganization.get()}/${githubProject.get()}" })
    }

    val githubPackagesUrl: Property<String> = project.objects.property(String::class.java).apply {
        convention(project.provider { "https://maven.pkg.github.com/${githubOrganization.get()}/${githubProject.get()}" })
    }

}

/**
 * Extension function to get or create the config extension for a Gradle project.
 *
 * This function provides a convenient way to access and configure the ConfigExtension
 * for a Gradle project. If the extension doesn't exist yet, it creates a new one.
 *
 * Example usage in a build script:
 * ```kotlin
 * config {
 *     organizationName.set("My Organization")
 *
 *     // Configure GitHub properties
 *     githubOrganization.set("my-org")
 *     githubProject.set("my-repo")
 *
 *     // The repository URLs will be automatically updated based on the GitHub properties:
 *     // repositoryUrl will be "https://github.com/my-org/my-repo"
 *     // githubPackagesUrl will be "https://maven.pkg.github.com/my-org/my-repo"
 * }
 * ```
 *
 * @param configure A configuration block to apply to the extension
 * @return The configured ConfigExtension instance
 */
fun Project.config(configure: ConfigExtension.() -> Unit = {}): ConfigExtension {
    val extension = extensions.findByType(ConfigExtension::class.java) ?: extensions.create(
        "config", ConfigExtension::class.java, this
    )

    extension.configure()
    return extension
}
