package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import org.gradle.api.Project

object PublishCatalogSetup {

	fun setupCatalogPublish(project: Project, config: ConfigExtension) {
		project.plugins.withId("version-catalog") {
			project.createMavenPublicationIfAbsent("versionCatalog", config)
		}
	}
}
