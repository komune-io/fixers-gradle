package io.komune.fixers.gradle.config.model

import io.komune.fixers.gradle.config.utils.mergeIfNotPresent
import io.komune.fixers.gradle.config.utils.property
import io.komune.fixers.gradle.config.utils.versionFromFile
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
                signingGpgKey=******,
                signingGpgKeyPassword=******,
                npmjsToken=******,
                npmGithubToken=******,
                gradlePlugin=${gradlePlugin.orNull},
                gradlePluginPortalEnabled=${gradlePluginPortalEnabled.orNull},
                gradlePortalKey=******,
                gradlePortalSecret=******,
                stagingDirectory=${stagingDirectory.orNull},
                githubPackagesUrl=${githubPackagesUrl.orNull}
            )
        """.trimIndent()
    }
    /**
     * Maven Central URL for publishing.
     */
    val mavenCentralUrl: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_MAVEN_CENTRAL_URL",
        projectKey = "fixers.publish.maven.central.url",
        defaultValue = "https://central.sonatype.com/api/v1/publisher"
    )

    /**
     * Maven Snapshots URL for publishing.
     */
    val mavenSnapshotsUrl: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_MAVEN_SNAPSHOTS_URL",
        projectKey = "fixers.publish.maven.snapshots.url",
        defaultValue = "https://central.sonatype.com/repository/maven-snapshots/"
    )

    /**
     * Maven Central username for publishing via Central Portal API.
     */
    val mavenCentralUsername: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_MAVEN_CENTRAL_USERNAME",
        projectKey = "fixers.publish.maven.central.username"
    )

    /**
     * Maven Central password for publishing via Central Portal API.
     */
    val mavenCentralPassword: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_MAVEN_CENTRAL_PASSWORD",
        projectKey = "fixers.publish.maven.central.password"
    )

    /**
     * GitHub username for package publishing.
     */
    val pkgGithubUsername: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_GITHUB_USERNAME",
        projectKey = "fixers.publish.github.username"
    )

    /**
     * GitHub token for package publishing.
     */
    val pkgGithubToken: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_GITHUB_TOKEN",
        projectKey = "fixers.publish.github.token"
    )

    /**
     * Signing key for artifacts.
     */
    val signingGpgKey: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_SIGNING_GPG_KEY",
        projectKey = "fixers.publish.signing.gpgKey"
    )

    /**
     * Signing password for artifacts.
     */
    val signingGpgKeyPassword: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_SIGNING_GPG_KEY_PASSWORD",
        projectKey = "fixers.publish.signing.gpgKeyPassword"
    )

    /**
     * npmjs automation token for `@komune-io` scope publishes from `NpmPlugin`.
     * Bound to the `npmjs` registry in the npm-publish extension.
     */
    val npmjsToken: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_NPMJS_TOKEN",
        projectKey = "fixers.publish.npmjs.token"
    )

    /**
     * GitHub Packages npm token for `@komune-io` scope publishes from `NpmPlugin`.
     * Bound to the `github` registry in the npm-publish extension.
     * In CI, sourced directly from the auto-forwarded `GITHUB_TOKEN` via reusable workflows.
     */
    val npmGithubToken: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_NPM_GITHUB_TOKEN",
        projectKey = "fixers.publish.npm.github.token"
    )

    /**
     * List of marker publications for Gradle plugins.
     */
    val gradlePlugin: ListProperty<String> = project.objects.listProperty(String::class.java).apply {
        convention(emptyList())
    }

    /**
     * Whether to publish to the Gradle Plugin Portal during promote.
     * Defaults to true. Set to false to skip Gradle Plugin Portal publishing.
     */
    val gradlePluginPortalEnabled: Property<Boolean> = project.property(
        envKey = "FIXERS_PUBLISH_GRADLE_PORTAL_ENABLED",
        projectKey = "fixers.publish.gradle.portal.enabled",
        defaultValue = true
    )

    /**
     * Gradle Plugin Portal publish key. Bridged to the `gradle.publish.key`
     * system property that `com.gradle.plugin-publish` reads at task-execution time.
     */
    val gradlePortalKey: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_GRADLE_PORTAL_KEY",
        projectKey = "fixers.publish.gradle.portal.key"
    )

    /**
     * Gradle Plugin Portal publish secret. Bridged to the `gradle.publish.secret`
     * system property that `com.gradle.plugin-publish` reads at task-execution time.
     */
    val gradlePortalSecret: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_GRADLE_PORTAL_SECRET",
        projectKey = "fixers.publish.gradle.portal.secret"
    )

    /**
     * Directory for staging deployments.
     */
    val stagingDirectory: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_STAGING_DIRECTORY",
        projectKey = "fixers.publish.staging.directory",
        defaultValue = "staging-deploy"
    )

    /**
     * GitHub Packages URL for publishing.
     */
    val githubPackagesUrl: Property<String> = project.property(
        envKey = "FIXERS_PUBLISH_GITHUB_PACKAGES_URL",
        projectKey = "fixers.publish.github.packages.url",
        defaultValue = "https://maven.pkg.github.com/komune-io/${project.rootProject.name}"
    )

    /**
     * Searches for the version in the VERSION file or falls back to the project's version.
     *
     * @return A provider that resolves to the version string
     */
    val version: Provider<String> = project.provider {
        project.versionFromFile() ?: project.version.toString()
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
        signingGpgKey.mergeIfNotPresent(source.signingGpgKey)
        signingGpgKeyPassword.mergeIfNotPresent(source.signingGpgKeyPassword)
        npmjsToken.mergeIfNotPresent(source.npmjsToken)
        npmGithubToken.mergeIfNotPresent(source.npmGithubToken)
        stagingDirectory.mergeIfNotPresent(source.stagingDirectory)
        githubPackagesUrl.mergeIfNotPresent(source.githubPackagesUrl)
        gradlePluginPortalEnabled.mergeIfNotPresent(source.gradlePluginPortalEnabled)
        gradlePortalKey.mergeIfNotPresent(source.gradlePortalKey)
        gradlePortalSecret.mergeIfNotPresent(source.gradlePortalSecret)
        return this
    }
}
