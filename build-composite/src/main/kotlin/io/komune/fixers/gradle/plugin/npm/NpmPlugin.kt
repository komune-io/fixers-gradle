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

/**
 * Applies `dev.petuska.npm.publish` with Komune conventions: two registries (npmjs,
 * GitHub Packages) authenticated via `$NPM_TOKEN`, a `kt2Ts` cleanup task wired
 * before every publish, and a dist-tag policy for prerelease versions.
 *
 * Dist-tag policy:
 *   - release versions (no `-` in the semver) → npm default `latest`
 *   - prerelease versions (containing `-`, e.g. `0.35.0-SNAPSHOT.cae20d5`) →
 *     `fixers.npm.tag` (default `next`). Required by npm 7+, which refuses
 *     `npm publish` without `--tag` for prereleases.
 *
 * The tag is applied via `convention`, so `./gradlew ... --tag=foo` still overrides
 * per-invocation.
 */
class NpmPlugin : Plugin<Project> {

	override fun apply(target: Project) {
		target.logger.info("Apply NpmPlugin to ${target.name}")
		// Use afterEvaluate instead of projectsEvaluated because NpmPublishPlugin
		// internally uses afterEvaluate which would fail if project is already evaluated
		target.afterEvaluate {
			target.rootProject.extensions.fixers?.takeIf { it.npm.publish.get() }?.let { config ->
				target.logger.info("Apply NpmPlugin to ${target.name} - ${target.version}")
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
		val effectiveVersion = config.npm.version.orNull ?: project.version.toString()
		project.the<NpmPublishExtension>().apply {
			organization.set(config.npm.organization.get())
			version.set(effectiveVersion)
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

		// See the class KDoc for the dist-tag policy.
		if (effectiveVersion.contains('-')) {
			val prereleaseTag = config.npm.tag.get()
			tasks.withType(NpmPublishTask::class.java).configureEach {
				tag.convention(prereleaseTag)
			}
		}
	}

}
