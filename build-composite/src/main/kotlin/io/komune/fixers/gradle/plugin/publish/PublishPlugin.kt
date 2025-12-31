package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.fixers
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

class PublishPlugin : Plugin<Project> {

	override fun apply(project: Project) {
		project.plugins.apply(MavenPublishPlugin::class.java)
		project.plugins.apply(SigningPlugin::class.java)

		// IMPORTANT: Apply JReleaser plugin OUTSIDE afterEvaluate
		// JReleaser has internal afterEvaluate hooks that initialize fields like 'immutableRelease'
		// If we apply the plugin inside afterEvaluate, these hooks never run, causing NPE
		JReleaserDeployer.applyPlugin(project)

		// Keep afterEvaluate for configuration as some values need to be resolved
		project.afterEvaluate {
			// Use root project's config - this is where fixers { bundle { ... } } is typically configured
			// Using rootProject ensures we get the config even before projectsEvaluated merges it to subprojects
			val fixersConfig = rootProject.extensions.fixers
			if (fixersConfig == null) {
				logger.warn("No fixers config found on root project, skipping publish configuration")
				return@afterEvaluate
			}
			setupPublishing(fixersConfig)
			setupSign(fixersConfig)
			JReleaserDeployer.configure(project, fixersConfig)
		}
	}

	private fun Project.setupPublishing(fixersConfig: ConfigExtension) {
		val publishing = extensions.getByType(PublishingExtension::class.java)
		publishing.repositories {
			maven {
				url = project.uri(project.layout.buildDirectory.dir(fixersConfig.publish.stagingDirectory.get()))
			}
		}
		val currentProject = this
		PublishMppSetup.setupMppPublish(currentProject, fixersConfig)
		PublishJvmSetup.setupJVMPublish(currentProject, fixersConfig)

		publishing.publications {
			configureMavenPublications(currentProject, fixersConfig)
		}
	}

	private fun Project.setupSign(fixersConfig: ConfigExtension) {
		if (!fixersConfig.publish.signingKey.isPresent || !fixersConfig.publish.signingPassword.isPresent) {
			logger.debug("No signing config provided, skip signing")
			disableSigningTasks()
			return
		}

		val inMemoryKey = fixersConfig.publish.signingKey.get()
		val password = fixersConfig.publish.signingPassword.get()
		if (inMemoryKey.isEmpty()) {
			logger.warn("Empty signing key provided, skip signing")
			disableSigningTasks()
			return
		}

		val hasPublishPlugin = plugins.hasPlugin("com.gradle.plugin-publish")

		extensions.getByType(SigningExtension::class.java).apply {
			isRequired = true
			useInMemoryPgpKeys(inMemoryKey, password)

			if (!hasPublishPlugin) {
				sign(
					extensions.getByType(PublishingExtension::class.java).publications
				)
			} else {
				val publishing = extensions.getByType<PublishingExtension>()
				publishing.publications.findByName("mavenJava")?.let {
					sign(it)
				}
			}
		}
	}

	private fun Project.disableSigningTasks() {
		tasks.withType(org.gradle.plugins.signing.Sign::class.java).configureEach {
			enabled = false
		}
	}
}
