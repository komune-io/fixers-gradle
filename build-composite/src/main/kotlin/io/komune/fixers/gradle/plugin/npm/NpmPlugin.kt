package io.komune.fixers.gradle.plugin.npm

import dev.petuska.npm.publish.NpmPublishPlugin
import dev.petuska.npm.publish.extension.NpmPublishExtension
import dev.petuska.npm.publish.task.NpmPublishTask
import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.Npm
import io.komune.fixers.gradle.plugin.config.buildCleaningRegex
import io.komune.fixers.gradle.plugin.npm.task.NpmTsGenTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

class NpmPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		target.logger.info("Apply NpmPlugin to ${target.name}")
		// Use afterEvaluate instead of projectsEvaluated because NpmPublishPlugin
		// internally uses afterEvaluate which would fail if project is already evaluated
		target.afterEvaluate {
			target.rootProject.extensions.fixers?.takeIf { it.npm.publish.get() }?.let { config ->
				target.logger.info("Apply NpmPlugin to ${target.name} - ${target.getVersion(config)}")
				target.configureNpmPublishPlugin(config)
				target.configurePackTsCleaning(config.npm)
			}
		}
	}

	private fun Project.configurePackTsCleaning(npm: Npm) {
		// Resolve values at configuration time for configuration cache compatibility
		val projectBuildDir = "${layout.buildDirectory.asFile.get().absolutePath}/packages/js"
		val cleaningRegex = rootProject.extensions.fixers?.kt2Ts?.buildCleaningRegex() ?: emptyMap()

		tasks.register("npmTsGenTask", NpmTsGenTask::class.java) {
			group = "build"
			buildDir = projectBuildDir
			cleaning = cleaningRegex
			onlyIf { npm.clean.get() }
		}

		// Use configureEach for lazy task configuration instead of forEach
		tasks.withType(NpmPublishTask::class.java).configureEach {
			dependsOn(tasks.withType(NpmTsGenTask::class.java))
		}
	}

	private fun Project.configureNpmPublishPlugin(config: ConfigExtension) {
		logger.info("Apply NpmPublishPlugin to ${this.name}")
		project.pluginManager.apply(NpmPublishPlugin::class.java)
		// Use providers.environmentVariable() for configuration cache compatibility
		val npmToken = providers.environmentVariable("NPM_TOKEN")
		project.the<NpmPublishExtension>().apply {
			organization.set(config.npm.organization.get())
			version.set(getVersion(config))
			registries {
				register("npmjs") {
					uri.set(uri("https://registry.npmjs.org"))
					authToken.set(npmToken)
				}
				register("github") {
					uri.set(uri("https://npm.pkg.github.com"))
					authToken.set(npmToken)
				}
			}
		}
	}

	private fun Project.getVersion(config: ConfigExtension): String? {
		if (!config.npm.version.isPresent) return null
		val npmVersion = config.npm.version.get()
		val buildTimeValue = config.buildTime.get()
		val projectVersion = project.version.toString().let { projectVersion ->
			if(projectVersion == "next-SNAPSHOT" || projectVersion == "experimental-SNAPSHOT") {
				projectVersion.replace("-SNAPSHOT", ".$buildTimeValue")
			} else {
				"next.$buildTimeValue"
			}
		}
		return npmVersion.replace("-SNAPSHOT", projectVersion).also {
			logger.info("NpmPublishPlugin - Npm Version - $projectVersion")
			logger.info("NpmPublishPlugin - Project Version - $projectVersion")
			logger.info("NpmPublishPlugin - Final Version - $npmVersion")
		}
	}

}
