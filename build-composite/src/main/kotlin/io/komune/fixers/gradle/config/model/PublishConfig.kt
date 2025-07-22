package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.initListProperty
import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

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
     * Checks if the package deployment type is STAGE.
     */
    fun isPkgDeployTypePublish(): Boolean {
        return this == STAGE
    }

    companion object {
        private fun fromString(value: String?): PkgDeployType? {
            return when (value?.uppercase()) {
                "PROMOTE" -> PROMOTE
                "PUBLISH" -> STAGE
                else -> null
            }
        }
        fun fromStrings(value: String?): List<PkgDeployType> {
            return value?.split(",")?.mapNotNull { fromString(it.trim()) } ?: emptyList()
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
                pkgDeployTypes=${pkgDeployTypes.orNull}, 
                pkgGithubUsername=${pkgGithubUsername.orNull}, 
                pkgGithubToken=******, 
                signingKey=******, 
                signingPassword=******,
                gradlePlugin=${gradlePlugin.orNull},
                stagingDirectory=${stagingDirectory.orNull}
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
    val pkgDeployTypes: ListProperty<PkgDeployType> = project.objects.listProperty(PkgDeployType::class.java).apply {
        convention(project.provider {
            val deployTypeStr = project.property<String>(
                envKey = "PKG_DEPLOY_TYPE",
                projectKey = "PKG_DEPLOY_TYPE"
            ).orNull
            PkgDeployType.fromStrings(deployTypeStr)
        })
    }

    /**
     * Checks if the package deployment type is PROMOTE.
     */
    val isPkgDeployTypePromote: Provider<Boolean> = project.provider {
        pkgDeployTypes.get().any { it.isPkgDeployTypePromote() }
    }

    /**
     * Checks if the package deployment type is STAGE.
     */
    val isPkgDeployTypePublish: Provider<Boolean> = project.provider {
        pkgDeployTypes.get().any { it.isPkgDeployTypePublish() }
    }

    /**
     * Determines if artifacts should be published.
     * Artifacts are published if the deployment type is PUBLISH or if the repository is GitHub.
     */
    val isStage: Provider<Boolean> = project.provider {
        isPkgDeployTypePublish.orNull ?: false
    }

    /**
     * Determines if artifacts should be promoted.
     * Artifacts are promoted if the deployment type is PROMOTE or if the repository is not GitHub.
     */
    val isPromote: Provider<Boolean> = project.provider {
        isPkgDeployTypePromote.orNull ?: false
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

    /**
     * Directory for staging deployments.
     */
    val stagingDirectory: Property<String> = project.property(
        envKey = "STAGING_DIRECTORY",
        projectKey = "staging.directory",
        defaultValue = "staging-deploy"
    )

    /**
     * GitHub Packages URL for publishing.
     */
    val githubPackagesUrl: Property<String> = project.objects.property(String::class.java).apply {
        convention(project.provider { "https://maven.pkg.github.com/komune-io/${project.rootProject.name}" })
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

    /**
     * Gets the staging repository path.
     *
     * @param project The Gradle project
     * @return The absolute path to the staging repository
     */
    fun getStagingRepositoryPath(project: Project): String {
        val stageDirectory = stagingDirectory.get()
        val buildStageDirectory = project.layout.buildDirectory.dir(stageDirectory)
        return buildStageDirectory.get().asFile.absolutePath
    }

    /**
     * Merges properties from the source PublishConfig into this PublishConfig.
     * Properties are only merged if the target property is not present and the source property is present.
     *
     * @param source The source PublishConfig to merge from
     * @return This PublishConfig after merging
     */
    fun mergeFrom(source: PublishConfig): PublishConfig {
        mavenCentralUrl.mergeIfNotPresent(source.mavenCentralUrl)
        mavenSnapshotsUrl.mergeIfNotPresent(source.mavenSnapshotsUrl)
        pkgDeployTypes.mergeIfNotPresent(source.pkgDeployTypes)
        pkgGithubUsername.mergeIfNotPresent(source.pkgGithubUsername)
        pkgGithubToken.mergeIfNotPresent(source.pkgGithubToken)
        signingKey.mergeIfNotPresent(source.signingKey)
        signingPassword.mergeIfNotPresent(source.signingPassword)
        gradlePlugin.mergeIfNotPresent(source.gradlePlugin)
        stagingDirectory.mergeIfNotPresent(source.stagingDirectory)
        return this
    }
}
