package io.komune.fixers.gradle.plugin.publish

import io.komune.fixers.gradle.config.ConfigExtension
import io.komune.fixers.gradle.config.fixers
import io.komune.fixers.gradle.config.model.PublishConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

class PublishPlugin : Plugin<Project> {

	companion object {
		const val PLUGIN_ID = "io.komune.fixers.gradle.publish"
		const val GRADLE_PLUGIN_PUBLISH_ID = "com.gradle.plugin-publish"
	}

	override fun apply(project: Project) {
		if (project == project.rootProject) {
			applyToRoot(project)
		} else {
			applyToSubproject(project)
		}
	}

	private fun applyToRoot(root: Project) {
		root.gradle.projectsEvaluated {
			val fixersConfig = root.extensions.fixers ?: return@projectsEvaluated
			bridgeGradlePortalCredentials(fixersConfig.publish)
			val publishSubprojects = root.subprojects.filter {
				it.pluginManager.hasPlugin(PLUGIN_ID)
			}
			if (publishSubprojects.isNotEmpty()) {
				registerPublishTasks(root, fixersConfig, publishSubprojects)
			}
		}
	}

	/**
	 * Bridges `PublishConfig.gradlePortalKey` / `PublishConfig.gradlePortalSecret`
	 * (env `FIXERS_PUBLISH_GRADLE_PORTAL_KEY/SECRET` or gradle prop
	 * `fixers.publish.gradle.portal.key/secret`) to the `gradle.publish.key` /
	 * `gradle.publish.secret` system properties that `com.gradle.plugin-publish`
	 * reads at task-execution time.
	 *
	 * Uses `System.setProperty()` because `com.gradle.plugin-publish` only checks
	 * system properties and `GRADLE_PUBLISH_KEY/SECRET` env vars — not Gradle
	 * project properties or extra properties.
	 *
	 * Called inside `projectsEvaluated` so the `PublishConfig` properties are fully
	 * resolved (env → gradle prop → DSL fallback chain).
	 *
	 * Only sets the property if it is not already set, so explicit
	 * `-Dgradle.publish.key=...` always wins.
	 */
	private fun bridgeGradlePortalCredentials(config: PublishConfig) {
		val portalKey = config.gradlePortalKey.orNull
		val portalSecret = config.gradlePortalSecret.orNull

		if (!portalKey.isNullOrEmpty() && System.getProperty("gradle.publish.key") == null) {
			System.setProperty("gradle.publish.key", portalKey)
		}
		if (!portalSecret.isNullOrEmpty() && System.getProperty("gradle.publish.secret") == null) {
			System.setProperty("gradle.publish.secret", portalSecret)
		}
	}

	private fun applyToSubproject(project: Project) {
		project.plugins.apply(MavenPublishPlugin::class.java)
		project.plugins.apply(SigningPlugin::class.java)

		project.afterEvaluate {
			val fixersConfig = rootProject.extensions.fixers
			if (fixersConfig == null) {
				logger.warn("No fixers config found on root project, skipping publish configuration")
				return@afterEvaluate
			}
			setupPublishing(fixersConfig)
			setupSign(fixersConfig)
		}
	}

	@Suppress("CyclomaticComplexMethod", "ReturnCount")
	private fun Project.setupPublishing(fixersConfig: ConfigExtension) {
		val publishing = extensions.getByType(PublishingExtension::class.java)

		// Staging repository (for promote → Maven Central upload)
		val stagingProject = rootProject
		publishing.repositories {
			maven {
				name = "staging"
				url = project.uri(stagingProject.layout.buildDirectory.dir(fixersConfig.publish.stagingDirectory.get()))
			}
		}

		// GitHub Packages repository (for stage)
		publishing.repositories {
			maven {
				name = "githubPackages"
				url = project.uri(fixersConfig.publish.githubPackagesUrl.get())
				credentials {
					username = fixersConfig.publish.pkgGithubUsername.orNull ?: ""
					password = fixersConfig.publish.pkgGithubToken.orNull ?: ""
				}
			}
		}

		val currentProject = this
		PublishMppSetup.setupMppPublish(currentProject, fixersConfig)
		PublishJvmSetup.setupJVMPublish(currentProject, fixersConfig)
		PublishPlatformSetup.setupPlatformPublish(currentProject, fixersConfig)
		PublishCatalogSetup.setupCatalogPublish(currentProject, fixersConfig)

		publishing.publications {
			configureMavenPublications(currentProject, fixersConfig)
		}

		// Inline dependency versions from dependencyManagement into dependencies
		// so Maven Central validation doesn't reject missing <version> elements.
		// versionMapping handles most deps, but KMP cross-platform dependencies
		// (e.g. f2-dsl-cqrs) need a fallback because the POM uses the root artifact ID
		// while the resolved graph uses platform-suffixed IDs (e.g. f2-dsl-cqrs-jvm).
		val resolvedVersions by lazy { collectResolvedVersions(currentProject) }
		publishing.publications.withType<MavenPublication>().configureEach {
			versionMapping {
				allVariants {
					fromResolutionResult()
				}
			}
			pom.withXml {
				inlineDependencyVersions(asNode(), resolvedVersions)
			}
		}
	}

