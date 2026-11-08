// Komune CI: resolve fixers artifacts from GitHub Packages as well as Maven Central.
fun org.gradle.api.artifacts.dsl.RepositoryHandler.fixersRepositories() {
    mavenCentral()
    maven {
        name = "FixersGitHubPackages"
        url = uri("https://maven.pkg.github.com/komune-io/fixers")
        credentials {
            username = System.getenv("FIXERS_PUBLISH_GITHUB_USERNAME")
            password = System.getenv("FIXERS_PUBLISH_GITHUB_TOKEN")
        }
    }
}

// A build that forbids project repositories (the fixers settings plugin does) gets them in its
// settings instead: adding them to its projects would fail it.
var projectRepositories = true

settingsEvaluated {
    pluginManagement {
        repositories {
            mavenCentral()
            gradlePluginPortal()
            fixersRepositories()
        }
    }
    if (dependencyResolutionManagement.repositoriesMode.get() == RepositoriesMode.FAIL_ON_PROJECT_REPOS) {
        projectRepositories = false
        dependencyResolutionManagement.repositories.fixersRepositories()
    }
}

allprojects {
    if (projectRepositories) {
        repositories.fixersRepositories()
    }
}
