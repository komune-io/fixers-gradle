package io.komune.fixers.gradle.config.model

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import io.komune.fixers.gradle.config.utils.property
import io.komune.fixers.gradle.config.utils.initListProperty

/**
 * Enum representing the deployment type
 */
enum class PkgDeployType {
    STAGE, PROMOTE;

    /**
     * Checks if the package deployment type is PROMOTE.
     */
    fun isPkgDeployTypePromote(): Boolean {
        return this == PROMOTE
    }

    /**
     * Checks if the package deployment type is PUBLISH.
     */
    fun isPkgDeployTypePublish(): Boolean {
        return this == STAGE
    }

    companion object {
        fun fromString(value: String?): PkgDeployType {
            return when (value?.uppercase()) {
                "PROMOTE" -> PROMOTE
                "PUBLISH" -> STAGE
                else -> STAGE // Default value
            }
        }
    }
}

/**
 * Enum representing the Maven repository type
 */
enum class PkgMavenRepo {
    GITHUB, MAVEN_CENTRAL, NONE;

    fun isGithubMavenRepo(): Boolean {
        return this == GITHUB
    }

    fun isNotGithubMavenRepo(): Boolean {
        return !isGithubMavenRepo() && this != NONE
    }

    companion object {
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
 * Configuration for publishing artifacts.
 */
open class PublishConfig(
    private val project: Project
) {
    override fun toString(): String {
        return """
            PublishConfig(
                mavenCentralUrl=${mavenCentralUrl.orNull}, 
                mavenSnapshotsUrl=${mavenSnapshotsUrl.orNull}, 
                pkgDeployType=${pkgDeployType.orNull}, 
                pkgMavenRepo=${pkgMavenRepo.orNull}, 
                pkgGithubUsername=${pkgGithubUsername.orNull}, 
                pkgGithubToken=******, 
                signingKey=******, 
                signingPassword=******,
                gradlePlugin=${gradlePlugin.orNull}
            )
        """.trimIndent()
    }
    /**
     * Maven Central URL for publishing.
     */
    val mavenCentralUrl: Property<String> = project.property(
        envKey = "MAVEN_CENTRAL_URL",
        projectKey = "maven.central.url",
        defaultValue = "https://central.sonatype.com/api/v1/publisher"
    )

    /**
     * Maven Snapshots URL for publishing.
     */
    val mavenSnapshotsUrl: Property<String> = project.property(
        envKey = "MAVEN_SNAPSHOTS_URL",
        projectKey = "maven.snapshots.url",
        defaultValue = "https://central.sonatype.com/repository/maven-snapshots/"
    )

    /**
     * Gets the package deployment type from environment variables or project properties.
     * @return The package deployment type, defaulting to PUBLISH if not specified.
     */
    val pkgDeployType: Property<PkgDeployType> = project.objects.property(PkgDeployType::class.java).apply {
        convention(project.provider {
            val deployTypeStr = project.property<String>(
                envKey = "PKG_DEPLOY_TYPE",
                projectKey = "PKG_DEPLOY_TYPE"
            ).orNull
            PkgDeployType.fromString(deployTypeStr)
        })
    }

    /**
     * Gets the Maven repository type from environment variables or project properties.
     * @return The Maven repository type, defaulting to GITHUB if not specified.
     */
    val pkgMavenRepo: Property<PkgMavenRepo> = project.objects.property(PkgMavenRepo::class.java).apply {
        convention(project.provider {
            val repoTypeStr = project.property<String>(
                envKey = "PKG_MAVEN_REPO",
                projectKey = "PKG_MAVEN_REPO"
            ).orNull
            PkgMavenRepo.fromString(repoTypeStr)
        })
    }

    /**
     * Checks if the package deployment type is PROMOTE.
     */
    val isPkgDeployTypePromote: Provider<Boolean> = project.provider {
        pkgDeployType.orNull?.isPkgDeployTypePromote() ?: false
    }

    /**
     * Checks if the package deployment type is PUBLISH.
     */
    val isPkgDeployTypePublish: Provider<Boolean> = project.provider {
        pkgDeployType.orNull?.isPkgDeployTypePublish() ?: false
    }

    /**
     * Checks if the Maven repository is GitHub.
     */
    val isGithubMavenRepo: Provider<Boolean> = project.provider {
        pkgMavenRepo.orNull?.isGithubMavenRepo() ?: false
    }

    /**
     * Checks if the Maven repository is not GitHub and not NONE.
     */
    val isNotGithubMavenRepo: Provider<Boolean> = project.provider {
        pkgMavenRepo.orNull?.isNotGithubMavenRepo() ?: false
    }

    /**
     * Determines if artifacts should be published.
     * Artifacts are published if the deployment type is PUBLISH or if the repository is GitHub.
     */
    val isPublish: Provider<Boolean> = project.provider {
        isPkgDeployTypePublish.orNull ?: false || isGithubMavenRepo.orNull ?: false
    }

    /**
     * Determines if artifacts should be promoted.
     * Artifacts are promoted if the deployment type is PROMOTE or if the repository is not GitHub.
     */
    val isPromote: Provider<Boolean> = project.provider {
        isPkgDeployTypePromote.orNull ?: false || isNotGithubMavenRepo.orNull ?: false
    }

    /**
     * GitHub username for package publishing.
     */
    val pkgGithubUsername: Property<String> = project.property(
        envKey = "PKG_GITHUB_USERNAME",
        projectKey = "pkg.github.username"
    )

    /**
     * GitHub token for package publishing.
     */
    val pkgGithubToken: Property<String> = project.property(
        envKey = "PKG_GITHUB_TOKEN",
        projectKey = "pkg.github.token"
    )

    /**
     * Signing key for artifacts.
     */
    val signingKey: Property<String> = project.property(
        envKey = "GPG_SIGNING_KEY",
        projectKey = "signing.key"
    )

    /**
     * Signing password for artifacts.
     */
    val signingPassword: Property<String> = project.property(
        envKey = "GPG_SIGNING_PASSWORD",
        projectKey = "signing.password"
    )

    /**
     * List of marker publications for Gradle plugins.
     */
    val gradlePlugin: ListProperty<String> = project.initListProperty<String>(
        envKey = "GRADLE_PLUGIN",
        projectKey = "gradle.plugin",
        defaultValue = emptyList()
    )
}
