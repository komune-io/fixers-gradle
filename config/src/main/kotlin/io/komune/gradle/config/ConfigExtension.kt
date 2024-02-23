package io.komune.gradle.config


import io.komune.gradle.config.model.Bundle
import io.komune.gradle.config.model.Detekt
import io.komune.gradle.config.model.Jdk
import io.komune.gradle.config.model.Kt2Ts
import io.komune.gradle.config.model.Npm
import io.komune.gradle.config.model.Publication
import io.komune.gradle.config.model.Repository
import io.komune.gradle.config.model.Sonar
import io.komune.gradle.config.model.sonarCloud
import io.komune.gradle.config.model.sonatype
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.maven.MavenPom
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

/**
 * Retrieves the [fixers][io.komune.fixers.gradle.fixers] extension.
 */
val ExtensionContainer.fixers: ConfigExtension?
	get() = try {
		getByName(ConfigExtension.NAME) as ConfigExtension?
	} catch (e: org.gradle.api.UnknownDomainObjectException) {
		null
	}

/**
 * Configures the [fixers][io.komune.fixers.gradle.fixers] extension.
 */
fun Project.fixers(configure: Action<ConfigExtension>): Unit =
	this.rootProject.extensions.configure(ConfigExtension.NAME, configure)

/**
 * Configures the [fixers][io.komune.fixers.gradle.fixers] extension if exists.
 */
fun ExtensionContainer.fixersIfExists(configure: Action<ConfigExtension>) {
	if (fixers != null) {
		configure(ConfigExtension.NAME, configure)
	}
}

fun PluginDependenciesSpec.fixers(module: String): PluginDependencySpec = id("io.komune.fixers.gradle.${module}")


abstract class ConfigExtension(
	val project: Project
) {
	companion object {
		const val NAME: String = "fixers"
	}

	var bundle: Bundle = Bundle(
		name = project.name
	)

	var kt2Ts: Kt2Ts = Kt2Ts(outputDirectory = "platform/web/kotlin")

	var jdk: Jdk = Jdk(
		version = 17
	)

	var buildTime: Long = System.currentTimeMillis()

	var repository: Repository = Repository.sonatype(project)

	var publication: Publication? = null

	var npm: Npm = Npm()

	var detekt: Detekt = Detekt()

	var sonar: Sonar = Sonar.sonarCloud(project)

	var properties: MutableMap<String, Any> = mutableMapOf()

	fun bundle(configure: Action<Bundle>) {
		configure.execute(bundle)
		publication(project.pom(bundle))
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

	fun publication(configure: Action<MavenPom>) {
		publication = Publication(configure)
	}

	fun npm(configure: Action<Npm>) {
		configure.execute(npm)
	}

	fun detekt(configure: Action<Detekt>) {
		configure.execute(detekt)
	}

	fun repository(configure: Action<Repository>) {
		configure.execute(repository)
	}
}

fun Project.pom(bundle: Bundle): Action<MavenPom> = Action {
	name.set(bundle.name)
	description.set(bundle.description)
	url.set(bundle.url)

	this.scm {
		url.set(bundle.url)
	}
	licenses {
		license {
			name.set("The Apache Software License, Version 2.0")
			url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
		}
	}
	developers {
		developer {
			id.set("Komune")
			name.set("Komune Team")
			organization.set("Komune")
			organizationUrl.set("https://komune.io")
		}
	}
}
