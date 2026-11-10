rootProject.name = "fixers-gradle"

includeBuild("build-composite")

// This build publishes the fixers settings plugin, so it cannot apply it: it declares what the
// plugin would, every repository in the settings, and sets the marker the project plugins require.
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}
gradle.extensions.extraProperties["io.komune.fixers.gradle.settings"] = true

include("config")
include("plugin")
include("settings-plugin")
include("integration-tests")
