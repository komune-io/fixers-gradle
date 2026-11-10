package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create

/**
 * Creates the `maven` publication from the given software component if no publication
 * with that name exists yet; `configureMavenPublications` gives it the fixers POM, as every other one.
 */
internal fun Project.createMavenPublicationIfAbsent(componentName: String, config: ConfigExtension) {
    extensions.findByType(PublishingExtension::class.java)?.publications {
        if (findByName("maven") == null) {
            create<MavenPublication>("maven") {
                from(components.getByName(componentName))
            }
        }
    }
}
