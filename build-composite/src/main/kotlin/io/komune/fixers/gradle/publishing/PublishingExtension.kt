package io.komune.fixers.gradle.publishing

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

open class PublishingExtension(private val project: Project) {

    val signingKey: Property<String> = project.objects.property(String::class.java).apply {
        set(System.getenv("GPG_SIGNING_KEY") ?: "")
    }

    val signingPassword: Property<String> = project.objects.property(String::class.java).apply {
        set(System.getenv("GPG_SIGNING_PASSWORD") ?: "")
    }

    val markerPublications: ListProperty<String> = project.objects.listProperty(String::class.java).apply {
        convention(emptyList())
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

    // No initialization needed

    extension.configure()
    return extension
}
