package io.komune.fixers.gradle.settings

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.provider.Property

/**
 * Loads the f2 version catalog as `catalogue`, at this plugin's own version, and declares the
 * Node.js and Yarn distributions Kotlin/JS downloads, each repository serving its module only.
 *
 * Every repository lives in the settings: a project that declares one fails the build, and the
 * fixers project plugins add none. No Maven repository is added here: the project declares its
 * own, in its own order.
 */
class SettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val version = FixersSettingsVersion.VALUE
        checkCatalogVersion(settings, version)
        settings.dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        settings.dependencyResolutionManagement.repositories.declareJsDistributions()
        settings.gradle.extensions.extraProperties.set(MARKER, true)
        settings.dependencyResolutionManagement.versionCatalogs.create(CATALOG_NAME) {
            from("$CATALOG_COORDINATES:$version")
        }
        settings.gradle.allprojects { clearJsDownloadBaseUrls() }
    }

    internal companion object {
        const val CATALOG_NAME = "catalogue"
        const val CATALOG_COORDINATES = "io.komune.f2:f2-gradle-catalog"
        const val LOCAL_CATALOG = "gradle/libs.versions.toml"

        /** Read by the fixers config plugin, which then adds no project repository. */
        const val MARKER = "io.komune.fixers.gradle.settings"
    }
}

/**
 * A `fixers = "x"` entry in the project's own catalog that differs from this plugin's version
 * would build against two fixers releases at once.
 */
private fun checkCatalogVersion(settings: Settings, version: String) {
    val catalog = settings.settingsDir.resolve(SettingsPlugin.LOCAL_CATALOG)
    if (!catalog.exists()) return
    val declared = catalog.readLines()
        .dropWhile { it.trim() != VERSIONS_SECTION }
        .drop(1)
        .takeWhile { !it.trim().startsWith("[") }
        .firstNotNullOfOrNull { FIXERS_VERSION.find(it)?.groupValues?.get(1) }
    if (declared != null && declared != version) {
        throw GradleException(
            "${SettingsPlugin.LOCAL_CATALOG} declares fixers = \"$declared\", but the fixers settings plugin is " +
                "$version: set both to the same version."
        )
    }
}

private const val VERSIONS_SECTION = "[versions]"

private val FIXERS_VERSION = Regex("""^\s*fixers\s*=\s*"([^"]+)"""")

private fun RepositoryHandler.declareJsDistributions() {
    // Each layout matches its Kotlin Gradle plugin setup task (NodeJsSetupTask, YarnSetupTask).
    exclusiveContent {
        forRepository {
            ivy {
                name = "Node.js"
                setUrl("https://nodejs.org/dist")
                patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                metadataSources { artifact() }
            }
        }
        filter { includeModule("org.nodejs", "node") }
    }
    exclusiveContent {
        forRepository {
            ivy {
                name = "Yarn"
                setUrl("https://github.com/yarnpkg/yarn/releases/download")
                patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                metadataSources { artifact() }
            }
        }
        filter { includeModule("com.yarnpkg", "yarn") }
    }
}

private val JS_DOWNLOAD_SPECS = setOf(
    "org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec",
    "org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec",
)

/**
 * A non-null download base URL makes the Kotlin plugin add its own project repository, which the
 * settings repositories above replace. The Kotlin plugin is reached by type name: this plugin
 * must not put it on the settings classpath.
 */
private fun Project.clearJsDownloadBaseUrls() {
    plugins.configureEach {
        extensions.extensionsSchema.elements
            .filter { element -> element.publicType.concreteClass.name in JS_DOWNLOAD_SPECS }
            .forEach { element ->
                val spec = extensions.getByName(element.name)
                @Suppress("UNCHECKED_CAST")
                val downloadBaseUrl = spec.javaClass.getMethod("getDownloadBaseUrl").invoke(spec) as Property<String>
                downloadBaseUrl.set(null as String?)
            }
    }
}
