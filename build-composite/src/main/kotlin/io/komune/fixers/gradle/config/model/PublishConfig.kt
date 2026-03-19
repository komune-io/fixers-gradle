package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Configuration for publishing artifacts.
 */
open class PublishConfig(
    private val project: Project
) {
    companion object {
        private const val USERNAME_PREVIEW_LENGTH = 3
    }

    override fun toString(): String {
        return """
            PublishConfig(
                mavenCentralUrl=${mavenCentralUrl.orNull},
                mavenSnapshotsUrl=${mavenSnapshotsUrl.orNull},
                mavenCentralUsername=${mavenCentralUsername.orNull?.take(USERNAME_PREVIEW_LENGTH)}***,
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
     * Maven Central username for publishing via Central Portal API.
     */
    val mavenCentralUsername: Property<String> = project.property(
        envKey = "JRELEASER_MAVENCENTRAL_USERNAME",
        projectKey = "maven.central.username"
    )

    /**
     * Maven Central password for publishing via Central Portal API.
     */
    val mavenCentralPassword: Property<String> = project.property(
        envKey = "JRELEASER_MAVENCENTRAL_PASSWORD",
        projectKey = "maven.central.password"
    )

    /**
     * GitHub username for package publishing.
     */
    val pkgGithubUsername: Property<String> = project.property(
        envKey = "JRELEASER_DEPLOY_MAVEN_GITHUB_GITHUB_USERNAME",
        projectKey = "pkg.github.username"
    )

    /**
     * GitHub token for package publishing.
     */
    val pkgGithubToken: Property<String> = project.property(
        envKey = "JRELEASER_DEPLOY_MAVEN_GITHUB_GITHUB_TOKEN",
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
    val gradlePlugin: ListProperty<String> = project.objects.listProperty(String::class.java).apply {
        convention(emptyList())
    }

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
        mavenCentralUsername.mergeIfNotPresent(source.mavenCentralUsername)
        mavenCentralPassword.mergeIfNotPresent(source.mavenCentralPassword)
        pkgGithubUsername.mergeIfNotPresent(source.pkgGithubUsername)
        pkgGithubToken.mergeIfNotPresent(source.pkgGithubToken)
        signingKey.mergeIfNotPresent(source.signingKey)
        signingPassword.mergeIfNotPresent(source.signingPassword)
        stagingDirectory.mergeIfNotPresent(source.stagingDirectory)
        return this
    }
}
