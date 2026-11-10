// Komune CI: resolve Komune's artifacts from GitHub Packages as well as Maven Central.
// GitHub Packages answers 401 rather than 404 for what it does not hold, which stops a lookup:
// it serves Komune's groups only, and always comes last.
fun org.gradle.api.artifacts.dsl.RepositoryHandler.githubPackages() {
    maven {
        name = "FixersGitHubPackages"
        url = uri("https://maven.pkg.github.com/komune-io/fixers")
        credentials {
            username = System.getenv("FIXERS_PUBLISH_GITHUB_USERNAME")
            password = System.getenv("FIXERS_PUBLISH_GITHUB_TOKEN")
        }
        content { includeGroupByRegex("""io\.komune(\..+)?""") }
    }
}

fun org.gradle.api.artifacts.dsl.RepositoryHandler.fixersRepositories() {
    mavenCentral()
    githubPackages()
}

// A build that prefers or requires settings repositories gets these in its settings: on its
// projects they would be ignored, or fail the build.
var projectRepositories = true

beforeSettings {
    // The fixers settings plugin adds Maven Central once the settings are evaluated: GitHub
    // Packages goes after it, so it is registered once that plugin is applied.
    pluginManager.withPlugin("io.komune.fixers.gradle.settings") {
        projectRepositories = false
        gradle.settingsEvaluated { dependencyResolutionManagement.repositories.githubPackages() }
    }
}

settingsEvaluated {
    pluginManagement {
        repositories {
            mavenCentral()
            gradlePluginPortal()
            githubPackages()
        }
    }
    if (projectRepositories && dependencyResolutionManagement.repositoriesMode.get() != RepositoriesMode.PREFER_PROJECT) {
        projectRepositories = false
        dependencyResolutionManagement.repositories.fixersRepositories()
    }
}

allprojects {
    if (projectRepositories) {
        repositories.fixersRepositories()
    }
}
