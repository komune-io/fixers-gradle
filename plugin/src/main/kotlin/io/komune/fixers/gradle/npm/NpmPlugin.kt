package io.komune.fixers.gradle.npm

import dev.petuska.npm.publish.NpmPublishPlugin
import dev.petuska.npm.publish.extension.NpmPublishExtension
import dev.petuska.npm.publish.task.NpmPublishTask
import io.komune.fixers.gradle.npm.task.NpmTsGenTask
import io.komune.gradle.config.ConfigExtension
import io.komune.gradle.config.fixers
import io.komune.gradle.config.model.Npm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

class NpmPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		target.logger.info("Apply NpmPlugin to ${target.name}")
		target.afterEvaluate {
			 target.rootProject.extensions.fixers?.takeIf { it.npm.publish }?.let {config ->
				 target.logger.info("Apply NpmPlugin to ${target.name} - ${target.getVersion(config)}")
				 target.configureNpmPublishPlugin(config)
				 configurePackTsCleaning(config.npm)
			}
		}
	}

	private fun Project.configurePackTsCleaning(npm: Npm) {
		tasks.register("npmTsGenTask", NpmTsGenTask::class.java) {
			group = "build"
			onlyIf { npm.clean }
		}
		afterEvaluate {
			logger.error("afterEvaluate - Apply NpmPlugin to ${this.name}")
			tasks.withType(NpmPublishTask::class.java).forEach {
				it.apply {
					dependsOn(tasks.withType(NpmTsGenTask::class.java))
				}
			}
		}
	}

	private fun Project.configureNpmPublishPlugin(config: ConfigExtension) {
		logger.info("Apply NpmPublishPlugin to ${this.name}")
		project.pluginManager.apply(NpmPublishPlugin::class.java)
		project.the<NpmPublishExtension>().apply {
			organization.set(config.npm.organization)
			version.set(getVersion(config))
			registries {
                register("npmjs") {
                    uri.set(uri("https://registry.npmjs.org"))
                    authToken.set(System.getenv("NPM_TOKEN"))
                }
				register("github") {
					uri.set(uri("https://npm.pkg.github.com"))
					authToken.set(System.getenv("NPM_TOKEN"))
				}
			}
		}
	}

	private fun Project.getVersion(config: ConfigExtension): String? {
		val npmVersion = config.npm.version ?: return null
		val projectVersion = project.version.toString().let { projectVersion ->
			if(projectVersion == "next-SNAPSHOT" || projectVersion == "experimental-SNAPSHOT") {
				projectVersion.replace("-SNAPSHOT", ".${config.buildTime}")
			} else {
				"next.${config.buildTime}"
			}
		}
		return npmVersion.replace("-SNAPSHOT", projectVersion).also {
			logger.info("NpmPublishPlugin - Npm Version - $projectVersion")
			logger.info("NpmPublishPlugin - Project Version - $projectVersion")
			logger.info("NpmPublishPlugin - Final Version - $npmVersion")
		}
	}

}
