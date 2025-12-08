package io.komune.fixers.gradle.config

import io.komune.fixers.gradle.config.model.Bundle
import io.komune.fixers.gradle.config.model.Detekt
import io.komune.fixers.gradle.config.model.Jdk
import io.komune.fixers.gradle.config.model.Kt2Ts
import io.komune.fixers.gradle.config.model.Npm
import io.komune.fixers.gradle.config.model.Publication
import io.komune.fixers.gradle.config.model.PublishConfig
import io.komune.fixers.gradle.config.model.Sonar
import io.komune.fixers.gradle.config.model.sonarCloud
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPom
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

/**
 * Retrieves the [fixers][io.komune.fixers.gradle.config.ConfigExtension.NAME] extension.
 */
val ExtensionContainer.fixers: ConfigExtension?
	get() = findByName(ConfigExtension.NAME) as? ConfigExtension

/**
 * Configures the [fixers][io.komune.fixers.gradle.config.ConfigExtension.NAME] extension.
 */
fun Project.fixers(configure: Action<ConfigExtension>): Unit =
	this.rootProject.extensions.configure(ConfigExtension.NAME, configure)

/**
 * Configures the [fixers][io.komune.fixers.gradle.config.ConfigExtension.NAME] extension if exists.
 */
fun ExtensionContainer.fixersIfExists(configure: Action<ConfigExtension>) {
	if (fixers != null) {
		configure(ConfigExtension.NAME, configure)
	}
}

fun PluginDependenciesSpec.fixers(module: String): PluginDependencySpec = id("io.komune.fixers.gradle.${module}")

/**
 * Main configuration extension for the Fixers Gradle plugins.
 * 
 * This class is marked as abstract to allow Gradle to create a dynamic subclass at runtime
 * for property convention mapping and extension instantiation. This is a common pattern
 * in Gradle plugin development, even when the class doesn't have explicit abstract members.
 */
@Suppress("UnnecessaryAbstractClass")
abstract class ConfigExtension(
	val project: Project
) {
	companion object {
		const val NAME: String = "fixers"
	}

	var properties: MutableMap<String, Any> = mutableMapOf()

	var bundle: Bundle = Bundle(
		project = project,
		name = project.name
	)

	var kt2Ts: Kt2Ts = Kt2Ts(project)

	var jdk: Jdk = Jdk(project)

	/**
	 * Build time as a lazy Provider for configuration cache compatibility.
	 * The timestamp is captured when the value is first accessed, not at configuration time.
	 */
	val buildTime: Property<Long> = project.objects.property(Long::class.java).convention(
		project.provider { System.currentTimeMillis() }
	)

	var pom: Publication = Publication(project)

	var npm: Npm = Npm(project)

	var detekt: Detekt = Detekt(project)

	var sonar: Sonar = Sonar.sonarCloud(project)

	var publish: PublishConfig = PublishConfig(project)

	fun bundle(configure: Action<Bundle>) {
		configure.execute(bundle)
	}

	fun kt2Ts(configure: Action<Kt2Ts>) {
		configure.execute(kt2Ts)
	}

	fun sonar(configure: Action<Sonar>) {
		configure.execute(sonar)
	}

	fun jdk(configure: Action<Jdk>) {
		configure.execute(jdk)
	}

	fun pom(configure: Action<MavenPom>) {
		pom.configure.set(configure)
	}

	fun npm(configure: Action<Npm>) {
		configure.execute(npm)
	}

	fun detekt(configure: Action<Detekt>) {
		configure.execute(detekt)
	}

	fun publish(configure: Action<PublishConfig>) {
		configure.execute(publish)
	}

	override fun toString(): String {
		return """
			ConfigExtension(
			bundle=$bundle, 
			kt2Ts=$kt2Ts, 
			jdk=$jdk, 
			buildTime=$buildTime, 
			publication=$pom, 
			npm=$npm, 
			detekt=$detekt, 
			sonar=$sonar, 
			publish=$publish)
			""".trimIndent()
	}
}
