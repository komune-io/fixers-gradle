package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.utils.pom
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlatformPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create

object PublishPlatformSetup {

	fun setupPlatformPublish(project: Project, config: ConfigExtension) {
		project.plugins.withType(JavaPlatformPlugin::class.java) {
			project.setupPlatformPublication(config)
		}
	}

	private fun Project.setupPlatformPublication(config: ConfigExtension) {
		extensions.findByType(PublishingExtension::class.java)?.let { publishing ->
			publishing.publications {
				if (findByName("maven") == null) {
					create<MavenPublication>("maven") {
						from(components.findByName("javaPlatform"))
						val publication = project.pom(config.bundle)
						pom(publication)
					}
				}
			}
		}
	}
}
