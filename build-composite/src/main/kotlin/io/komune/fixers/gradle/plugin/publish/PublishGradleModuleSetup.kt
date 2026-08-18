package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.utils.pom
import org.gradle.api.Project
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.maven.MavenPublication

/**
 * Applies POM metadata to every Maven publication in the container.
 *
 * The catch-all `withType(MavenPublication).configureEach` covers all publications,
 * present and future: the `pluginMaven` publication, plugin-marker publications
 * (`*PluginMarkerMaven`), and manually created publications (e.g. version catalogs)
 * that bypass the specific setup classes (JVM, MPP, Platform, Catalog).
 */
fun PublicationContainer.configureMavenPublications(
    project: Project,
    configExtension: ConfigExtension
) {
    val pomMetadata = project.pom(configExtension.bundle)
    withType(MavenPublication::class.java).configureEach {
        pom(pomMetadata)
    }
}