	private fun Project.setupSign(fixersConfig: ConfigExtension) {
		if (!fixersConfig.publish.signingGpgKey.isPresent || !fixersConfig.publish.signingGpgKeyPassword.isPresent) {
			logger.info("No signing config provided, skip signing")
			disableSigningTasks()
			return
		}

		val inMemoryKey = fixersConfig.publish.signingGpgKey.get()
		val password = fixersConfig.publish.signingGpgKeyPassword.get()
		if (inMemoryKey.isEmpty()) {
			logger.info("Empty signing key provided, skip signing")
			disableSigningTasks()
			return
		}

		val hasPublishPlugin = plugins.hasPlugin(GRADLE_PLUGIN_PUBLISH_ID)

		extensions.getByType(SigningExtension::class.java).apply {
			isRequired = true
			useInMemoryPgpKeys(inMemoryKey, password)

			if (!hasPublishPlugin) {
				sign(
					extensions.getByType(PublishingExtension::class.java).publications
				)
			} else {
				val pub = extensions.getByType<PublishingExtension>()
				pub.publications.findByName("mavenJava")?.let {
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

	@Suppress("LongMethod", "ThrowsCount")
	private fun registerPublishTasks(
		root: Project,
		fixersConfig: ConfigExtension,
		publishSubprojects: List<Project>
	) {
		val allPublishToStagingTasks = publishSubprojects.map {
			"${it.path}:publishAllPublicationsToStagingRepository"
		}

		val version = fixersConfig.publish.version.get()
		val isSnapshot = version.endsWith("-SNAPSHOT")

		// Resolve all Project-dependent values at configuration time
		// so that doLast lambdas only capture serializable values (configuration cache compatible)
		val stagingDirProvider = root.layout.buildDirectory.dir(fixersConfig.publish.stagingDirectory.get())
		val centralUrlProvider = fixersConfig.publish.mavenCentralUrl
		val centralUsernameProvider = fixersConfig.publish.mavenCentralUsername
		val centralPasswordProvider = fixersConfig.publish.mavenCentralPassword
		val bundleName = "${root.name}-$version"

		val cleanStagingTask = root.tasks.register("cleanStaging") {
			group = "publishing"
			description = "Cleans the staging directory before publishing"
			doLast {
				stagingDirProvider.get().asFile.deleteRecursively()
			}
		}

		// Ensure ALL publish-to-staging tasks run after cleanStaging.
		// publishAllPublicationsToStagingRepository is just an aggregate — the actual file writes
		// happen in individual tasks (publishXPublicationToStagingRepository). With parallel=true,
		// those individual tasks can race with cleanStaging's deleteRecursively(), causing partial
		// staging directories (some files deleted mid-write). Matching all publish*ToStagingRepository
		// tasks prevents this race condition.
		publishSubprojects.forEach { subproject ->
			subproject.tasks.matching {
				it.name.startsWith("publish") && it.name.endsWith("ToStagingRepository")
			}.configureEach {
				mustRunAfter(cleanStagingTask)
			}
		}

		// Stage: publish to staging directory, then upload to GitHub Packages
		// Uses custom uploader to handle 409 Conflict (already-published artifacts) gracefully.
		root.tasks.register("stage") {
			group = "publishing"
			description = "Publishes all artifacts to GitHub Packages"
			dependsOn(cleanStagingTask)
			dependsOn(allPublishToStagingTasks)
			doLast {
				uploadToGithubPackages(stagingDirProvider.get().asFile, fixersConfig)
			}
		}

		// Promote: route based on version type
		root.tasks.register("promote") {
			group = "publishing"
			if (isSnapshot) {
				description = "Publishes all SNAPSHOT artifacts to Maven Central Snapshots"
				dependsOn(cleanStagingTask)
				dependsOn(allPublishToStagingTasks)
				doLast {
					uploadToMavenSnapshots(stagingDirProvider.get().asFile, fixersConfig)
				}
			} else {
				description = "Publishes all artifacts to Maven Central via Central Portal"
				dependsOn(cleanStagingTask)
				dependsOn(allPublishToStagingTasks)

				// Also publish to Gradle Plugin Portal (only non-SNAPSHOT releases are accepted)
				if (fixersConfig.publish.gradlePluginPortalEnabled.getOrElse(true)) {
					dependsOn(
						publishSubprojects
							.filter { it.plugins.hasPlugin(GRADLE_PLUGIN_PUBLISH_ID) }
							.map { "${it.path}:publishPlugins" }
					)
				}

				doLast {
					uploadToCentralPortal(
						stagingDirProvider.get().asFile, centralUrlProvider.get(),
						centralUsernameProvider.orNull, centralPasswordProvider.orNull, bundleName
					)
				}
			}
		}
	}

	private fun uploadToGithubPackages(stagingDir: java.io.File, fixersConfig: ConfigExtension) {
		val ghUsername = fixersConfig.publish.pkgGithubUsername.orNull
			?: throw org.gradle.api.GradleException(
				"FIXERS_PUBLISH_GITHUB_USERNAME is not set"
			)
		val ghToken = fixersConfig.publish.pkgGithubToken.orNull
			?: throw org.gradle.api.GradleException(
				"FIXERS_PUBLISH_GITHUB_TOKEN is not set"
			)
		MavenRepositoryUploader.to("GitHub Packages")
			.from(stagingDir)
			.at(fixersConfig.publish.githubPackagesUrl.get())
			.withCredentials(ghUsername, ghToken)
			.upload()
	}

	private fun uploadToMavenSnapshots(stagingDir: java.io.File, fixersConfig: ConfigExtension) {
		val username = fixersConfig.publish.mavenCentralUsername.orNull
			?: throw org.gradle.api.GradleException("FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME is not set")
		val password = fixersConfig.publish.mavenCentralPassword.orNull
			?: throw org.gradle.api.GradleException("FIXERS_PUBLISH_MAVEN_CENTRAL_PASSWORD is not set")
		MavenRepositoryUploader.to("Maven Central Snapshots")
			.from(stagingDir)
			.at(fixersConfig.publish.mavenSnapshotsUrl.get())
			.withCredentials(username, password)
			.upload()
	}

	private fun uploadToCentralPortal(
		stagingDir: java.io.File,
		centralUrl: String,
		username: String?,
		password: String?,
		bundleName: String,
	) {
		val resolvedUsername = username
			?: throw org.gradle.api.GradleException("FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME is not set")
		val resolvedPassword = password
			?: throw org.gradle.api.GradleException("FIXERS_PUBLISH_MAVEN_CENTRAL_PASSWORD is not set")
		CentralPortalUploader.upload(
			stagingDir = stagingDir,
			baseUrl = centralUrl,
			username = resolvedUsername,
			password = resolvedPassword,
			bundleName = bundleName,
		)
	}
}

/**
 * Collects resolved dependency versions from all resolvable configurations,
 * stripping KMP platform suffixes so that root artifact coordinates (e.g. f2-dsl-cqrs)
 * can be matched to platform-specific resolved artifacts (e.g. f2-dsl-cqrs-jvm).
 */
private val KMP_PLATFORM_SUFFIXES = listOf("-jvm", "-js", "-wasm-js", "-wasmjs")

private fun collectResolvedVersions(project: Project): Map<String, String> {
	val versions = mutableMapOf<String, String>()
	project.configurations.filter { it.isCanBeResolved }.forEach { config ->
		try {
			config.incoming.resolutionResult.allComponents.forEach { component ->
				val id = component.moduleVersion ?: return@forEach
				if (id.group.isEmpty()) return@forEach
				addVersionWithPlatformStripping(versions, id.group, id.name, id.version)
			}
		} catch (_: Exception) {
			// Skip configurations that cannot be resolved
		}
	}
	return versions
}

private fun addVersionWithPlatformStripping(
	versions: MutableMap<String, String>,
	group: String,
	name: String,
	version: String
) {
	versions.putIfAbsent("$group:$name", version)
	for (suffix in KMP_PLATFORM_SUFFIXES) {
		if (name.endsWith(suffix)) {
			versions.putIfAbsent("$group:${name.removeSuffix(suffix)}", version)
		}
	}
}

/**
 * Post-processes a POM XML node to inline dependency versions from
 * dependencyManagement into dependency entries that lack explicit versions.
 * Falls back to [resolvedVersions] for deps not covered by dependencyManagement
 * (e.g. BOM-managed KMP dependencies where versionMapping can't match coordinates).
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount")
internal fun inlineDependencyVersions(
	root: groovy.util.Node,
	resolvedVersions: Map<String, String> = emptyMap()
) {
	// POM dependencyManagement versions take precedence over resolved versions
	val allVersions = resolvedVersions + extractVersionMap(root)
	if (allVersions.isNotEmpty()) {
		applyVersionsToDependencies(root, allVersions)
	}
	// Always remove dependencyManagement section — versions are either inlined above
	// or provided by Gradle's versionMapping from resolved dependency graph
	val depMgmt = findChildNode(root, "dependencyManagement")
	if (depMgmt != null) {
		root.remove(depMgmt)
	}
}

private fun extractVersionMap(root: groovy.util.Node): Map<String, String> {
	val depMgmt = findChildNode(root, "dependencyManagement")
	val mgmtDeps = depMgmt?.let { findChildNode(it, "dependencies") }

	return mgmtDeps?.let { deps ->
		dependencyNodes(deps)
			.filter { !isBomImport(it) }
			.mapNotNull { dep ->
				val groupId = nodeChildText(dep, "groupId")
				val artifactId = nodeChildText(dep, "artifactId")
				val version = nodeChildText(dep, "version")
				if (groupId != null && artifactId != null && version != null) {
					"$groupId:$artifactId" to version
				} else {
					null
				}
			}
			.toMap()
	} ?: emptyMap()
}

private fun applyVersionsToDependencies(root: groovy.util.Node, versionMap: Map<String, String>) {
	val depsNode = findChildNode(root, "dependencies") ?: return

	dependencyNodes(depsNode)
		.filter { findChildNode(it, "version") == null }
		.forEach { dep ->
			val groupId = nodeChildText(dep, "groupId")
			val artifactId = nodeChildText(dep, "artifactId")
			val key = "$groupId:$artifactId"
			val version = versionMap[key]
			if (version != null) {
				dep.appendNode("version", version)
			}
		}
}

private fun dependencyNodes(depsNode: groovy.util.Node): List<groovy.util.Node> {
	return childNodes(depsNode).filter { nodeLocalName(it) == "dependency" }
}

private fun isBomImport(dep: groovy.util.Node): Boolean {
	val scope = nodeChildText(dep, "scope")
	return scope?.equals("import", ignoreCase = true) == true
}

private fun findChildNode(parent: groovy.util.Node, localName: String): groovy.util.Node? {
	return childNodes(parent).firstOrNull { nodeLocalName(it) == localName }
}

private fun childNodes(node: groovy.util.Node): List<groovy.util.Node> {
	return node.children().filterIsInstance<groovy.util.Node>()
}

private fun nodeLocalName(node: groovy.util.Node): String {
	val name = node.name()
	val str = name.toString()
	return if (str.startsWith("{")) str.substringAfter("}") else str
}

private fun nodeChildText(parent: groovy.util.Node, localName: String): String? {
	val child = findChildNode(parent, localName) ?: return null
	return child.text()?.takeIf { it.isNotBlank() }
}
