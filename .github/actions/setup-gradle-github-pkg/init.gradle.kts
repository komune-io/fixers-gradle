// Komune CI: resolve fixers artifacts from GitHub Packages as well as Maven Central.
// GitHub Packages answers 401 rather than 404 for what it does not hold, which stops a lookup:
// it always comes last.
fun org.gradle.api.artifacts.dsl.RepositoryHandler.githubPackages() {
    maven {
        name = "FixersGitHubPackages"
        url = uri("https://maven.pkg.github.com/komune-io/fixers")
        credentials {
            username = System.getenv("FIXERS_PUBLISH_GITHUB_USERNAME")
            password = System.getenv("FIXERS_PUBLISH_GITHUB_TOKEN")
        }
    }
}

fun org.gradle.api.artifacts.dsl.RepositoryHandler.fixersRepositories() {
    mavenCentral()
    githubPackages()
}

// A build that prefers or requires settings repositories (the fixers settings plugin requires
// them) gets these in its settings: on its projects they would be ignored, or fail the build.
var projectRepositories = true

settingsEvaluated {
    pluginManagement {
        repositories {
            mavenCentral()
            gradlePluginPortal()
            githubPackages()
        }
    }
    if (dependencyResolutionManagement.repositoriesMode.get() != RepositoriesMode.PREFER_PROJECT) {
        projectRepositories = false
        dependencyResolutionManagement.repositories.fixersRepositories()
    }
}

allprojects {
    if (projectRepositories) {
        repositories.fixersRepositories()
    }
}
