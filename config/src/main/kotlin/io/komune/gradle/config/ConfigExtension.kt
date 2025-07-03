package io.komune.gradle.config

import io.komune.gradle.config.model.Bundle
import io.komune.gradle.config.model.Detekt
import io.komune.gradle.config.model.Jdk
import io.komune.gradle.config.model.Kt2Ts
import io.komune.gradle.config.model.Npm
import io.komune.gradle.config.model.Publication
import io.komune.gradle.config.model.Repository
import io.komune.gradle.config.model.Sonar
import io.komune.gradle.config.model.github
import io.komune.gradle.config.model.sonarCloud
import io.komune.gradle.config.model.sonatypeOss
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPom
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

/**
 * Enum representing the deployment type
 */
enum class PkgDeployType {
    PUBLISH, PROMOTE;

    companion object {
        /**
         * Parse a string value to DeployType, with PUBLISH as default
         */
        fun fromString(value: String?): PkgDeployType {
            return when (value?.uppercase()) {
                "PROMOTE" -> PROMOTE
                "PUBLISH" -> PUBLISH
                else -> PUBLISH // Default value
            }
        }
    }
}

/**
 * Enum representing the Maven repository type
 */
enum class PkgMavenRepo {
    GITHUB, MAVEN_CENTRAL, NONE;

    companion object {
        /**
         * Parse a string value to PkgMavenRepo, with GITHUB as default
         */
        fun fromString(value: String?): PkgMavenRepo {
            return when (value?.uppercase()) {
                "GITHUB" -> GITHUB
                "MAVEN_CENTRAL" -> MAVEN_CENTRAL
                else -> NONE
            }
        }
    }
}

/**
 * Retrieves the [fixers][io.komune.fixers.gradle.fixers] extension.
 */
