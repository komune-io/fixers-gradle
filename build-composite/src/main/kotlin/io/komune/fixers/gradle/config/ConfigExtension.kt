package io.komune.fixers.gradle.config

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

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

enum class PkgMavenRepo {
    GITHUB, MAVEN_CENTRAL, NONE;

    companion object {
        /**
         * Parse a string value to PkgMavenRepo, with MAVEN_CENTRAL as default
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
 * Configuration extension for the publishing plugin.
 * Contains all configurable properties used across the publishing functionality.
 */
open class ConfigExtension(project: Project) {
    val licenseName: Property<String> = project.objects.property(String::class.java)

    val licenseUrl: Property<String> = project.objects.property(String::class.java)

    val organizationId: Property<String> = project.objects.property(String::class.java)

    val organizationName: Property<String> = project.objects.property(String::class.java)

    val organizationUrl: Property<String> = project.objects.property(String::class.java)

    val githubOrganization: Property<String> = project.objects.property(String::class.java)

    val githubProject: Property<String> = project.objects.property(String::class.java)

    val repositoryUrl: Property<String> = project.objects.property(String::class.java).apply {
        convention(project.provider { "https://github.com/${githubOrganization.get()}/${githubProject.get()}" })
    }

    val mavenCentralUrl: Property<String> = project.objects.property(String::class.java).apply {
        convention("https://central.sonatype.com/api/v1/publisher")
    }

    val githubPackagesUrl: Property<String> = project.objects.property(String::class.java).apply {
        convention(project.provider { "https://maven.pkg.github.com/${githubOrganization.get()}/${githubProject.get()}" })
    }

    val mavenSnapshotsUrl: Property<String> = project.objects.property(String::class.java).apply {
        convention("https://central.sonatype.com/repository/maven-snapshots/")
    }

    val pkgDeployType: Property<PkgDeployType> = project.objects.property(PkgDeployType::class.java).apply {
        convention(project.provider {
            PkgDeployType.fromString(System.getenv("PKG_DEPLOY_TYPE"))
        })
    }

    val pkgMavenRepo: Property<PkgMavenRepo> = project.objects.property(PkgMavenRepo::class.java).apply {
        convention(project.provider {
            val tt = PkgMavenRepo.fromString(System.getenv("PKG_MAVEN_REPO"))
            project.logger.lifecycle("PKG_MAVEN_REPO: $tt")
            tt
        })
    }

    val pkgGithubUsername: Property<String> = project.objects.property(String::class.java).apply {
        System.getenv("PKG_GITHUB_USERNAME")?.let { value ->
            convention(project.provider { value })
        }
    }

    val pkgGithubToken: Property<String> = project.objects.property(String::class.java).apply {
        System.getenv("PKG_GITHUB_TOKEN")?.let { value ->
            convention(project.provider { value })
        }
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
     * Checks if the Maven repository is not GitHub and not NONE.
     */
    val isNotGithubMavenRepo: Provider<Boolean> = project.provider {
        !isGithubMavenRepo.get() && pkgMavenRepo.get() != PkgMavenRepo.NONE
    }

    /**
     * Determines if artifacts should be published.
     * Artifacts are published if the deployment type is PUBLISH or if the repository is GitHub.
     */
    val isPublish: Provider<Boolean> = project.provider {
        isPkgDeployTypePublish.get() || isGithubMavenRepo.get()
    }

    /**
     * Determines if artifacts should be promoted.
     * Artifacts are promoted if the deployment type is PROMOTE or if the repository is not GitHub.
     */
    val isPromote: Provider<Boolean> = project.provider {
        isPkgDeployTypePromote.get() || isNotGithubMavenRepo.get()
    }
}

/**
 * Extension function to get or create the config extension for a Gradle project.
 *
 * This function provides a convenient way to access and configure the ConfigExtension
 * for a Gradle project. If the extension doesn't exist yet, it creates a new one.
 *
 * Example usage in a build script:
 * ```kotlin
 * config {
 *     organizationName.set("My Organization")
 *
 *     // Configure GitHub properties
 *     githubOrganization.set("my-org")
 *     githubProject.set("my-repo")
 *
 *     // The repository URLs will be automatically updated based on the GitHub properties:
 *     // repositoryUrl will be "https://github.com/my-org/my-repo"
 *     // githubPackagesUrl will be "https://maven.pkg.github.com/my-org/my-repo"
 * }
 * ```
 *
 * @param configure A configuration block to apply to the extension
 * @return The configured ConfigExtension instance
 */
fun Project.config(configure: ConfigExtension.() -> Unit = {}): ConfigExtension {
    val extension = extensions.findByType(ConfigExtension::class.java) ?: extensions.create(
        "config", ConfigExtension::class.java, this
    )

    extension.configure()
    return extension
}
