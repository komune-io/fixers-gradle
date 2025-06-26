package io.komune.fixers.gradle.publishing.dsl

import io.komune.fixers.gradle.publishing.PublishingExtension
import org.gradle.api.Project

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
 * This function follows Gradle's conventions for extension functions:
 * - It looks for an existing extension first
 * - It creates a new extension if one doesn't exist
 * - It applies the configuration block to the extension
 * - It returns the configured extension
 * 
 * @param configure A configuration block to apply to the extension
 * @return The configured PublishingExtension instance
 */
fun Project.publishing(configure: PublishingExtension.() -> Unit = {}): PublishingExtension {
    val extension = extensions.findByType(PublishingExtension::class.java) ?:
        extensions.create("publishing", PublishingExtension::class.java, this)

    if (!extension.signingKey.isPresent) {
        extension.configurePomFunctions()
    }

    extension.configure()

    return extension
}
