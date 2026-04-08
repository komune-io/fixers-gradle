package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.utils.pom
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create

object PublishCatalogSetup {

	fun setupCatalogPublish(project: Project, config: ConfigExtension) {
		project.plugins.withId("version-catalog") {
			project.setupCatalogPublication(config)
		}
	}

	private fun Project.setupCatalogPublication(config: ConfigExtension) {
		extensions.findByType(PublishingExtension::class.java)?.let { publishing ->
			publishing.publications {
				if (findByName("maven") == null) {
					create<MavenPublication>("maven") {
						from(components.findByName("versionCatalog"))
						val publication = project.pom(config.bundle)
						pom(publication)
					}
				}
			}
		}
	}
}
