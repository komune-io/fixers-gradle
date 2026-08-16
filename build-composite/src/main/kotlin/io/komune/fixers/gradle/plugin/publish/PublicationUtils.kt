package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.utils.pom
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create

/**
 * Creates the `maven` publication from the given software component if no publication
 * with that name exists yet, applying the standard fixers POM from the bundle config.
 */
internal fun Project.createMavenPublicationIfAbsent(componentName: String, config: ConfigExtension) {
	extensions.findByType(PublishingExtension::class.java)?.let { publishing ->
		publishing.publications {
			if (findByName("maven") == null) {
				create<MavenPublication>("maven") {
					from(components.findByName(componentName))
					pom(project.pom(config.bundle))
				}
			}
		}
	}
}
