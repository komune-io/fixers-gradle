package io.komune.fixers.gradle.publish

import io.komune.gradle.config.ConfigExtension
import io.komune.gradle.config.fixers
import io.komune.gradle.config.model.Repository
import io.komune.gradle.config.model.github
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.plugins.signing.SigningExtension
import java.lang.System.getenv
import org.gradle.plugins.signing.SigningPlugin

class PublishPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		target.plugins.apply(MavenPublishPlugin::class.java)
		target.plugins.apply(SigningPlugin::class.java)
		target.logger.info("Apply PublishPlugin to ${target.name}")
		target.afterEvaluate {
			val fixers = target.rootProject.extensions.fixers
			fixers?.let { fixersConfig ->
				setupPublishing(fixersConfig)
				setupSign()
			}
		}
	}

	private fun Project.setupPublishing(fixersConfig: ConfigExtension) {
		val publishing = project.extensions.getByType(PublishingExtension::class.java)
		val publication = fixersConfig.publication
		val repositoryName = getenv("PKG_MAVEN_REPO") ?: findProperty("PKG_MAVEN_REPO")?.toString() ?: ""

		val repository = fixersConfig.repositories[repositoryName] ?: Repository.github(project)

		publishing.repositories {
			maven {
				name = repository.name
				url = repository.getUrl()
				credentials {
					username = repository.username
					password = repository.password
				}
			}
		}
		PublishMppPlugin.setupMppPublish(this, publication)
		PublishJvmPlugin.setupJVMPublish(this, publication)
	}

	private fun Project.setupSign() {
		val inMemoryKey = getenv("signingKey") ?: findProperty("signingKey")?.toString()
		val password = getenv("signingPassword") ?: findProperty("signingPassword")?.toString()
		if (inMemoryKey == null) {
			logger.warn("No signing config provided, skip signing")
			return
		}

		extensions.getByType(SigningExtension::class.java).apply {
			isRequired = true
			useInMemoryPgpKeys(inMemoryKey, password)
			sign(
				extensions.getByType(PublishingExtension::class.java).publications
			)
		}
	}
}
