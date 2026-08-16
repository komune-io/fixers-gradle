package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlatformPlugin

object PublishPlatformSetup {

	fun setupPlatformPublish(project: Project, config: ConfigExtension) {
		project.plugins.withType(JavaPlatformPlugin::class.java) {
			project.createMavenPublicationIfAbsent("javaPlatform", config)
		}
	}
}
