package city.smartb.fixers.gradle.config

import city.smartb.fixers.gradle.config.model.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.maven.MavenPom
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

/**
 * Retrieves the [fixers][city.smartb.fixers.gradle.fixers] extension.
 */
val ExtensionContainer.fixers: ConfigExtension?
	get() = try {
		getByName(ConfigExtension.NAME) as ConfigExtension?
	} catch (e: org.gradle.api.UnknownDomainObjectException) {
		null
	}
/**
 * Configures the [fixers][city.smartb.fixers.gradle.fixers] extension.
 */
fun Project.fixers(configure: Action<ConfigExtension>): Unit =
	(this as org.gradle.api.plugins.ExtensionAware).extensions.configure(ConfigExtension.NAME, configure)

/**
 * Configures the [fixers][city.smartb.fixers.gradle.fixers] extension if exists.
 */
fun ExtensionContainer.fixersIfExists(configure: Action<ConfigExtension>) {
	if(fixers != null) {
		configure(ConfigExtension.NAME, configure)
	}
}

fun PluginDependenciesSpec.fixers(module: String): PluginDependencySpec = id("city.smartb.fixers.gradle.${module}")


open class ConfigExtension(
	private val project: Project
) {
	companion object {
		const val NAME: String = "fixers"
	}

	var bundle: Bundle = Bundle(
		name = project.name
	)

	var repository: Repository = Repository.sonatype(project)

	var publication: Publication? = null

	var sonar: Sonar = Sonar.smartB(project)

	var d2: D2 = D2(outputDirectory = project.file("storybook/stories/d2"))

	fun bundle(configure: Action<Bundle>) {
		configure.execute(bundle)
		publication(pom(bundle))
	}

	fun d2(configure: Action<D2>) {
		configure.execute(d2)
	}

	fun sonar(configure: Action<Sonar>) {
		configure.execute(sonar)
	}

	fun publication(configure: Action<MavenPom>) {
		publication = Publication(configure)
	}

	fun repository(configure: Action<Repository>) {
		configure.execute(repository)
	}
}

fun pom(bundle: Bundle): Action<MavenPom> = Action {
	name.set(bundle.name)
	description.set(bundle.description)
	url.set(bundle.url)

	licenses {
		license {
			name.set("The Apache Software License, Version 2.0")
			url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
		}
	}
	developers {
		developer {
			id.set("SmartB")
			name.set("SmartB Team")
			organization.set("SmartB")
			organizationUrl.set("https://www.smartb.city")
		}
	}

}
