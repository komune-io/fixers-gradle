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

	companion object {
		const val PLUGIN_ID = "io.komune.fixers.gradle.publish"
	}

	override fun apply(project: Project) {
		if (project == project.rootProject) {
			applyToRoot(project)
		} else {
			applyToSubproject(project)
		}
	}

	private fun applyToRoot(root: Project) {
		// Apply JReleaser plugin eagerly (must be before afterEvaluate)
		// so JReleaser's internal afterEvaluate hooks can initialize
		JReleaserDeployer.applyPlugin(root)

		// Configure after all projects are evaluated, so we can discover
		// which subprojects also have PublishPlugin applied.
		// Uses plugin ID (not class reference) to avoid classloader identity mismatches
		// between the root project and subproject classloaders.
		root.gradle.projectsEvaluated {
			val fixersConfig = root.extensions.fixers ?: return@projectsEvaluated
			val publishSubprojects = root.subprojects.filter {
				it.pluginManager.hasPlugin(PLUGIN_ID)
			}
			if (publishSubprojects.isNotEmpty()) {
				RootJReleaserSetup.configure(root, fixersConfig, publishSubprojects)
			}
		}
	}

	private fun applyToSubproject(project: Project) {
		project.plugins.apply(MavenPublishPlugin::class.java)
		project.plugins.apply(SigningPlugin::class.java)

		// Use plugin ID instead of class reference to avoid classloader identity mismatches
		val rootHandlesJReleaser = project.rootProject.pluginManager.hasPlugin(PLUGIN_ID)

		if (!rootHandlesJReleaser) {
			// Legacy: per-subproject JReleaser when root does not have PublishPlugin
			JReleaserDeployer.applyPlugin(project)
		}

		project.afterEvaluate {
			val fixersConfig = rootProject.extensions.fixers
			if (fixersConfig == null) {
				logger.warn("No fixers config found on root project, skipping publish configuration")
				return@afterEvaluate
			}
			setupPublishing(fixersConfig, rootHandlesJReleaser)
			setupSign(fixersConfig)
			if (!rootHandlesJReleaser) {
				JReleaserDeployer.configure(project, fixersConfig)
			}
		}
	}

	private fun Project.setupPublishing(fixersConfig: ConfigExtension, rootHandlesJReleaser: Boolean = false) {
		val publishing = extensions.getByType(PublishingExtension::class.java)
		// When root handles JReleaser, all subprojects stage to the root's build directory
		// so JReleaser finds all artifacts in a single staging directory
		val stagingProject = if (rootHandlesJReleaser) rootProject else project
		publishing.repositories {
			maven {
				url = project.uri(stagingProject.layout.buildDirectory.dir(fixersConfig.publish.stagingDirectory.get()))
			}
		}
		val currentProject = this
		PublishMppSetup.setupMppPublish(currentProject, fixersConfig)
		PublishJvmSetup.setupJVMPublish(currentProject, fixersConfig)
		PublishPlatformSetup.setupPlatformPublish(currentProject, fixersConfig)

		publishing.publications {
			configureMavenPublications(currentProject, fixersConfig)
		}
	}

	private fun Project.setupSign(fixersConfig: ConfigExtension) {
		if (!fixersConfig.publish.signingKey.isPresent || !fixersConfig.publish.signingPassword.isPresent) {
			logger.info("No signing config provided, skip signing")
			disableSigningTasks()
			return
		}

		val inMemoryKey = fixersConfig.publish.signingKey.get()
		val password = fixersConfig.publish.signingPassword.get()
		if (inMemoryKey.isEmpty()) {
			logger.info("Empty signing key provided, skip signing")
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