val ExtensionContainer.fixers: ConfigExtension?
	get() = findByName(ConfigExtension.NAME) as ConfigExtension?

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

	var bundle: Bundle = Bundle(
		name = project.name
	)

	var kt2Ts: Kt2Ts = Kt2Ts(outputDirectory = "platform/web/kotlin")

	var jdk: Jdk = Jdk(
		version = 17
	)

	var buildTime: Long = System.currentTimeMillis()

	var repositories: Map<String, Repository> = setOf(
		Repository.sonatypeOss(project), Repository.github(project)
	).associateBy { it.name }

	var publication: Publication? = null

	var npm: Npm = Npm()

	var detekt: Detekt = Detekt()

	var sonar: Sonar = Sonar.sonarCloud(project)

	var properties: MutableMap<String, Any> = mutableMapOf()

	/**
	 * Maven Central URL for publishing.
	 */
	val mavenCentralUrl: Property<String> = project.objects.property(String::class.java).apply {
		convention("https://central.sonatype.com/api/v1/publisher")
	}

	/**
	 * GitHub Packages URL for publishing.
	 */
	val githubPackagesUrl: Property<String> = project.objects.property(String::class.java).apply {
		convention(project.provider { "https://maven.pkg.github.com/komune-io/${project.rootProject.name}" })
	}

	/**
	 * Maven Snapshots URL for publishing.
	 */
	val mavenSnapshotsUrl: Property<String> = project.objects.property(String::class.java).apply {
		convention("https://central.sonatype.com/repository/maven-snapshots/")
	}

	/**
	 * Gets the package deployment type from environment variables or project properties.
	 * @return The package deployment type, defaulting to PUBLISH if not specified.
	 */
	val pkgDeployType: Property<PkgDeployType>
		= project.objects.property(PkgDeployType::class.java).apply {
		convention(project.provider {
			val deployTypeStr = System.getenv("PKG_DEPLOY_TYPE") ?: project.findProperty("PKG_DEPLOY_TYPE")?.toString()
			PkgDeployType.fromString(deployTypeStr)
		})
	}

	/**
	 * Gets the Maven repository type from environment variables or project properties.
	 * @return The Maven repository type, defaulting to GITHUB if not specified.
	 */
	val pkgMavenRepo: Property<PkgMavenRepo>
		= project.objects.property(PkgMavenRepo::class.java).apply {
		convention(project.provider {
			val repoTypeStr = System.getenv("PKG_MAVEN_REPO") ?: project.findProperty("PKG_MAVEN_REPO")?.toString()
			PkgMavenRepo.fromString(repoTypeStr)
		})
	}

	/**
	 * Checks if the package deployment type is PROMOTE.
	 */
	val isPkgDeployTypePromote: Provider<Boolean> = project.provider {
		pkgDeployType.get() == PkgDeployType.PROMOTE
	}

	/**
	 * Checks if the package deployment type is PUBLISH.
	 */
	val isPkgDeployTypePublish: Provider<Boolean> = project.provider {
		pkgDeployType.get() == PkgDeployType.PUBLISH
	}

	/**
	 * Checks if the Maven repository is GitHub.
	 */
	val isGithubMavenRepo: Provider<Boolean> = project.provider {
		pkgMavenRepo.get() == PkgMavenRepo.GITHUB
	}

	/**
	 * Checks if the Maven repository is not GitHub.
	 */
	val isNotGithubMavenRepo: Provider<Boolean> = project.provider {
		pkgMavenRepo.get() != PkgMavenRepo.GITHUB
	}

	/**
	 * Determines if artifacts should be promoted.
	 * Artifacts are promoted if the deployment type is PROMOTE or if the repository is not GitHub.
	 */
	val isPromote: Provider<Boolean> = project.provider {
		isPkgDeployTypePromote.get() || isNotGithubMavenRepo.get()
	}

	/**
	 * Determines if artifacts should be published.
	 * Artifacts are published if the deployment type is PUBLISH or if the repository is GitHub.
	 */
	val isPublish: Provider<Boolean> = project.provider {
		isPkgDeployTypePublish.get() || isGithubMavenRepo.get()
	}

	/**
	 * GitHub username for package publishing.
	 */
	val pkgGithubUsername: Property<String> = project.objects.property(String::class.java).apply {
		convention(project.provider { 
			System.getenv("PKG_GITHUB_USERNAME") ?: project.findProperty("PKG_GITHUB_USERNAME")?.toString() ?: ""
		})
	}

	/**
	 * GitHub token for package publishing.
	 */
	val pkgGithubToken: Property<String> = project.objects.property(String::class.java).apply {
		convention(project.provider { 
			System.getenv("PKG_GITHUB_TOKEN") ?: project.findProperty("PKG_GITHUB_TOKEN")?.toString() ?: ""
		})
	}

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

	fun repositories(configure: Action<Map<String, Repository>>) {
		configure.execute(repositories)
	}

	/**
	 * Searches for the version in the VERSION file or falls back to the project's version.
	 *
	 * @return A provider that resolves to the version string
	 */
	val version: Provider<String> = project.provider {
		val versionFile = project.rootProject.file("VERSION")
		if (versionFile.exists()) {
			versionFile.readText().trim()
		} else {
			project.version.toString()
		}
	}
}

fun Project.pom(bundle: Bundle): Action<MavenPom> = Action {
	name.set(bundle.name)
	description.set(bundle.description)
	url.set(bundle.url)

	this.scm {
		url.set(bundle.url)
		bundle.scmConnection?.let { connection.set(it) }
		bundle.scmDeveloperConnection?.let { developerConnection.set(it) }
	}
	licenses {
		license {
			bundle.licenseName?.let { name.set(it) }
			bundle.licenseUrl?.let { url.set(it) }
			bundle.licenseDistribution?.let { distribution.set(it) }
		}
	}
	developers {
		developer {
			bundle.developerId?.let { id.set(it) }
			bundle.developerName?.let { name.set(it) }
			bundle.developerOrganization?.let { organization.set(it) }
			bundle.developerOrganizationUrl?.let { organizationUrl.set(it) }
		}
	}
}
